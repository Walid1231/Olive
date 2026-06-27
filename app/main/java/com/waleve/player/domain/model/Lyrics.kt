package com.waleve.player.domain.model

data class Lyrics(
    val plainLyrics: String? = null,
    val syncedLyrics: List<LyricLine> = emptyList()
)

data class LyricLine(
    val timestampMs: Long,
    val text: String
)
