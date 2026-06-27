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
) : ViewModel() {

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
    fun downloadSong(result: SearchResult, selectedQuality: AudioQuality? = null) {
        val url = "https://music.youtube.com/watch?v=${result.videoId}"
        val processId = "search_dl_${result.videoId}_${System.currentTimeMillis()}"

        _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
            put(result.videoId, DownloadProgress(result.videoId, 0f, "Starting…"))
        }

        viewModelScope.launch {
            _events.emit(SearchUiEvent.DownloadStarted(result.title))

            // Build format — use "bestaudio" for yt-dlp to select the best
            val format = MediaFormat(
                formatId = "bestaudio",
                extension = "m4a",
                quality = selectedQuality?.label ?: "Best",
                fileSize = selectedQuality?.contentLength ?: 0L,
                isAudioOnly = true,
                isVideoOnly = false,
                label = selectedQuality?.label ?: "Best Audio",
                tag = "Download",
            )

            val result2 = extractor.download(
                url = url,
                format = format,
                processId = processId,
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
                tagDownloadedSongWithVideoId(result.title, result.videoId)
                persistThumbnailForDownload(result.title, result.thumbnailUrl)
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
