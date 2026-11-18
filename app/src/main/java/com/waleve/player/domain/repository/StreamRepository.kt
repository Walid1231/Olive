package com.waleve.player.domain.repository

import com.waleve.player.domain.model.SearchResult

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
