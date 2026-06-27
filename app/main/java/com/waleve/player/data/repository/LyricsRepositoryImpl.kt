package com.waleve.player.data.repository

import com.waleve.player.domain.model.LyricLine
import com.waleve.player.domain.model.Lyrics
import com.waleve.player.domain.repository.LyricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val client: OkHttpClient
) : LyricsRepository {

    override suspend fun getLyrics(
        trackName: String,
        artistName: String,
        durationMs: Long
    ): Result<Lyrics> = withContext(Dispatchers.IO) {
        try {
            // duration is required in seconds for lrclib API, but it's optional. 
            // We'll provide it to get more accurate results if available.
            val durationSec = durationMs / 1000

            val urlBuilder = "https://lrclib.net/api/get".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("track_name", trackName)
                ?.addQueryParameter("artist_name", artistName)
            
            if (durationSec > 0) {
                urlBuilder?.addQueryParameter("duration", durationSec.toString())
            }

            val request = Request.Builder()
                .url(urlBuilder!!.build())
                .header("User-Agent", "WaLEve Player (https://github.com/waleve/player)")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                // If exact match fails, try a fuzzy search as fallback
                return@withContext searchLyricsFallback(trackName, artistName)
            }

            val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
            val json = JSONObject(responseBody)

            val plainLyrics = json.optString("plainLyrics", "").takeIf { it.isNotBlank() }
            val syncedLyricsRaw = json.optString("syncedLyrics", "")

            val syncedLyrics = parseSyncedLyrics(syncedLyricsRaw)

            if (plainLyrics == null && syncedLyrics.isEmpty()) {
                return@withContext Result.failure(Exception("No lyrics found in response"))
            }

            Result.success(Lyrics(plainLyrics, syncedLyrics))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun searchLyricsFallback(trackName: String, artistName: String): Result<Lyrics> {
        try {
            val urlBuilder = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("q", "$trackName $artistName")
            
            val request = Request.Builder()
                .url(urlBuilder!!.build())
                .header("User-Agent", "WaLEve Player")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return Result.failure(Exception("Search fallback failed"))

            val responseBody = response.body?.string() ?: return Result.failure(Exception("Empty body"))
            val jsonArray = org.json.JSONArray(responseBody)
            
            if (jsonArray.length() == 0) return Result.failure(Exception("No lyrics found"))

            // Get the best match (first one)
            val json = jsonArray.getJSONObject(0)
            val plainLyrics = json.optString("plainLyrics", "").takeIf { it.isNotBlank() }
            val syncedLyricsRaw = json.optString("syncedLyrics", "")
            val syncedLyrics = parseSyncedLyrics(syncedLyricsRaw)

            return Result.success(Lyrics(plainLyrics, syncedLyrics))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun parseSyncedLyrics(raw: String): List<LyricLine> {
        if (raw.isBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()
        // LRC format: [mm:ss.xx] text
        val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")
        
        raw.lines().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val msStr = match.groupValues[3]
                val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                val text = match.groupValues[4].trim()
                
                val timestampMs = (min * 60 * 1000) + (sec * 1000) + ms
                if (text.isNotBlank()) {
                    lines.add(LyricLine(timestampMs, text))
                }
            }
        }
        return lines.sortedBy { it.timestampMs }
    }
}
