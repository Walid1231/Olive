package com.waleve.player.presentation.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.waleve.player.domain.model.Friend
import com.waleve.player.domain.model.FriendRequest
import com.waleve.player.domain.model.InboxNotification
import com.waleve.player.domain.model.InboxNotificationType
import com.waleve.player.domain.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import com.waleve.player.data.extractor.MediaExtractor
import com.waleve.player.data.extractor.MediaFormat
import com.waleve.player.data.local.dao.SongDao
import com.waleve.player.domain.usecase.ScanMusicUseCase
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

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val auth: FirebaseAuth,
    private val mediaExtractor: MediaExtractor,
    private val scanMusicUseCase: ScanMusicUseCase,
    private val songDao: SongDao,
    private val streamRepository: com.waleve.player.domain.repository.StreamRepository,
    private val shareNotificationHelper: com.waleve.player.service.ShareNotificationHelper,
) : ViewModel() {

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _inbox = MutableStateFlow<List<InboxNotification>>(emptyList())
    val inbox: StateFlow<List<InboxNotification>> = _inbox.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Friend>>(emptyList())
    val searchResults: StateFlow<List<Friend>> = _searchResults.asStateFlow()

    private val _outgoingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val outgoingRequests: StateFlow<List<FriendRequest>> = _outgoingRequests.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Detected search mode for UI hint
    private val _searchMode = MutableStateFlow<SearchMode>(SearchMode.NAME)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, SharedSongDownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, SharedSongDownloadProgress>> = _downloadProgress.asStateFlow()

    private val _events = MutableSharedFlow<FriendsUiEvent>()
    val events: SharedFlow<FriendsUiEvent> = _events.asSharedFlow()

    val currentUid: String? get() = auth.currentUser?.uid
    val currentDisplayName: String? get() = auth.currentUser?.displayName
    val currentEmail: String? get() = auth.currentUser?.email
    val isSignedIn: Boolean get() = auth.currentUser != null

    // Unread inbox count
    val unreadCount: Int get() = _inbox.value.count { !it.isRead }

    private var observersStarted = false

    // Track notification IDs we've already shown, to avoid re-notifying on resubscribe
    private val seenNotifIds = mutableSetOf<String>()

    init {
        if (isSignedIn) {
            startObservers()
        }
        // Watch for auth state changes so that if the user signs in after
        // ViewModel creation, observers are started automatically.
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null && !observersStarted) {
                startObservers()
            }
        }
    }

    private fun startObservers() {
        if (observersStarted) return
        observersStarted = true
        ensureProfile()
        observeFriends()
        observeInbox()
        loadOutgoingRequests()
    }

    fun refreshAfterSignIn() {
        observersStarted = false
        startObservers()
    }

    private fun ensureProfile() {
        val uid = currentUid ?: return
        val name = currentDisplayName ?: "Unknown"
        val email = currentEmail ?: ""
        viewModelScope.launch {
            try {
                friendRepository.ensureUserProfile(uid, name, email)
            } catch (e: Exception) {
                // Non-fatal — user may be offline
            }
        }
    }

    private fun observeFriends() {
        viewModelScope.launch {
            friendRepository.observeFriends()
                .catch { /* silent */ }
                .collect { _friends.value = it }
        }
    }

    private fun observeInbox() {
        viewModelScope.launch {
            friendRepository.observeInbox()
                .catch { /* silent */ }
                .collect { items ->
                    val previousIds = _inbox.value.map { it.notifId }.toSet()
                    _inbox.value = items

                    // Fire system notifications for truly new, unread share items
                    items.filter { notif ->
                        !notif.isRead
                                && notif.notifId !in previousIds
                                && notif.notifId !in seenNotifIds
                                && (notif.type == InboxNotificationType.SONG_SHARED
                                    || notif.type == InboxNotificationType.PLAYLIST_SHARED)
                    }.forEach { notif ->
                        seenNotifIds.add(notif.notifId)
                        shareNotificationHelper.showShareNotification(notif)
                    }
                }
        }
    }

    private fun loadOutgoingRequests() {
        viewModelScope.launch {
            try {
                _outgoingRequests.value = friendRepository.getOutgoingRequests()
            } catch (e: Exception) { /* silent */ }
        }
    }

    /**
     * Smart search that auto-detects query type:
     * - Starts with "WLEV-" → Friend Code lookup
     * - Contains "@" → Email lookup
     * - Otherwise → Name prefix search
     */
    fun smartSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _searchResults.value = emptyList()
            return
        }

        // Detect mode
        val mode = when {
            trimmed.uppercase().startsWith("WLEV-") -> SearchMode.FRIEND_CODE
            trimmed.contains("@") -> SearchMode.EMAIL
            else -> SearchMode.NAME
        }
        _searchMode.value = mode
        _isSearching.value = true

        viewModelScope.launch {
            try {
                val results = when (mode) {
                    SearchMode.FRIEND_CODE -> {
                        val friend = friendRepository.searchByFriendCode(trimmed)
                        if (friend != null) listOf(friend) else emptyList()
                    }
                    SearchMode.EMAIL -> {
                        val friend = friendRepository.searchByEmail(trimmed)
                        if (friend != null) listOf(friend) else emptyList()
                    }
                    SearchMode.NAME -> {
                        friendRepository.searchUsers(trimmed)
                    }
                }
                _searchResults.value = results
            } catch (e: Exception) {
                _events.emit(FriendsUiEvent.Error("Search failed: ${e.message}"))
            }
            _isSearching.value = false
        }
    }

    fun clearSearch() { _searchResults.value = emptyList() }

    fun sendFriendRequest(toUid: String, toName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = friendRepository.sendFriendRequest(toUid, toName)
            result.onSuccess {
                _events.emit(FriendsUiEvent.Success("Friend request sent to $toName!"))
                loadOutgoingRequests()
            }.onFailure { e ->
                _events.emit(FriendsUiEvent.Error(e.message ?: "Failed to send request"))
            }
            _isLoading.value = false
        }
    }

    fun acceptFriendRequest(notif: InboxNotification) {
        val requestId = notif.requestId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = friendRepository.acceptFriendRequest(requestId, notif.fromUid)
            result.onSuccess {
                _events.emit(FriendsUiEvent.Success("You and ${notif.fromName} are now friends!"))
            }.onFailure { e ->
                _events.emit(FriendsUiEvent.Error(e.message ?: "Failed to accept"))
            }
            _isLoading.value = false
        }
    }

    fun declineFriendRequest(notif: InboxNotification) {
        val requestId = notif.requestId ?: return
        viewModelScope.launch {
            friendRepository.declineFriendRequest(requestId)
            _events.emit(FriendsUiEvent.Success("Request declined"))
        }
    }

    fun markNotificationRead(notifId: String) {
        viewModelScope.launch { friendRepository.markNotificationRead(notifId) }
    }

    fun removeFriend(friend: Friend) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = friendRepository.removeFriend(friend.uid)
            result.onSuccess {
                _events.emit(FriendsUiEvent.Success("Removed ${friend.displayName} from friends"))
            }.onFailure { e ->
                _events.emit(FriendsUiEvent.Error(e.message ?: "Failed to remove friend"))
            }
            _isLoading.value = false
        }
    }

    /** Pending friend requests inbox only */
    fun friendRequestNotifs(): List<InboxNotification> =
        _inbox.value.filter { it.type == InboxNotificationType.FRIEND_REQUEST }

    /** Shared playlist notifications inbox only */
    fun sharedPlaylistNotifs(): List<InboxNotification> =
        _inbox.value.filter { it.type == InboxNotificationType.PLAYLIST_SHARED }

    /** Download a single shared song */
    fun downloadSharedSong(notif: InboxNotification) {
        val videoId = notif.videoId ?: return
        val url = "https://music.youtube.com/watch?v=$videoId"
        val processId = "shared_song_${videoId}_${System.currentTimeMillis()}"

        updateDownloadProgress(notif.notifId, 0f, "Starting…")

        viewModelScope.launch {
            val format = MediaFormat(
                formatId    = "bestaudio",
                extension   = "m4a",
                quality     = "Best",
                fileSize    = 0L,
                isAudioOnly = true,
                isVideoOnly = false,
                label       = "Best Audio",
                tag         = "Download",
            )

            val result = mediaExtractor.download(
                url       = url,
                format    = format,
                processId = processId,
                onProgress = { progress, _, line ->
                    val status = when {
                        line == "SAVING_TO_LIBRARY" -> "Saving to library…"
                        progress >= 99f             -> "Finalizing…"
                        else                        -> "${progress.toInt()}%"
                    }
                    updateDownloadProgress(notif.notifId, progress, status)
                },
            )

            result.onSuccess {
                updateDownloadProgress(notif.notifId, 100f, "Done!", isComplete = true)
                scanMusicUseCase()
                // Tag the newly-scanned song with its YouTube videoId
                tagVideoId(videoId, notif.songTitle)
                _events.emit(FriendsUiEvent.Success("'${notif.songTitle}' saved to library!"))
            }.onFailure { e ->
                updateDownloadProgress(notif.notifId, 0f, "Failed", isFailed = true)
                _events.emit(FriendsUiEvent.Error("Download failed: ${e.message}"))
            }
        }
    }

    /** Resolve a shared song notification into a playable Song via stream URL */
    suspend fun playSharedSong(notif: InboxNotification): com.waleve.player.domain.model.Song? {
        val videoId = notif.videoId ?: return null
        return try {
            val streamUrl = streamRepository.getStreamUrl(videoId)
            if (streamUrl != null) {
                markNotificationRead(notif.notifId)
                com.waleve.player.domain.model.Song(
                    id = videoId.hashCode().toLong(),
                    title = notif.songTitle ?: "Shared Song",
                    artist = "Shared by ${notif.fromName}",
                    album = "",
                    albumArtUri = notif.thumbnailUrl,
                    duration = 0L,
                    path = streamUrl,
                    source = "stream",
                    videoId = videoId,
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

enum class SearchMode {
    NAME,           // prefix search by display name
    FRIEND_CODE,    // exact match by WLEV-XXXX code
    EMAIL,          // exact match by email
}
