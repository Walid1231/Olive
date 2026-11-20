package com.waleve.player.data.repository

import android.content.Context
import com.waleve.player.data.extractor.MediaExtractor
import com.waleve.player.domain.model.SearchResult
import com.waleve.player.domain.repository.AudioQuality
import com.waleve.player.domain.repository.StreamRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.launch
import javax.inject.Singleton

/**
 * Implementation that routes ALL YouTube operations through yt-dlp (via [MediaExtractor]).
 * yt-dlp self-updates to bypass YouTube's constantly-changing anti-bot measures,
 * making it far more reliable than the InnerTube API which breaks frequently.
 */
@Singleton
class StreamRepositoryImpl @Inject constructor(
    private val mediaExtractor: MediaExtractor,
    @ApplicationContext private val context: Context,
    private val ytMusic: com.waleve.player.data.remote.YouTubeMusicBrowser,
) : StreamRepository {

    /**
     * Search YouTube for songs using yt-dlp's `ytsearch` feature.
     * Much more reliable than calling InnerTube API endpoints directly.
     */
    override suspend fun searchSongs(query: String): List<SearchResult> {
        // Fast path: YouTube Music's own search (well under a second). yt-dlp is the safety net.
        runCatching { ytMusic.searchSongs(query) }.getOrNull()?.takeIf { it.size >= 3 }?.let { return it }
        return try {
            val ytResults = mediaExtractor.searchYouTubeMusic(query, maxResults = 20)
            ytResults.map { yt ->
                val durationMs = yt.durationSec * 1000
                val durationText = formatDuration(yt.durationSec)
                SearchResult(
                    videoId      = yt.videoId,
                    title        = yt.title,
                    artist       = yt.artist,
                    album        = null,
                    thumbnailUrl = yt.thumbnailUrl,
                    duration     = durationText,
                    durationMs   = durationMs,
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("StreamRepository", "searchSongs failed for '$query'", e)
            throw e // let SearchViewModel show the error snackbar
        }
    }

    /**
     * Returns the best audio stream URL for the given video ID.
     * Uses yt-dlp via [MediaExtractor] for reliable extraction.
     */
    /** videoId → (url, expires at). Links stay valid for a few hours. */
    private val urlCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()
    private val bg = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val prefetching = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Links last ~6 hours: keep them on disk so songs start instantly after reopening the app
    private val linkPrefs by lazy { context.getSharedPreferences("stream_links", Context.MODE_PRIVATE) }
    init {
        bg.launch {
            val now = System.currentTimeMillis()
            val stale = mutableListOf<String>()
            linkPrefs.all.forEach { (id, v) ->
                val str = v as? String ?: return@forEach
                val exp = str.substringBefore('|').toLongOrNull() ?: 0L
                if (exp - now > 15 * 60_000) urlCache.putIfAbsent(id, str.substringAfter('|') to exp) else stale += id
            }
            if (stale.isNotEmpty()) linkPrefs.edit().apply { stale.forEach { remove(it) } }.apply()
        }
    }

    private fun remember(videoId: String, link: Pair<String, Long>) {
        urlCache[videoId] = link
        linkPrefs.edit().putString(videoId, "${link.second}|${link.first}").apply()
    }

    override fun prefetch(videoIds: List<String>) {
        videoIds.distinct().take(6).forEach { id ->
            val cached = urlCache[id]
            if (cached != null && cached.second - System.currentTimeMillis() > 30 * 60_000) return@forEach
            if (!prefetching.add(id)) return@forEach
            bg.launch {
                try { runCatching { ytMusic.audioUrl(id) }.getOrNull()?.let { remember(id, it) } }
                finally { prefetching.remove(id) }
            }
        }
    }

    override suspend fun getStreamUrl(videoId: String): String? {
        // 1. Already looked up recently
        urlCache[videoId]?.let { (u, exp) -> if (exp - System.currentTimeMillis() > 10 * 60_000) return u }
        // 2. YouTube's own player (fast)
        runCatching { ytMusic.audioUrl(videoId) }.getOrNull()?.let { remember(videoId, it); return it.first }
        // 3. yt-dlp (slower, but handles everything)
        return getStreamUrlSlow(videoId)
    }

    override suspend fun getStreamUrlSlow(videoId: String): String? {
        return try {
            mediaExtractor.extractStreamUrl(videoId)?.also { remember(videoId, it to (System.currentTimeMillis() + 5 * 3_600_000L)) }
        } catch (e: Exception) {
            android.util.Log.e("StreamRepository", "getStreamUrl failed for $videoId", e)
            null
        }
    }

    /**
     * Returns available audio quality options for the given video ID.
     * Delegates to [MediaExtractor.extractAudioQualities] (yt-dlp -J) which returns
     * real stream URLs — far more reliable than the InnerTube player API.
     */
    override fun forget(videoId: String) {
        urlCache.remove(videoId)
        linkPrefs.edit().remove(videoId).apply()
    }

    override suspend fun downloadForPlayback(videoId: String): String? = mediaExtractor.downloadForPlayback(videoId)

    override suspend fun getAvailableQualities(videoId: String): List<AudioQuality> {
        return try {
            mediaExtractor.extractAudioQualities(videoId)
        } catch (e: Exception) {
            android.util.Log.e("StreamRepository", "getAvailableQualities failed for $videoId", e)
            emptyList()
        }
    }

    /**
     * Formats seconds into "M:SS" or "H:MM:SS" human-readable duration.
     */
    private fun formatDuration(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "0:00"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}

