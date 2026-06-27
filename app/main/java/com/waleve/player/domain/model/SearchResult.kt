package com.waleve.player.domain.model

/**
 * Represents a single song result from an InnerTube search.
 */
data class SearchResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val thumbnailUrl: String? = null,
    val duration: String? = null,       // human-friendly e.g. "3:45"
    val durationMs: Long = 0,           // milliseconds
)
