package com.waleve.player.data.history

import android.content.Context
import android.util.Log
import com.waleve.player.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notes what you listen to, for the For you page: every song / audiobook gets one entry
 * with how much of it you heard, whether you finished it, or skipped it early.
 * The player calls [tick] about twice a second; only time that actually played counts
 * (seeking doesn't).
 */
@Singleton
class ListeningTracker @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: ListeningDao,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.w(TAG, "history write failed", e) }
    )
    private val prefs = context.getSharedPreferences("listening", Context.MODE_PRIVATE)

    private val _paused = MutableStateFlow(prefs.getBoolean(KEY_PAUSED, false))
    /** "Pause listening history": nothing new is noted while true. */
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    fun setPaused(paused: Boolean) {
        _paused.value = paused
        prefs.edit().putBoolean(KEY_PAUSED, paused).apply()
        if (paused) { cur?.let(::finish); cur = null }
    }

    /** Bumped whenever an entry is saved, so the For you page knows to refresh. */
    private val _saved = MutableStateFlow(0)
    val saved: StateFlow<Int> = _saved.asStateFlow()

    private class Session(
        val song: Song,
        val key: String,
        val startedAt: Long,
        var durationMs: Long,
        var listenedMs: Long = 0,
        var lastPos: Long = -1,
        var maxPos: Long = 0,
        var lastPlayingAt: Long = startedAt,
    )

    private var cur: Session? = null
    private var lastPersist = 0L

    init {
        scope.launch {
            restoreUnfinished()
            dao.deleteBefore(System.currentTimeMillis() - 365L * DAY)
        }
    }

    fun keyOf(song: Song): String = song.videoId?.let { "yt:$it" } ?: "local:${song.path}"

    fun tick(song: Song?, isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        val now = System.currentTimeMillis()
        val key = song?.let(::keyOf)
        cur?.let { s ->
            val repeated = key == s.key && s.durationMs > 0 && s.lastPos > s.durationMs * 0.9 && positionMs in 0..5_000
            val idle = !isPlaying && now - s.lastPlayingAt > 30 * 60_000L
            if (key != s.key || repeated || idle) { finish(s); cur = null }
        }
        if (song == null || key == null || _paused.value) return
        val s = cur ?: run {
            if (!isPlaying) return
            Session(song, key, now, durationMs.takeIf { it > 0 } ?: song.duration).also { cur = it }
        }
        if (durationMs > 0) s.durationMs = durationMs
        if (isPlaying) {
            if (s.lastPos >= 0) {
                val d = positionMs - s.lastPos
                if (d in 1..4_000) s.listenedMs += d
            }
            s.lastPlayingAt = now
        }
        s.lastPos = positionMs
        if (positionMs > s.maxPos) s.maxPos = positionMs
        if (now - lastPersist > 15_000) { lastPersist = now; persist(s) }
    }

    private fun finish(s: Session) {
        prefs.edit().remove(KEY_OPEN).apply()
        if (s.listenedMs < 1_000) return   // never really played (error, instant skip of a broken link)
        val dur = s.durationMs.takeIf { it > 0 } ?: s.song.duration
        val frac = if (dur > 0) s.listenedMs.toFloat() / dur else 0f
        val reachedEnd = dur > 0 && s.maxPos >= dur - 15_000 && s.listenedMs > 30_000
        val completed = dur > 0 && (frac >= 0.8f || reachedEnd)
        val skipped = !s.song.isAudiobook && !completed && s.listenedMs < 30_000 && frac < 0.5f
        val event = PlayEventEntity(
            itemKey = s.key,
            videoId = s.song.videoId,
            title = s.song.title,
            artist = s.song.artist,
            album = s.song.album,
            thumb = s.song.albumArtUri,
            audiobook = s.song.isAudiobook,
            startedAt = s.startedAt,
            listenedMs = s.listenedMs,
            durationMs = dur,
            completed = completed,
            skipped = skipped,
            hour = Calendar.getInstance().apply { timeInMillis = s.startedAt }.get(Calendar.HOUR_OF_DAY),
        )
        scope.launch {
            dao.insert(event)
            _saved.value = _saved.value + 1
        }
    }

    /** Save the open listen now and then, so it isn't lost if Android closes the app. */
    private fun persist(s: Session) {
        val f = listOf(
            s.key, s.song.videoId.orEmpty(), s.song.title, s.song.artist, s.song.album, s.song.albumArtUri.orEmpty(),
            s.song.mediaType, s.song.path, s.startedAt, s.durationMs, s.listenedMs, s.maxPos,
        ).joinToString(SEP)
        prefs.edit().putString(KEY_OPEN, f).apply()
    }

    private fun restoreUnfinished() {
        val f = prefs.getString(KEY_OPEN, null)?.split(SEP) ?: return
        prefs.edit().remove(KEY_OPEN).apply()
        if (f.size < 12) return
        runCatching {
            val song = Song(
                title = f[2], artist = f[3], album = f[4], albumArtUri = f[5].ifEmpty { null },
                path = f[7], videoId = f[1].ifEmpty { null }, mediaType = f[6], duration = f[9].toLong(),
            )
            finish(Session(song, f[0], f[8].toLong(), f[9].toLong(), listenedMs = f[10].toLong(), maxPos = f[11].toLong()))
        }
    }

    suspend fun clearHistory() {
        cur = null
        prefs.edit().remove(KEY_OPEN).apply()
        dao.clearHistory()
        _saved.value = _saved.value + 1
    }

    private companion object {
        const val TAG = "ListeningTracker"
        const val KEY_PAUSED = "paused"
        const val KEY_OPEN = "open_session"
        const val SEP = "\u0001"
        const val DAY = 86_400_000L
    }
}
