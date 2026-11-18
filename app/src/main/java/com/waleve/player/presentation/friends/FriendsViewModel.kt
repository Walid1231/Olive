package com.waleve.player.presentation.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.waleve.player.data.extractor.MediaExtractor
import com.waleve.player.data.extractor.MediaFormat
import com.waleve.player.data.local.dao.SongDao
import com.waleve.player.data.mapper.toDomain
import com.waleve.player.domain.model.Friend
import com.waleve.player.domain.model.FriendRequest
import com.waleve.player.domain.model.InboxNotification
import com.waleve.player.domain.model.InboxNotificationType
import com.waleve.player.domain.repository.FriendRepository
import com.waleve.player.domain.repository.SendRequestResult
import com.waleve.player.domain.usecase.ScanMusicUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FriendsUiEvent {
    data class Success(val message: String) : FriendsUiEvent()
    data class Error(val message: String) : FriendsUiEvent()
}

data class SharedSongDownloadProgress(
    val notifId: String,
    val progress: Float,
    val status: String,
    val isComplete: Boolean = false,
    val isFailed: Boolean = false,
)

/** How a person in search results relates to me — decides which button the UI shows. */
enum class Relation { NONE, FRIEND, REQUEST_SENT, REQUEST_RECEIVED }

data class PersonResult(val person: Friend, val relation: Relation, val requestId: String? = null)

enum class SearchMode { NAME, FRIEND_CODE, EMAIL }

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val auth: FirebaseAuth,
    private val mediaExtractor: MediaExtractor,
    private val scanMusicUseCase: ScanMusicUseCase,
    private val songDao: SongDao,
    private val streamRepository: com.waleve.player.domain.repository.StreamRepository,
    private val shareNotificationHelper: com.waleve.player.service.ShareNotificationHelper,
    private val supabaseStorage: com.waleve.player.data.remote.SupabaseStorage,
    private val shareAutoSaver: com.waleve.player.data.social.ShareAutoSaver,
) : ViewModel() {

    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T) =
        this.catch { }.stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    // ── Live data (all follow sign-in / sign-out automatically) ──
    val friends: StateFlow<List<Friend>> = friendRepository.observeFriends().state(emptyList())
    val incomingRequests: StateFlow<List<FriendRequest>> = friendRepository.observeIncomingRequests().state(emptyList())
    val outgoingRequests: StateFlow<List<FriendRequest>> = friendRepository.observeOutgoingRequests().state(emptyList())
    private val allInbox: StateFlow<List<InboxNotification>> = friendRepository.observeInbox().state(emptyList())

    /** Inbox = shares + "request accepted" notices. Friend requests live in the Requests tab. */
    val inbox: StateFlow<List<InboxNotification>> = allInbox
        .map { list -> list.filter { it.type != InboxNotificationType.FRIEND_REQUEST } }
        .state(emptyList())

    val unreadInboxCount: StateFlow<Int> = inbox.map { l -> l.count { !it.isRead } }.state(0)

    private val _searchResults = MutableStateFlow<List<Friend>>(emptyList())
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Search results annotated with friend / pending status. */
    val people: StateFlow<List<PersonResult>> = combine(_searchResults, friends, incomingRequests, outgoingRequests) { res, fr, inc, out ->
        res.map { p ->
            when {
                fr.any { it.uid == p.uid } -> PersonResult(p, Relation.FRIEND)
                out.any { it.toUid == p.uid } -> PersonResult(p, Relation.REQUEST_SENT, out.first { it.toUid == p.uid }.requestId)
                inc.any { it.fromUid == p.uid } -> PersonResult(p, Relation.REQUEST_RECEIVED, inc.first { it.fromUid == p.uid }.requestId)
                else -> PersonResult(p, Relation.NONE)
            }
        }
    }.state(emptyList())

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchMode = MutableStateFlow(SearchMode.NAME)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    /** uids / request ids with an action in flight — buttons show a spinner and can't be double-tapped. */
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, SharedSongDownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, SharedSongDownloadProgress>> = _downloadProgress.asStateFlow()

    private val _events = MutableSharedFlow<FriendsUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<FriendsUiEvent> = _events.asSharedFlow()

    val currentUid: String? get() = auth.currentUser?.uid
    val isSignedIn: Boolean get() = auth.currentUser != null

    private val seenNotifIds = mutableSetOf<String>()

    init {
        // Make sure my profile document exists whenever I'm signed in
        viewModelScope.launch {
            friendRepository.observeUid().collect { uid ->
                if (uid != null) {
                    val u = auth.currentUser
                    runCatching { friendRepository.ensureUserProfile(uid, u?.displayName ?: "Unknown", u?.email ?: "") }
                } else seenNotifIds.clear()
            }
        }
        // System notification for genuinely new items (the first snapshot after sign-in is the backlog)
        viewModelScope.launch {
            friendRepository.observeUid()
                .flatMapLatest { uid -> if (uid == null) emptyFlow() else friendRepository.observeInbox().withIndex() }
                .catch { }
                .collect { (index, items) ->
                    if (index == 0) { items.forEach { seenNotifIds.add(it.notifId) }; return@collect }
                    items.filter { !it.isRead && seenNotifIds.add(it.notifId) }.forEach { shareNotificationHelper.showShareNotification(it) }
                }
        }
    }

    /** Kept for callers from the sign-in screen; flows already restart on their own. */
    fun refreshAfterSignIn() {}

    private fun busy(key: String, on: Boolean) { _busy.value = if (on) _busy.value + key else _busy.value - key }
    private fun ok(msg: String) { _events.tryEmit(FriendsUiEvent.Success(msg)) }
    private fun fail(e: Throwable, fallback: String) { _events.tryEmit(FriendsUiEvent.Error(e.message ?: fallback)) }

    // ── Search ──
    private var searchJob: Job? = null

    fun updateQuery(q: String) {
        _query.value = q
        searchJob?.cancel()
        val trimmed = q.trim()
        if (trimmed.length < 2) { _searchResults.value = emptyList(); _isSearching.value = false; return }
        searchJob = viewModelScope.launch {
            delay(350) // debounce typing
            smartSearch(trimmed)
        }
    }

    /**
     * Detects what was typed: friend code (OLV-XXXX / WLEV-XXXX / 4–6 char code), email, or a name.
     */
    fun smartSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) { _searchResults.value = emptyList(); return }
        val upper = trimmed.uppercase()
        val mode = when {
            upper.startsWith("OLV-") || upper.startsWith("WLEV-") -> SearchMode.FRIEND_CODE
            trimmed.contains("@") -> SearchMode.EMAIL
            else -> SearchMode.NAME
        }
        _searchMode.value = mode
        _isSearching.value = true
        viewModelScope.launch {
            try {
                val results = when (mode) {
                    SearchMode.FRIEND_CODE -> listOfNotNull(friendRepository.searchByFriendCode(trimmed))
                    SearchMode.EMAIL -> listOfNotNull(friendRepository.searchByEmail(trimmed))
                    SearchMode.NAME -> {
                        val byName = friendRepository.searchUsers(trimmed)
                        // A bare code like "7K3P" also works
                        val byCode = if (trimmed.length in 4..6 && !trimmed.contains(" ")) friendRepository.searchByFriendCode(trimmed) else null
                        (listOfNotNull(byCode) + byName).distinctBy { it.uid }
                    }
                }
                _searchResults.value = results
            } catch (e: Exception) {
                fail(e, "Search failed")
            }
            _isSearching.value = false
        }
    }

    fun clearSearch() { _query.value = ""; _searchResults.value = emptyList() }

    // ── Requests ──
    fun sendFriendRequest(toUid: String, toName: String) {
        if (toUid in _busy.value) return
        busy(toUid, true)
        viewModelScope.launch {
            friendRepository.sendFriendRequest(toUid, toName)
                .onSuccess { r -> ok(if (r == SendRequestResult.BECAME_FRIENDS) "You and $toName are now friends! 🎉" else "Request sent to $toName") }
                .onFailure { fail(it, "Couldn't send the request") }
            busy(toUid, false)
        }
    }

    fun acceptRequest(req: FriendRequest) {
        if (req.requestId in _busy.value) return
        busy(req.requestId, true)
        viewModelScope.launch {
            friendRepository.acceptFriendRequest(req.requestId, req.fromUid)
                .onSuccess { ok("You and ${req.fromName} are now friends! 🎉") }
                .onFailure { fail(it, "Couldn't accept") }
            busy(req.requestId, false)
        }
    }

    fun declineRequest(req: FriendRequest) {
        if (req.requestId in _busy.value) return
        busy(req.requestId, true)
        viewModelScope.launch {
            friendRepository.declineFriendRequest(req.requestId)
                .onSuccess { ok("Request declined") }
                .onFailure { fail(it, "Couldn't decline") }
            busy(req.requestId, false)
        }
    }

    fun cancelRequest(requestId: String, toName: String) {
        if (requestId in _busy.value) return
        busy(requestId, true)
        viewModelScope.launch {
            friendRepository.cancelFriendRequest(requestId)
                .onSuccess { ok("Request to $toName cancelled") }
                .onFailure { fail(it, "Couldn't cancel") }
            busy(requestId, false)
        }
    }

    /** Accept straight from search results (when they had already asked me). */
    fun acceptFromSearch(p: PersonResult) {
        val reqId = p.requestId ?: return
        acceptRequest(FriendRequest(requestId = reqId, fromUid = p.person.uid, fromName = p.person.displayName, toUid = currentUid ?: "", toName = ""))
    }

    fun removeFriend(friend: Friend) {
        if (friend.uid in _busy.value) return
        busy(friend.uid, true)
        viewModelScope.launch {
            friendRepository.removeFriend(friend.uid)
                .onSuccess { ok("Removed ${friend.displayName} from friends") }
                .onFailure { fail(it, "Couldn't remove friend") }
            busy(friend.uid, false)
        }
    }

    // ── Inbox ──
    fun markNotificationRead(notifId: String) { viewModelScope.launch { friendRepository.markNotificationRead(notifId) } }
    fun markAllRead() { viewModelScope.launch { friendRepository.markAllRead(); ok("All marked as read") } }
    fun deleteNotification(notifId: String) {
        viewModelScope.launch {
            friendRepository.deleteNotification(notifId)
            _downloadProgress.value = _downloadProgress.value - notifId
        }
    }
    /** React with an emoji and/or a short reply to something a friend shared. */
    fun react(n: InboxNotification, emoji: String?, message: String? = null) {
        viewModelScope.launch {
            friendRepository.reactToShare(n, emoji, message)
                .onSuccess { ok(if (message.isNullOrBlank()) "Sent ${emoji ?: ""} to ${n.fromName}" else "Reply sent to ${n.fromName}") }
                .onFailure { fail(it, "Couldn't send") }
        }
    }

    // ── Auto-save of shared songs ──
    val autoSaving: StateFlow<Map<String, Float>> get() = shareAutoSaver.saving
    val autoSaveEnabled: StateFlow<Boolean> get() = shareAutoSaver.enabled
    fun setAutoSave(on: Boolean) = shareAutoSaver.setEnabled(on)

    fun clearInbox() { viewModelScope.launch { friendRepository.clearInbox(); ok("Inbox cleared") } }

    /**
     * Download a shared song / audiobook.
     *  • Uploaded file (fileUrl) → downloaded as-is into Music/Olive or Audiobooks/Olive
     *  • YouTube share (videoId) → yt-dlp in the chosen [format] (default M4A)
     */
    fun downloadSharedSong(
        notif: InboxNotification,
        format: MediaFormat? = null,
        asAudiobook: Boolean = notif.isAudiobook,
    ) {
        val category = when {
            format != null && !format.isAudioOnly -> com.waleve.player.data.extractor.DownloadCategory.VIDEO
            asAudiobook -> com.waleve.player.data.extractor.DownloadCategory.AUDIOBOOK
            else -> com.waleve.player.data.extractor.DownloadCategory.SONG
        }
        val label = notif.songTitle ?: "Shared item"
        updateDownloadProgress(notif.notifId, 0f, "Starting…")

        // ── Uploaded file ──
        notif.fileUrl?.let { fileUrl ->
            viewModelScope.launch {
                val name = notif.fileName ?: "${label}.${notif.mimeType?.substringAfter('/') ?: "mp3"}"
                val file = supabaseStorage.downloadToCache(fileUrl, name) { p ->
                    updateDownloadProgress(notif.notifId, p, "${p.toInt()}%")
                }.getOrElse { e ->
                    updateDownloadProgress(notif.notifId, 0f, "Failed", isFailed = true)
                    _events.emit(FriendsUiEvent.Error(e.message ?: "Download failed"))
                    return@launch
                }
                updateDownloadProgress(notif.notifId, 100f, "Saving to library…")
                val saved = runCatching { mediaExtractor.saveToSharedStorage(file, category) }.getOrNull()
                file.delete()
                if (saved == null) {
                    updateDownloadProgress(notif.notifId, 0f, "Failed", isFailed = true)
                    _events.emit(FriendsUiEvent.Error("Couldn't save the file"))
                    return@launch
                }
                scanMusicUseCase()
                songDao.getSongByPath(saved)?.let { song ->
                    notif.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { songDao.updateAlbumArtUri(song.id, it) }
                }
                updateDownloadProgress(notif.notifId, 100f, "Done!", isComplete = true)
                _events.emit(FriendsUiEvent.Success("'$label' saved to ${if (asAudiobook) "Audiobooks" else "Songs"}!"))
            }
            return
        }

        // ── YouTube share ──
        val videoId = notif.videoId ?: run {
            updateDownloadProgress(notif.notifId, 0f, "Not downloadable", isFailed = true)
            return
        }
        val url = "https://music.youtube.com/watch?v=$videoId"
        val processId = "shared_song_${videoId}_${System.currentTimeMillis()}"

        viewModelScope.launch {
            val chosen = format ?: MediaExtractor.audioConversionChoices().first { it.extension == "m4a" }
            val result = mediaExtractor.download(
                url       = url,
                format    = chosen,
                processId = processId,
                category  = category,
                onProgress = { progress, _, line ->
                    val status = when {
                        line == "SAVING_TO_LIBRARY" -> "Saving to library…"
                        progress >= 99f             -> "Finalizing…"
                        else                        -> "${progress.toInt()}%"
                    }
                    updateDownloadProgress(notif.notifId, progress, status)
                },
            )

            result.onSuccess { r ->
                updateDownloadProgress(notif.notifId, 100f, "Done!", isComplete = true)
                scanMusicUseCase()
                val song = r.savedPath?.let { songDao.getSongByPath(it) }
                if (song != null) {
                    songDao.updateVideoId(song.id, videoId)
                    r.thumbnailUrl?.let { songDao.updateAlbumArtUri(song.id, it) }
                } else {
                    tagVideoId(videoId, notif.songTitle)
                }
                _events.emit(FriendsUiEvent.Success(r.message))
            }.onFailure { e ->
                updateDownloadProgress(notif.notifId, 0f, "Failed", isFailed = true)
                _events.emit(FriendsUiEvent.Error("Download failed: ${e.message}"))
            }
        }
    }

    /** Resolve a shared item into a playable Song (YouTube stream or the uploaded file URL). */
    suspend fun playSharedSong(notif: InboxNotification): com.waleve.player.domain.model.Song? {
        // Already saved on this phone → play the local copy (the upload may be deleted by now)
        notif.savedPath?.let { path ->
            songDao.getSongByPath(path)?.let { markNotificationRead(notif.notifId); return it.toDomain() }
        }
        notif.fileUrl?.let { fileUrl ->
            markNotificationRead(notif.notifId)
            return com.waleve.player.domain.model.Song(
                id = fileUrl.hashCode().toLong(),
                title = notif.songTitle ?: "Shared",
                artist = notif.artist ?: "Shared by ${notif.fromName}",
                album = if (notif.isAudiobook) (notif.songTitle ?: "") else "",
                albumArtUri = notif.thumbnailUrl?.takeIf { it.isNotBlank() },
                duration = notif.durationMs,
                path = fileUrl,
                source = "stream",
                mediaType = notif.mediaType,
            )
        }
        val videoId = notif.videoId ?: return null
        return try {
            val streamUrl = streamRepository.getStreamUrl(videoId)
            if (streamUrl != null) {
                markNotificationRead(notif.notifId)
                com.waleve.player.domain.model.Song(
                    id = videoId.hashCode().toLong(),
                    title = notif.songTitle ?: "Shared Song",
                    artist = notif.artist ?: "Shared by ${notif.fromName}",
                    album = "",
                    albumArtUri = notif.thumbnailUrl,
                    duration = notif.durationMs,
                    path = streamUrl,
                    source = "stream",
                    videoId = videoId,
                    mediaType = notif.mediaType,
                )
            } else {
                _events.emit(FriendsUiEvent.Error("Could not stream this song"))
                null
            }
        } catch (e: Exception) {
            _events.emit(FriendsUiEvent.Error("Stream failed: ${e.message}"))
            null
        }
    }

    private fun updateDownloadProgress(
        notifId: String,
        progress: Float,
        status: String,
        isComplete: Boolean = false,
        isFailed: Boolean = false,
    ) {
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
            put(notifId, SharedSongDownloadProgress(notifId, progress, status, isComplete, isFailed))
        }
    }

    /**
     * Tag a newly-scanned song in Room DB with its YouTube videoId.
     */
    private suspend fun tagVideoId(videoId: String?, title: String?) {
        if (videoId.isNullOrEmpty() || title.isNullOrEmpty()) return
        try {
            val pattern = "%${title.take(40)}%"
            val candidates = songDao.findRecentByTitle(pattern)
            val match = candidates.firstOrNull { it.videoId == null }
            if (match != null) {
                songDao.updateVideoId(match.id, videoId)
                android.util.Log.d("FriendsVM", "Tagged song ${match.id} ('${match.title}') with videoId=$videoId")
            }
        } catch (e: Exception) {
            android.util.Log.w("FriendsVM", "Failed to tag videoId=$videoId", e)
        }
    }
}

