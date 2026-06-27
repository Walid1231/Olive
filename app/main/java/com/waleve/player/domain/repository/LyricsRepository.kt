package com.waleve.player.domain.repository

import com.waleve.player.domain.model.Lyrics

interface LyricsRepository {
    suspend fun getLyrics(trackName: String, artistName: String, durationMs: Long): Result<Lyrics>
}
