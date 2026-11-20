package com.waleve.player.data.repository

import android.content.Context
import com.waleve.player.data.extractor.MediaExtractor
import com.waleve.player.domain.model.SearchResult
import com.waleve.player.domain.repository.AudioQuality
import com.waleve.player.domain.repository.StreamRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
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
    private val resolver: com.waleve.player.data.remote.StreamResolver,
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
    /** videoId → link (+ headers). Links stay valid for a few hours. */
    private val urlCache = java.util.concurrent.ConcurrentHashMap<String, com.waleve.player.domain.model.StreamLink>()
    private val bg = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val prefetching = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    /** One look-up per song at a time (taps + prefetch + the player share it). */
    private val inFlight = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<com.waleve.player.domain.model.StreamLink?>>()

    // Links last ~6 hours: kept on disk so songs start instantly after reopening the app.
    // Stored as  expires \u0001 via \u0001 headers-json \u0001 url
    private val linkPrefs by lazy { context.getSharedPreferences("stream_links_v2", Context.MODE_PRIVATE) }
    init {
        bg.launch {
            val now = System.currentTimeMillis()
            val stale = mutableListOf<String>()
            linkPrefs.all.forEach { (id, v) ->
                val parts = (v as? String)?.split('\u0001') ?: return@forEach
                val exp = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                if (parts.size == 4 && exp - now > 15 * 60_000) {
                    val h = runCatching {
                        val o = org.json.JSONObject(parts[2]); o.keys().asSequence().associateWith { o.optString(it) }
                    }.getOrDefault(emptyMap())
                    urlCache.putIfAbsent(id, com.waleve.player.domain.model.StreamLink(parts[3], h, exp, parts[1]))
                } else stale += id
            }
            if (stale.isNotEmpty()) linkPrefs.edit().apply { stale.forEach { remove(it) } }.apply()
            // the old store (links without headers) is no longer used
            runCatching { context.deleteSharedPreferences("stream_links") }
        }
        // Get YouTube's player code ready now, so the first song doesn't wait for it
        bg.launch { resolver.warmUp() }
    }

    private fun remember(videoId: String, link: com.waleve.player.domain.model.StreamLink) {
        urlCache[videoId] = link
        val h = org.json.JSONObject(link.headers).toString()
        linkPrefs.edit().putString(videoId, listOf(link.expiresAt.toString(), link.via, h, link.url).joinToString("\u0001")).apply()
    }

    private fun cached(videoId: String, minLeftMs: Long = 10 * 60_000): com.waleve.player.domain.model.StreamLink? =
        urlCache[videoId]?.takeIf { it.expiresAt - System.currentTimeMillis() > minLeftMs }

    override suspend fun getStream(videoId: String, fresh: Boolean, avoid: Set<String>): com.waleve.player.domain.model.StreamLink? {
        if (!fresh) {
            cached(videoId)?.let { return it }
            // A look-up for this song is already running (e.g. a prefetch): use its answer
            inFlight[videoId]?.takeIf { it.isActive && avoid.isEmpty() }?.let { shared ->
                shared.await()?.let { return it }
            }
        } else forget(videoId)
        val job = bg.async {
            try {
                resolver.resolve(videoId, allowSlow = true, avoid = avoid)?.also { remember(videoId, it) }
            } finally { inFlight.remove(videoId) }
        }
        if (avoid.isEmpty()) inFlight[videoId] = job
        return job.await()
    }

    override fun prefetch(videoIds: List<String>) {
        videoIds.distinct().take(4).forEach { id ->
            if (cached(id, 30 * 60_000) != null) return@forEach
            if (inFlight[id]?.isActive == true || !prefetching.add(id)) return@forEach
            val job = bg.async {
                try {
                    // fast methods only — no heavy yt-dlp in the background
                    resolver.resolve(id, allowSlow = false)?.also { remember(id, it) }
                } finally { prefetching.remove(id); inFlight.remove(id) }
            }
            inFlight[id] = job
        }
    }

    override suspend fun getStreamUrl(videoId: String): String? = getStream(videoId)?.url

    override suspend fun getStreamUrlSlow(videoId: String): String? = getStream(videoId, fresh = true)?.url

    override fun diagnose(videoId: String) = resolver.diagnose(videoId)

    override fun recentResolutions() = resolver.recent()

    /**
     * Returns available audio quality options for the given video ID.
     * Delegates to [MediaExtractor.extractAudioQualities] (yt-dlp -J) which returns
     * real stream URLs — far more reliable than the InnerTube player API.
     */
    override fun forget(videoId: String) {
        urlCache.remove(videoId)
        linkPrefs.edit().remove(videoId).apply()
    }

    /** A link that refused to play: forget it and count it against the method that found it. */
    override fun blame(videoId: String) {
        urlCache[videoId]?.via?.let(resolver::blame)
        forget(videoId)
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

