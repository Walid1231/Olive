package com.waleve.player.presentation.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waleve.player.data.extractor.MediaExtractor
import com.waleve.player.data.extractor.MediaFormat
import com.waleve.player.data.local.dao.SongDao
import com.waleve.player.domain.model.SharedPlaylist
import com.waleve.player.domain.model.SharedTrack
import com.waleve.player.domain.repository.SharedPlaylistRepository
import com.waleve.player.domain.repository.StreamRepository
import com.waleve.player.domain.usecase.ScanMusicUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SharedPlaylistUiEvent {
    data class Success(val message: String) : SharedPlaylistUiEvent()
    data class Error(val message: String) : SharedPlaylistUiEvent()
    data class Imported(val localPlaylistId: Long) : SharedPlaylistUiEvent()
}

data class TrackDownloadProgress(
    val videoId: String,
    val progress: Float,   // 0-100
    val status: String,
    val isComplete: Boolean = false,
    val isFailed: Boolean = false,
)

@HiltViewModel
class SharedPlaylistViewModel @Inject constructor(
    private val sharedPlaylistRepository: SharedPlaylistRepository,
    private val mediaExtractor: MediaExtractor,
    private val scanMusicUseCase: ScanMusicUseCase,
    private val songDao: SongDao,
) : ViewModel() {

    private val _sharedPlaylist = MutableStateFlow<SharedPlaylist?>(null)
    val sharedPlaylist: StateFlow<SharedPlaylist?> = _sharedPlaylist.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, TrackDownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, TrackDownloadProgress>> = _downloadProgress.asStateFlow()

    private val _events = MutableSharedFlow<SharedPlaylistUiEvent>()
    val events: SharedFlow<SharedPlaylistUiEvent> = _events.asSharedFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    fun loadSharedPlaylist(shareId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = sharedPlaylistRepository.getSharedPlaylist(shareId)
            result.onSuccess { _sharedPlaylist.value = it }
                .onFailure { _events.emit(SharedPlaylistUiEvent.Error("Could not load playlist: ${it.message}")) }
            _isLoading.value = false
        }
    }

    fun importPlaylist(shareId: String) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = sharedPlaylistRepository.importSharedPlaylist(shareId)
            result.onSuccess { localId ->
                _events.emit(SharedPlaylistUiEvent.Imported(localId))
            }.onFailure { e ->
                _events.emit(SharedPlaylistUiEvent.Error("Import failed: ${e.message}"))
            }
            _isImporting.value = false
        }
    }

    /**
     * Download a single remote track from the shared playlist.
     */
    fun downloadTrack(track: SharedTrack) {
        val videoId = track.videoId ?: return
        val url = "https://music.youtube.com/watch?v=$videoId"
        val processId = "shared_dl_${videoId}_${System.currentTimeMillis()}"

        updateProgress(videoId, 0f, "Starting…")

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
                    updateProgress(videoId, progress, status)
                },
            )

            result.onSuccess {
                updateProgress(videoId, 100f, "Done!", isComplete = true)
                scanMusicUseCase()
                // Tag the newly-scanned song with its YouTube videoId
                tagVideoId(videoId, track.title)
                _events.emit(SharedPlaylistUiEvent.Success("'${track.title}' saved to library!"))
            }.onFailure { e ->
                updateProgress(videoId, 0f, "Failed", isFailed = true)
                _events.emit(SharedPlaylistUiEvent.Error("Download failed: ${e.message}"))
            }
        }
    }

    /**
     * Download ALL remote tracks from the loaded shared playlist.
     */
    fun downloadAll() {
        val playlist = _sharedPlaylist.value ?: return
        val remoteTracks = playlist.tracks.filter { !it.isLocalOnly && it.videoId != null }
        if (remoteTracks.isEmpty()) {
            viewModelScope.launch {
                _events.emit(SharedPlaylistUiEvent.Error("No downloadable tracks in this playlist"))
            }
            return
        }
        remoteTracks.forEach { downloadTrack(it) }
        viewModelScope.launch {
            _events.emit(SharedPlaylistUiEvent.Success("Queued ${remoteTracks.size} tracks for download!"))
        }
    }

    private fun updateProgress(
        videoId: String,
        progress: Float,
        status: String,
        isComplete: Boolean = false,
        isFailed: Boolean = false,
    ) {
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
            put(videoId, TrackDownloadProgress(videoId, progress, status, isComplete, isFailed))
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
                android.util.Log.d("SharedPlaylistVM", "Tagged song ${match.id} ('${match.title}') with videoId=$videoId")
            }
        } catch (e: Exception) {
            android.util.Log.w("SharedPlaylistVM", "Failed to tag videoId=$videoId", e)
        }
    }
}
