package com.waleve.player.domain.model

/** A playable link for an online song, plus the headers the server expects. */
data class StreamLink(
    val url: String,
    val headers: Map<String, String>,
    val expiresAt: Long,
    /** Which method found it: visionos, tv_samsung, tv, android_vr, yt-dlp. */
    val via: String,
)

/** One method's try at finding a link (for the Streaming check). */
data class StreamAttempt(val via: String, val ok: Boolean, val ms: Long, val note: String = "")

/** How a song's link was found: which method won and how long it took. */
data class StreamResolution(
    val videoId: String,
    val at: Long,
    val totalMs: Long,
    val winner: String?,
    val attempts: List<StreamAttempt>,
)
