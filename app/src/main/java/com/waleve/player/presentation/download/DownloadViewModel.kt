package com.waleve.player.presentation.download

import android.os.Environment
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waleve.player.data.extractor.MediaExtractor
import com.waleve.player.data.extractor.MediaFormat
import com.waleve.player.data.extractor.MediaInfo
import com.waleve.player.data.local.dao.SongDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.waleve.player.data.mapper.toDomain
import javax.inject.Inject
import com.waleve.player.domain.usecase.ScanMusicUseCase

sealed class DownloadUiState {
    data object Idle : DownloadUiState()
    data object Loading : DownloadUiState()
    data class Ready(val info: MediaInfo) : DownloadUiState()
    data class PlaylistReady(val items: List<com.waleve.player.data.extractor.PlaylistItem>) : DownloadUiState()
    data class Downloading(val progress: Float, val eta: String) : DownloadUiState()
    data class Done(
        val message: String,
        val trackTitle: String = "",
        val trackArtist: String = "",
        val thumbnailUrl: String? = null,
        val isBatch: Boolean = false,
        val batchCount: Int = 0,
    ) : DownloadUiState()
    data class Error(val message: String) : DownloadUiState()
}

@HiltViewModel
class DownloadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extractor: MediaExtractor,
    private val scanMusicUseCase: ScanMusicUseCase,
    private val songDao: SongDao,
    private val player: com.waleve.player.service.PlaybackController,
) : ViewModel() {

    /** Last few downloads, shown on the empty page. */
    val recent: StateFlow<List<com.waleve.player.domain.model.Song>> = songDao.getRecentDownloads()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun playRecent(index: Int) {
        val list = recent.value
        if (list.isNotEmpty()) player.setQueue(list, index.coerceIn(0, list.lastIndex))
    }

    init {
        // A link shared from another app: fill it in and fetch the formats right away
        viewModelScope.launch {
            com.waleve.player.data.download.SharedLinkInbox.pending.collect { link ->
                if (link != null) {
                    com.waleve.player.data.download.SharedLinkInbox.take()
                    reset(); updateUrl(link); extractInfo()
                }
            }
        }
    }

    /** Paste + go in one tap. */
    fun pasteAndGo(text: String?) {
        val link = com.waleve.player.data.download.SharedLinkInbox.firstUrlIn(text) ?: return
        updateUrl(link)
        extractInfo()
    }

    private val _uiState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private var currentUrl: String = ""
    private var downloadJob: kotlinx.coroutines.Job? = null
    private var currentProcessId: String? = null
    /** Last extracted media info — used to populate the Done success card. */
    private var lastMediaInfo: MediaInfo? = null

    fun updateUrl(newUrl: String) {
        _url.value = newUrl
    }

    fun extractInfo() {
        val targetUrl = url.value.trim()
        if (targetUrl.isEmpty()) return

        currentUrl = targetUrl
        _uiState.value = DownloadUiState.Loading

        viewModelScope.launch {
            if (targetUrl.contains("list=")) {
                val result = extractor.extractPlaylistInfo(targetUrl)
                result.onSuccess { items ->
                    if (items.isEmpty()) {
                        _uiState.value = DownloadUiState.Error("No items found in playlist")
                    } else {
                        _uiState.value = DownloadUiState.PlaylistReady(items)
                    }
                }.onFailure { err ->
                    _uiState.value = DownloadUiState.Error(err.message ?: "Failed to extract playlist")
                }
            } else {
                val result = extractor.extractInfo(targetUrl)
                result.onSuccess { info ->
                    _uiState.value = DownloadUiState.Ready(info)
                    lastMediaInfo = info
                }.onFailure { err ->
                    _uiState.value = DownloadUiState.Error(err.message ?: "Unknown error")
                }
            }
        }
    }

    fun download(format: MediaFormat, asAudiobook: Boolean = false) {
        val category = when {
            !format.isAudioOnly -> com.waleve.player.data.extractor.DownloadCategory.VIDEO
            asAudiobook -> com.waleve.player.data.extractor.DownloadCategory.AUDIOBOOK
            else -> com.waleve.player.data.extractor.DownloadCategory.SONG
        }
        if (currentUrl.isEmpty()) return

        val processId = "waleve_dl_${System.currentTimeMillis()}"
        currentProcessId = processId

        _uiState.value = DownloadUiState.Downloading(0f, "Starting…")
        downloadJob = viewModelScope.launch {
            val result = extractor.download(
                url = currentUrl,
                format = format,
                processId = processId,
                category = category,
                onProgress = { progress, etaSec, line ->
                    val etaText = when {
                        line == "SAVING_TO_LIBRARY" -> "Saving to Library…"
                        line.startsWith("POST:") -> line.removePrefix("POST:")
                        etaSec > 0 -> if (etaSec >= 60) "${etaSec / 60}m ${etaSec % 60}s remaining" else "${etaSec}s remaining"
                        progress >= 99f -> "Finalizing…"
                        else -> "Processing…"
                    }
                    _uiState.value = DownloadUiState.Downloading(progress, etaText)
                },
            )
            result.onSuccess { downloadResult ->
                val info = lastMediaInfo
                _uiState.value = DownloadUiState.Done(
                    message      = downloadResult.message,
                    trackTitle   = info?.title ?: "",
                    trackArtist  = info?.artist ?: "",
                    thumbnailUrl = info?.thumbnailUrl,
                )
                lastMediaInfo = null
                viewModelScope.launch {
                    scanMusicUseCase()
                    // Tag the newly-scanned song with its YouTube videoId and thumbnail
                    val byPath = downloadResult.savedPath?.let { songDao.getSongByPath(it) }
                    if (byPath != null) {
                        downloadResult.videoId?.let { songDao.updateVideoId(byPath.id, it) }
                        (downloadResult.thumbnailUrl ?: info?.thumbnailUrl)?.let { songDao.updateAlbumArtUri(byPath.id, it) }
                    } else {
                        tagVideoId(downloadResult.videoId, info?.title)
                        persistThumbnailUrl(info?.title, downloadResult.thumbnailUrl ?: info?.thumbnailUrl)
                    }
                }
            }.onFailure { error ->
                _uiState.value = DownloadUiState.Error(
                    error.message ?: "Download failed"
                )
            }
            currentProcessId = null
        }
    }

    fun downloadBatch(urls: List<String>, isAudioOnly: Boolean, asAudiobook: Boolean = false) {
        val category = when {
            !isAudioOnly -> com.waleve.player.data.extractor.DownloadCategory.VIDEO
            asAudiobook -> com.waleve.player.data.extractor.DownloadCategory.AUDIOBOOK
            else -> com.waleve.player.data.extractor.DownloadCategory.SONG
        }
        if (urls.isEmpty()) return
        
        val processId = "waleve_batch_${System.currentTimeMillis()}"
        currentProcessId = processId
        
        _uiState.value = DownloadUiState.Downloading(0f, "Starting batch download...")
        downloadJob = viewModelScope.launch {
            var successCount = 0
            val batchResults = mutableListOf<com.waleve.player.data.extractor.DownloadResult>()
            for ((index, itemUrl) in urls.withIndex()) {
                if (currentProcessId != processId) break // cancelled
                
                _uiState.value = DownloadUiState.Downloading(
                    (index.toFloat() / urls.size) * 100f, 
                    "Downloading ${index + 1} of ${urls.size}..."
                )
                
                // For batch, we just download the best audio format
                // In a real app, you might want to fetch formats for each, but yt-dlp 
                // can download audio directly if we pass format="bestaudio"
                val format = MediaFormat(
                    formatId = if (isAudioOnly) "bestaudio[ext=m4a]/bestaudio" else "video_720",
                    extension = if (isAudioOnly) "m4a" else "mp4",
                    quality = "Best",
                    fileSize = 0L,
                    isAudioOnly = isAudioOnly,
                    isVideoOnly = false,
                    label = "Batch Item",
                    tag = "Batch"
                )
                
                val result = extractor.download(
                    url = itemUrl,
                    format = format,
                    processId = processId,
                    category = category,
                    onProgress = { progress, _, _ -> 
                        val overallProgress = ((index + (progress / 100f)) / urls.size) * 100f
                        _uiState.value = DownloadUiState.Downloading(
                            overallProgress,
                            "Downloading ${index + 1} of ${urls.size} (${progress.toInt()}%)"
                        )
                    }
                )
                result.onSuccess { downloadResult ->
                    successCount++
                    // Tag videoId after individual download succeeds
                    batchResults.add(downloadResult)
                }
            }
            
            if (currentProcessId == processId) {
                if (successCount == 0) {
                    _uiState.value = DownloadUiState.Error("Failed to download playlist. Please try again.")
                } else if (successCount < urls.size) {
                    _uiState.value = DownloadUiState.Done("Downloaded $successCount of ${urls.size} items. Some files failed.")
                } else {
                    _uiState.value = DownloadUiState.Done("Successfully downloaded all ${urls.size} items!")
                }
                viewModelScope.launch {
                    scanMusicUseCase()
                    // Tag each downloaded file with its videoId + thumbnail (matched by saved path)
                    batchResults.forEach { r ->
                        val song = r.savedPath?.let { songDao.getSongByPath(it) } ?: return@forEach
                        r.videoId?.let { songDao.updateVideoId(song.id, it) }
                        r.thumbnailUrl?.let { songDao.updateAlbumArtUri(song.id, it) }
                    }
                }
            }
            currentProcessId = null
        }
    }

    /**
     * Tag a newly-scanned song in Room DB with its YouTube videoId.
     * Uses title-based fuzzy matching from [SongDao.findRecentByTitle].
     */
    private suspend fun tagVideoId(videoId: String?, title: String?) {
        if (videoId.isNullOrEmpty() || title.isNullOrEmpty()) return
        try {
            // Find recently-added songs whose title matches the downloaded track
            val pattern = "%${title.take(40)}%"
            val candidates = songDao.findRecentByTitle(pattern)
            val match = candidates.firstOrNull { it.videoId == null }
            if (match != null) {
                songDao.updateVideoId(match.id, videoId)
                android.util.Log.d("DownloadVM", "Tagged song ${match.id} ('${match.title}') with videoId=$videoId")
            }
        } catch (e: Exception) {
            android.util.Log.w("DownloadVM", "Failed to tag videoId=$videoId", e)
        }
    }

    /**
     * Persist the YouTube thumbnail URL as the song's albumArtUri so
     * thumbnails appear in the library, now playing, mini player, etc.
     */
    private suspend fun persistThumbnailUrl(title: String?, thumbnailUrl: String?) {
        if (title.isNullOrEmpty() || thumbnailUrl.isNullOrEmpty()) return
        try {
            val pattern = "%${title.take(40)}%"
            val candidates = songDao.findRecentByTitle(pattern)
            val match = candidates.firstOrNull()
            if (match != null) {
                songDao.updateAlbumArtUri(match.id, thumbnailUrl)
                android.util.Log.d("DownloadVM", "Set albumArtUri for song ${match.id} ('${match.title}')")
            }
        } catch (e: Exception) {
            android.util.Log.w("DownloadVM", "Failed to persist thumbnailUrl", e)
        }
    }

    fun reset() {
        _uiState.value = DownloadUiState.Idle
        _url.value = ""
    }

    fun cancelDownload() {
        // Kill the native yt-dlp process first
        currentProcessId?.let { extractor.cancelDownload(it) }
        currentProcessId = null
        downloadJob?.cancel()
        downloadJob = null
        _uiState.value = DownloadUiState.Idle
        // Clean up any partial cache files
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val cacheDir = java.io.File(context.cacheDir, "yt_dlp_downloads")
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }
}
