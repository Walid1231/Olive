package com.waleve.player.domain.repository

import com.waleve.player.domain.model.SearchResult
import com.waleve.player.domain.model.StreamAttempt
import com.waleve.player.domain.model.StreamLink
import com.waleve.player.domain.model.StreamResolution

/**
 * Repository interface for searching YouTube Music and fetching stream URLs.
 */
interface StreamRepository {

    /**
     * Search YouTube Music for songs matching the given query.
     */
    suspend fun searchSongs(query: String): List<SearchResult>

    /**
     * Get a direct audio stream URL for the given video ID.
     * Returns the best available audio-only stream URL, or null if unavailable.
     */
    suspend fun getStreamUrl(videoId: String): String?

    /** yt-dlp only (used when the fast link didn't play). */
    suspend fun getStreamUrlSlow(videoId: String): String?

    /**
     * Playable link + headers. Several methods race; the first link that really answers wins.
     * [avoid] skips methods whose link just failed for this song.
     */
    suspend fun getStream(videoId: String, fresh: Boolean = false, avoid: Set<String> = emptySet()): StreamLink?

    /** Streaming check: try every method separately and report each result as it arrives. */
    fun diagnose(videoId: String): kotlinx.coroutines.flow.Flow<StreamAttempt>

    /** The last songs' link look-ups (newest first). */
    fun recentResolutions(): List<StreamResolution>

    /** Last resort: save the audio to a small cache and return the file path. */
    suspend fun downloadForPlayback(videoId: String): String?

    /** Drop a cached link that stopped working. */
    fun forget(videoId: String)

    /** A link that refused to play: forget it and count it against the method that found it. */
    fun blame(videoId: String)

    /** Fetch links for songs the user is likely to tap next, so they start instantly. */
    fun prefetch(videoIds: List<String>)

    /**
     * Get available audio quality options for a video.
     * Returns a list of pairs: (qualityLabel, streamUrl).
     */
    suspend fun getAvailableQualities(videoId: String): List<AudioQuality>
}

/**
 * Represents an audio quality option for streaming/downloading.
 */
data class AudioQuality(
    val label: String,          // e.g. "High (256kbps)"
    val bitrate: Int,           // kbps
    val mimeType: String,       // e.g. "audio/mp4"
    val url: String,            // direct stream URL
    val contentLength: Long,    // bytes (estimated)
)
