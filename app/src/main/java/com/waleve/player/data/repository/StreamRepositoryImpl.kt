package com.waleve.player.data.repository

import android.content.Context
import com.waleve.player.data.extractor.MediaExtractor
import com.waleve.player.domain.model.SearchResult
import com.waleve.player.domain.repository.AudioQuality
import com.waleve.player.domain.repository.StreamRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
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
) : StreamRepository {

    /**
     * Search YouTube for songs using yt-dlp's `ytsearch` feature.
     * Much more reliable than calling InnerTube API endpoints directly.
     */
    override suspend fun searchSongs(query: String): List<SearchResult> {
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
    override suspend fun getStreamUrl(videoId: String): String? {
        return try {
            mediaExtractor.extractStreamUrl(videoId)
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

