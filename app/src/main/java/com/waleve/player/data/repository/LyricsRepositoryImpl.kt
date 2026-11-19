package com.waleve.player.data.repository

import com.waleve.player.domain.model.LyricLine
import com.waleve.player.domain.model.Lyrics
import com.waleve.player.domain.repository.LyricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Lyrics from LRCLIB. YouTube titles are messy ("Maahi Full Music | Kangana Rana", channel
 * "Soundify Beat"), so we clean them, try a few searches and pick the best match — preferring
 * time-synced lyrics whose length matches the song, so they can scroll with the music.
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val client: OkHttpClient
) : LyricsRepository {

    private val cache = object : LinkedHashMap<String, Lyrics>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>?) = size > 60
    }

    override suspend fun getLyrics(
        trackName: String,
        artistName: String,
        durationMs: Long
    ): Result<Lyrics> = withContext(Dispatchers.IO) {
        val key = "$trackName|$artistName|${durationMs / 2000}"
        synchronized(cache) { cache[key] }?.let { return@withContext Result.success(it) }
        try {
            val (title, artist) = cleanUp(trackName, artistName)
            val durSec = (durationMs / 1000).toDouble()
            val seen = HashSet<Long>()
            val candidates = mutableListOf<JSONObject>()
            fun add(arr: JSONArray?) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (seen.add(o.optLong("id", -i.toLong()))) candidates += o
                }
            }

            // 1) title + artist  2) title only  3) everything in one query
            if (artist.isNotBlank()) add(search { addQueryParameter("track_name", title); addQueryParameter("artist_name", artist) })
            if (candidates.none { good(it, durSec, title) }) add(search { addQueryParameter("track_name", title) })
            if (candidates.none { good(it, durSec, title) }) add(search { addQueryParameter("q", "$title $artist".trim()) })

            val best = candidates
                .filter { !it.optBoolean("instrumental", false) }
                .maxByOrNull { score(it, durSec, title, artist) }
                ?.takeIf { score(it, durSec, title, artist) > 0 }
                ?: return@withContext Result.failure(Exception("No lyrics found"))

            val plain = best.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }
            val synced = parseSyncedLyrics(best.optString("syncedLyrics", "").takeIf { it != "null" }.orEmpty())
            if (plain == null && synced.isEmpty()) return@withContext Result.failure(Exception("No lyrics found"))
            val lyrics = Lyrics(plain, synced)
            synchronized(cache) { cache[key] = lyrics }
            Result.success(lyrics)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun search(params: HttpUrl.Builder.() -> Unit): JSONArray? = runCatching {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().apply(params).build()
        val req = Request.Builder().url(url).header("User-Agent", "Olive Music Player (https://github.com/Walid1231/Olive)").build()
        client.newCall(req).execute().use { r -> if (r.isSuccessful) JSONArray(r.body?.string().orEmpty()) else null }
    }.getOrNull()

    /** A candidate that is clearly right: synced, close in length, same title. */
    private fun good(o: JSONObject, durSec: Double, title: String): Boolean =
        o.optString("syncedLyrics", "").let { it.isNotBlank() && it != "null" } &&
            (durSec <= 0 || abs(o.optDouble("duration", 0.0) - durSec) <= 4) &&
            similar(o.optString("trackName"), title) >= 0.6

    private fun score(o: JSONObject, durSec: Double, title: String, artist: String): Double {
        var s = 0.0
        val hasSynced = o.optString("syncedLyrics", "").let { it.isNotBlank() && it != "null" }
        if (hasSynced) s += 3
        val t = similar(o.optString("trackName"), title)
        s += t * 4
        if (artist.isNotBlank()) s += similar(o.optString("artistName"), artist) * 1.5
        if (durSec > 0) {
            val d = abs(o.optDouble("duration", 0.0) - durSec)
            s += when { d <= 2 -> 4.0; d <= 5 -> 3.0; d <= 12 -> 1.0; d <= 30 -> -1.0; else -> -4.0 }
        }
        if (t < 0.3) s -= 6           // a different song
        return s
    }

    /** Word overlap 0..1 between two titles (case / accents / punctuation ignored). */
    private fun similar(a: String, b: String): Double {
        fun words(x: String) = x.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(' ').filter { it.length > 1 }.toSet()
        val wa = words(a); val wb = words(b)
        if (wa.isEmpty() || wb.isEmpty()) return 0.0
        return wa.intersect(wb).size.toDouble() / minOf(wa.size, wb.size)
    }

    /** "Artist - Song (Official Video) | Movie" → ("Song", "Artist"); drops channel noise. */
    private fun cleanUp(rawTitle: String, rawArtist: String): Pair<String, String> {
        var title = rawTitle.replace('_', ' ')
        var artist = rawArtist.takeIf { it.isNotBlank() && it != "<unknown>" }.orEmpty()
        title = title.substringBefore(" | ").substringBefore(" ｜ ")
        val dash = Regex("""\s[-–—]\s""").find(title)
        if (dash != null) {
            val left = title.substring(0, dash.range.first).trim()
            val right = title.substring(dash.range.last + 1).trim()
            if (left.isNotEmpty() && right.isNotEmpty()) { artist = left; title = right }
        }
        val noise = Regex(
            """(?i)\b(official\s*(music\s*)?(video|audio|lyric(al)?s?\s*video)?|full\s*(video|song|music|audio)?\s*(song|video)?|lyrics?\s*video|lyrical|lyrics?|music\s*video|video\s*song|audio\s*song|hd|4k|1080p|720p|remastered(\s*\d{4})?|visualizer|explicit)\b"""
        )
        title = title
            .replace(Regex("""[(\[{][^)\]}]*[)\]}]"""), " ")      // (…) […] {…}
            .replace(Regex("""(?i)\s(ft\.?|feat\.?|featuring)\s.*$"""), " ")
            .replace(noise, " ")
            .replace(Regex("""[|•·~:"“”]+"""), " ")
            .replace(Regex("""\s{2,}"""), " ").trim()
        artist = artist
            .replace(Regex("""(?i)\s*-\s*topic$"""), "")
            .replace(Regex("""(?i)vevo$|\bofficial\b|\brecords?\b|\bmusic\b|\bbeats?\b|\bentertainment\b"""), " ")
            .replace(Regex("""(?i)\s(ft\.?|feat\.?|&|,|x)\s.*$"""), "")
            .replace(Regex("""\s{2,}"""), " ").trim()
        return title.ifBlank { rawTitle } to artist
    }

    /** LRC: "[mm:ss.xx] text", several stamps per line allowed, fraction optional. */
    private fun parseSyncedLyrics(raw: String): List<LyricLine> {
        if (raw.isBlank()) return emptyList()
        val stamp = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
        val lines = mutableListOf<LyricLine>()
        raw.lines().forEach { line ->
            val stamps = stamp.findAll(line).toList()
            if (stamps.isEmpty()) return@forEach
            val text = line.substring(stamps.last().range.last + 1).trim()
            if (text.isBlank()) return@forEach
            stamps.forEach { m ->
                val frac = m.groupValues[3]
                val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                lines += LyricLine(m.groupValues[1].toLong() * 60_000 + m.groupValues[2].toLong() * 1000 + ms, text)
            }
        }
        return lines.sortedBy { it.timestampMs }
    }
}
