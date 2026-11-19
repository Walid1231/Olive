package com.waleve.player.presentation.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waleve.player.data.extractor.MediaExtractor
import com.waleve.player.data.extractor.MediaFormat
import com.waleve.player.data.local.dao.SongDao
import com.waleve.player.domain.model.SearchResult
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.repository.AudioQuality
import com.waleve.player.domain.repository.StreamRepository
import com.waleve.player.domain.usecase.ScanMusicUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SearchUiEvent {
    data class Error(val message: String) : SearchUiEvent()
    data class DownloadStarted(val title: String) : SearchUiEvent()
    data class DownloadComplete(val title: String) : SearchUiEvent()
}

data class DownloadProgress(
    val videoId: String,
    val progress: Float,  // 0-100
    val status: String,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    private val extractor: MediaExtractor,
    private val scanMusicUseCase: ScanMusicUseCase,
    private val songDao: SongDao,
    @ApplicationContext private val context: Context,
    private val ytMusic: com.waleve.player.data.remote.YouTubeMusicBrowser,
) : ViewModel() {

    // ── Discover (YouTube Music home shelves, shown before typing) ──
    private val _home = MutableStateFlow(ytMusic.cachedHome())
    val home: StateFlow<com.waleve.player.data.remote.YtHome?> = _home.asStateFlow()
    private val _homeLoading = MutableStateFlow(false)
    val homeLoading: StateFlow<Boolean> = _homeLoading.asStateFlow()
    private val _homeError = MutableStateFlow(false)
    val homeError: StateFlow<Boolean> = _homeError.asStateFlow()
    private val _chip = MutableStateFlow<com.waleve.player.data.remote.YtChip?>(null)
    val chip: StateFlow<com.waleve.player.data.remote.YtChip?> = _chip.asStateFlow()
    /** Main home (no chip), so tapping "For you" again is instant. */
    private var mainHome: com.waleve.player.data.remote.YtHome? = _home.value
    private var homeJob: Job? = null

    /** Refresh shelves if they're missing or older than 30 minutes (or [force]). */
    fun loadHome(force: Boolean = false) {
        val fresh = mainHome?.let { System.currentTimeMillis() - it.fetchedAt < 30 * 60_000 } ?: false
        if (!force && fresh && _chip.value == null) return
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _homeLoading.value = true; _homeError.value = false
            runCatching { ytMusic.home(_chip.value?.params) }
                .onSuccess { h ->
                    if (_chip.value == null) mainHome = h
                    // keep the chip row from the main page when a mood page has none
                    _home.value = if (h.chips.isEmpty()) h.copy(chips = mainHome?.chips.orEmpty()) else h
                }
                .onFailure { _homeError.value = _home.value == null }
            _homeLoading.value = false
        }
    }

    fun selectChip(chip: com.waleve.player.data.remote.YtChip?) {
        if (chip == _chip.value) return
        _chip.value = chip
        if (chip == null && mainHome != null) { _home.value = mainHome; homeJob?.cancel(); _homeLoading.value = false; return }
        loadHome(force = true)
    }

    /** Queue for playing a shelf / playlist: YouTube ids are turned into streams by the player. */
    fun songsOf(items: List<com.waleve.player.data.remote.YtItem>): List<Song> = items.filter { it.playable }.map { it.toSong() }

    // ── Suggestions while typing ──
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    /** Run a search right away (suggestion / chip tap). */
    fun searchNow(q: String) {
        _query.value = q
        _suggestions.value = emptyList()
        performSearch(q)
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _qualities = MutableStateFlow<List<AudioQuality>>(emptyList())
    val qualities: StateFlow<List<AudioQuality>> = _qualities.asStateFlow()

    private val _isLoadingQualities = MutableStateFlow(false)
    val isLoadingQualities: StateFlow<Boolean> = _isLoadingQualities.asStateFlow()

    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = _activeDownloads.asStateFlow()

    private val _events = MutableSharedFlow<SearchUiEvent>()
    val events: SharedFlow<SearchUiEvent> = _events.asSharedFlow()

    private var searchJob: Job? = null
    private var qualityVideoId: String? = null

    init {
        observeQuery()
        observeSuggestions()
        loadHome()
    }

    @OptIn(FlowPreview::class)
    private fun observeSuggestions() {
        viewModelScope.launch {
            _query.debounce(200).distinctUntilChanged().collect { q ->
                _suggestions.value = if (q.isBlank()) emptyList() else runCatching { ytMusic.suggestions(q) }.getOrDefault(emptyList())
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeQuery() {
        viewModelScope.launch {
            _query
                .debounce(400)
                .distinctUntilChanged()
                .filter { it.length >= 2 }
                .collect { query ->
                    performSearch(query)
                }
        }
    }

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
        if (newQuery.length < 2) {
            _searchResults.value = emptyList()
            if (newQuery.isEmpty()) _suggestions.value = emptyList()
        }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            try {
                val results = streamRepository.searchSongs(query)
                _searchResults.value = results
            } catch (e: Exception) {
                val message = when {
                    e is java.net.UnknownHostException || e.message?.contains("Unable to resolve host") == true ->
                        "No internet connection. Please check your network."
                    else -> "Search failed. Please try again."
                }
                _events.emit(SearchUiEvent.Error(message))
            }
            _isSearching.value = false
        }
    }

    /**
     * Called when user taps Play on a search result.
     * Creates a Song from the stream URL and returns it.
     */
    suspend fun getStreamSong(result: SearchResult): Song? {
        return try {
            val streamUrl = streamRepository.getStreamUrl(result.videoId)
            if (streamUrl != null) {
                Song(
                    id = result.videoId.hashCode().toLong(),
                    title = result.title,
                    artist = result.artist,
                    album = result.album ?: "",
                    albumArtUri = result.thumbnailUrl,
                    duration = result.durationMs,
                    path = streamUrl,
                    source = "stream",
                    videoId = result.videoId,   // carry videoId so sharing works
                )
            } else null
        } catch (e: Exception) {
            _events.emit(SearchUiEvent.Error("Failed to start streaming."))
            null
        }
    }

    /**
     * Fetch available audio qualities before downloading.
     */
    fun loadQualities(videoId: String) {
        qualityVideoId = videoId
        _qualities.value = emptyList()
        _isLoadingQualities.value = true
        viewModelScope.launch {
            try {
                val q = streamRepository.getAvailableQualities(videoId)
                _qualities.value = q
            } catch (e: Exception) {
                _events.emit(SearchUiEvent.Error("Failed to load audio qualities."))
            }
            _isLoadingQualities.value = false
        }
    }

    fun clearQualities() {
        _qualities.value = emptyList()
        qualityVideoId = null
    }

    /**
     * Download a song using yt-dlp (reuses existing MediaExtractor pipeline).
     */
    fun downloadSong(result: SearchResult, chosenFormat: MediaFormat? = null, asAudiobook: Boolean = false) {
        val category = when {
            chosenFormat != null && !chosenFormat.isAudioOnly -> com.waleve.player.data.extractor.DownloadCategory.VIDEO
            asAudiobook -> com.waleve.player.data.extractor.DownloadCategory.AUDIOBOOK
            else -> com.waleve.player.data.extractor.DownloadCategory.SONG
        }
        val url = "https://music.youtube.com/watch?v=${result.videoId}"
        val processId = "search_dl_${result.videoId}_${System.currentTimeMillis()}"

        _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
            put(result.videoId, DownloadProgress(result.videoId, 0f, "Starting…"))
        }

        viewModelScope.launch {
            _events.emit(SearchUiEvent.DownloadStarted(result.title))

            // Default: M4A (fast, no quality loss)
            val format = chosenFormat ?: MediaExtractor.audioConversionChoices().first { it.extension == "m4a" }

            val result2 = extractor.download(
                url = url,
                format = format,
                processId = processId,
                category = category,
                onProgress = { progress, _, line ->
                    val status = when {
                        line == "SAVING_TO_LIBRARY" -> "Saving to Library…"
                        progress >= 99f -> "Finalizing…"
                        else -> "${progress.toInt()}%"
                    }
                    _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
                        put(result.videoId, DownloadProgress(result.videoId, progress, status))
                    }
                },
            )

            result2.onSuccess { downloadResult ->
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(result.videoId) }
                _events.emit(SearchUiEvent.DownloadComplete(result.title))
                // Scan and then tag the new song with its videoId + thumbnail
                scanMusicUseCase()
                val saved = downloadResult.savedPath?.let { songDao.getSongByPath(it) }
                if (saved != null) {
                    songDao.updateVideoId(saved.id, result.videoId)
                    result.thumbnailUrl?.let { songDao.updateAlbumArtUri(saved.id, it) }
                } else {
                    tagDownloadedSongWithVideoId(result.title, result.videoId)
                    persistThumbnailForDownload(result.title, result.thumbnailUrl)
                }
            }.onFailure { err ->
                _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { remove(result.videoId) }
                _events.emit(SearchUiEvent.Error("Download failed."))
            }
        }
    }

    /**
     * After scan, find the newly-added song by title and tag it with the YouTube videoId.
     * This enables the song to be shared as "downloadable" (not local-only) later.
     */
    private suspend fun tagDownloadedSongWithVideoId(title: String, videoId: String) {
        try {
            // Sanitize title for a fuzzy match (downloaded filename may differ slightly)
            val cleanTitle = title.replace(Regex("[_\\-]"), " ").trim()
            val matches = songDao.findRecentByTitle("%$cleanTitle%")
            val target = matches.firstOrNull { it.videoId == null }
            if (target != null) {
                songDao.updateVideoId(target.id, videoId)
            }
        } catch (_: Exception) { /* best-effort */ }
    }

    /**
     * Persist the YouTube thumbnail URL as the song's albumArtUri so
     * thumbnails appear in the library, now playing, mini player, etc.
     */
    private suspend fun persistThumbnailForDownload(title: String, thumbnailUrl: String?) {
        if (thumbnailUrl.isNullOrEmpty()) return
        try {
            val cleanTitle = title.replace(Regex("[_\\-]"), " ").trim()
            val matches = songDao.findRecentByTitle("%$cleanTitle%")
            val target = matches.firstOrNull()
            if (target != null) {
                songDao.updateAlbumArtUri(target.id, thumbnailUrl)
            }
        } catch (_: Exception) { /* best-effort */ }
    }
}

/** A YouTube Music song as a queue entry (the player resolves ytm://stream/ ids). */
fun com.waleve.player.data.remote.YtItem.toSong(): Song = Song(
    id = (videoId ?: title).hashCode().toLong(),
    title = title,
    artist = artist,
    album = subtitle.split(" • ").getOrNull(1).orEmpty(),
    albumArtUri = thumb,
    duration = durationMs,
    path = "ytm://stream/$videoId",
    source = "stream",
    videoId = videoId,
)

fun com.waleve.player.data.remote.YtItem.toSearchResult(): com.waleve.player.domain.model.SearchResult =
    com.waleve.player.domain.model.SearchResult(
        videoId = videoId ?: "", title = title, artist = artist,
        album = subtitle.split(" • ").getOrNull(1), thumbnailUrl = thumb, duration = duration, durationMs = durationMs,
    )
