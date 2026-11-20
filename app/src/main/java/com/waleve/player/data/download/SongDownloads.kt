package com.waleve.player.data.download

import android.util.Log
import com.waleve.player.data.extractor.DownloadCategory
import com.waleve.player.data.extractor.DownloadResult
import com.waleve.player.data.extractor.MediaExtractor
import com.waleve.player.data.extractor.MediaFormat
import com.waleve.player.data.local.dao.SongDao
import com.waleve.player.domain.model.SearchResult
import com.waleve.player.domain.usecase.ScanMusicUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/** How far one song's download has got. [progress] is 0–100. */
data class DownloadProgress(
    val videoId: String,
    val progress: Float,
    val status: String,
)

sealed interface SongDownloadEvent {
    data class Queued(val title: String) : SongDownloadEvent
    data class Done(val title: String) : SongDownloadEvent
    data class Failed(val title: String) : SongDownloadEvent
}

/**
 * Downloads YouTube songs picked in Search, the For you shelves and playlist pages.
 * It lives as long as the app, so a download keeps going when you leave the page you
 * started it from, and every screen shows the same progress. Two run at a time; the
 * rest wait their turn.
 */
@Singleton
class SongDownloads @Inject constructor(
    private val extractor: MediaExtractor,
    private val scanMusic: ScanMusicUseCase,
    private val songDao: SongDao,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.w(TAG, "download job failed", e) }
    )
    private val slots = Semaphore(2)

    private val _active = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    /** Downloads waiting or running, by videoId. */
    val active: StateFlow<Map<String, DownloadProgress>> = _active.asStateFlow()

    /** Finished in this session: covers the seconds before the library scan tags the new file. */
    private val justSaved = MutableStateFlow<Set<String>>(emptySet())

    /** videoIds already saved on the phone, so rows can show a tick instead of the arrow. */
    val saved: StateFlow<Set<String>> =
        combine(songDao.downloadedVideoIds(), justSaved) { inLibrary, recent -> inLibrary.toSet() + recent }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _events = MutableSharedFlow<SongDownloadEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SongDownloadEvent> = _events.asSharedFlow()

    fun start(result: SearchResult, chosenFormat: MediaFormat? = null, asAudiobook: Boolean = false) {
        val id = result.videoId
        if (id.isBlank() || _active.value.containsKey(id)) return
        val category = when {
            chosenFormat != null && !chosenFormat.isAudioOnly -> DownloadCategory.VIDEO
            asAudiobook -> DownloadCategory.AUDIOBOOK
            else -> DownloadCategory.SONG
        }
        setProgress(id, 0f, "Waiting…")
        _events.tryEmit(SongDownloadEvent.Queued(result.title))

        scope.launch {
            try {
                slots.withPermit {
                    setProgress(id, 0f, "Starting…")
                    // Default: M4A (fast, no quality loss)
                    val format = chosenFormat ?: MediaExtractor.audioConversionChoices().first { it.extension == "m4a" }
                    val outcome: Result<DownloadResult> = try {
                        extractor.download(
                            url = "https://music.youtube.com/watch?v=$id",
                            format = format,
                            processId = "search_dl_${id}_${System.currentTimeMillis()}",
                            category = category,
                            onProgress = { progress, _, line ->
                                val status = when {
                                    line == "SAVING_TO_LIBRARY" -> "Saving…"
                                    line.startsWith("POST:") -> line.removePrefix("POST:")
                                    progress >= 99f -> "Finalizing…"
                                    else -> "${progress.toInt()}%"
                                }
                                setProgress(id, progress, status)
                            },
                        )
                    } catch (e: Exception) {
                        Result.failure(e)
                    }

                    outcome.onSuccess { done ->
                        if (category != DownloadCategory.VIDEO) justSaved.update { it + id }
                        _active.update { it - id }
                        _events.tryEmit(SongDownloadEvent.Done(result.title))
                        tagInLibrary(done.savedPath, result)
                    }.onFailure { e ->
                        Log.w(TAG, "download failed: $id", e)
                        _active.update { it - id }
                        _events.tryEmit(SongDownloadEvent.Failed(result.title))
                    }
                }
            } finally {
                // never leave a spinner stuck on a row
                if (_active.value.containsKey(id)) _active.update { it - id }
            }
        }
    }

    private fun setProgress(id: String, progress: Float, status: String) {
        _active.update { it + (id to DownloadProgress(id, progress, status)) }
    }

    /** Scan the new file in, then tag it with its YouTube id and cover, so sharing and the tick work. */
    private suspend fun tagInLibrary(savedPath: String?, result: SearchResult) {
        runCatching { scanMusic() }
        runCatching {
            val song = savedPath?.let { songDao.getSongByPath(it) }
            if (song != null) {
                songDao.updateVideoId(song.id, result.videoId)
                result.thumbnailUrl?.let { songDao.updateAlbumArtUri(song.id, it) }
            } else {
                // The file was renamed on the way: match the newest song with the same title
                val clean = result.title.replace(Regex("[_\\-]"), " ").trim()
                val matches = songDao.findRecentByTitle("%$clean%")
                matches.firstOrNull { it.videoId == null }?.let { songDao.updateVideoId(it.id, result.videoId) }
                val thumb = result.thumbnailUrl
                if (!thumb.isNullOrEmpty()) matches.firstOrNull()?.let { songDao.updateAlbumArtUri(it.id, thumb) }
            }
        }
    }

    private companion object {
        const val TAG = "SongDownloads"
    }
}
