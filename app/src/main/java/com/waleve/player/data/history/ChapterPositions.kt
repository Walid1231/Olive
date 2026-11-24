package com.waleve.player.data.history

import android.content.Context
import com.waleve.player.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where you stopped in every audiobook chapter (for resume), and which chapters you finished.
 * Same prefs file as before, so existing resume points are kept.
 */
@Singleton
class ChapterPositions @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("audiobook_positions", Context.MODE_PRIVATE)

    /** Bumps on every save, so the library can redraw progress bars. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    /** Online books get a new stream URL every time, so they're remembered by YouTube id. */
    fun key(song: Song): String = song.videoId?.let { "yt:$it" } ?: song.path

    fun position(song: Song): Long = prefs.getLong(key(song), 0L)
    fun savedAt(song: Song): Long = prefs.getLong("t:" + key(song), 0L)

    /** [finished] = reached the last 30 s: start from 0 next time, but count the chapter as done. */
    fun save(song: Song, positionMs: Long, finished: Boolean) {
        val k = key(song)
        prefs.edit()
            .putLong(k, if (finished) 0L else positionMs)
            .putLong("t:$k", System.currentTimeMillis())
            .apply { if (finished) putBoolean("done:$k", true) }
            .apply()
        _version.update { it + 1 }
    }

    /** 0..1 for one chapter: finished = 1, otherwise how far into it you are. */
    fun progress(song: Song): Float {
        val k = key(song)
        if (prefs.getBoolean("done:$k", false)) return 1f
        val pos = prefs.getLong(k, 0L)
        // Saved by older versions (no "done" flag yet): a 0 resume point on a chapter you've played = finished
        if (pos <= 0L) return if (song.playCount > 0 && prefs.contains("t:$k")) 1f else 0f
        if (song.duration <= 0) return 0.05f
        return (pos.toFloat() / song.duration).coerceIn(0.01f, 1f)
    }

    /** 0..1 for a whole book, weighted by chapter length. */
    fun bookProgress(chapters: List<Song>): Float {
        if (chapters.isEmpty()) return 0f
        val total = chapters.sumOf { it.duration.coerceAtLeast(1L) }.toFloat()
        return chapters.sumOf { (progress(it) * it.duration.coerceAtLeast(1L)).toDouble() }.toFloat() / total
    }
}
