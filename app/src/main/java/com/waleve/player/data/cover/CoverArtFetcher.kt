package com.waleve.player.data.cover

import android.content.Context
import android.net.Uri
import com.waleve.player.data.local.dao.SongDao
import com.waleve.player.data.local.entity.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds cover art for local songs / audiobooks that have none, using the free iTunes Search API.
 * Results are cached per album (or per song when the album is unknown), and only confident
 * matches are used, so a wrong cover is unlikely.
 */
@Singleton
class CoverArtFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val prefs: LibraryPrefs,
) {
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private val cache = context.getSharedPreferences("cover_cache", Context.MODE_PRIVATE)
    private val running = AtomicBoolean(false)
    private val _found = MutableStateFlow(0)
    /** Covers found in the latest run (for a small "x covers added" note). */
    val found: StateFlow<Int> = _found.asStateFlow()

    private fun unknown(s: String?) = s.isNullOrBlank() || s.trim().lowercase() in setOf("<unknown>", "unknown", "unknown artist", "unknown album")

    private fun clean(s: String): String = s
        .replace(Regex("""\.(mp3|m4a|flac|wav|ogg|opus|aac|m4b)$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""[\(\[][^\)\]]*(official|video|audio|lyric|lyrics|hd|4k|remaster|visualizer|full song)[^\)\]]*[\)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\b(ft\.?|feat\.?)\s.*$""", RegexOption.IGNORE_CASE), "")
        .replace('_', ' ')
        .replace(Regex("""\s{2,}"""), " ")
        .trim()

    private fun words(s: String) = clean(s).lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length > 1 }

    private fun groupKey(e: SongEntity): String =
        if (unknown(e.album) || e.album.equals(e.folder, ignoreCase = true)) "song:${e.title}|${e.artist}"
        else "album:${e.album}|${e.artist}"

    private fun hasLocalArt(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        if (uri.startsWith("http")) return true
        return runCatching { context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false }.getOrDefault(false)
    }

    private val bg = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    /** Fire-and-forget version of [run] (after a library scan). */
    fun runInBackground() { bg.launch { runCatching { run() } } }

    /** Look up missing covers. Safe to call often: runs once at a time and caches results. */
    suspend fun run(maxLookups: Int = 60) = withContext(Dispatchers.IO) {
        if (!prefs.autoCovers.value || !running.compareAndSet(false, true)) return@withContext
        var lookups = 0
        var found = 0
        try {
            val groups = songDao.getLocalSongs().groupBy(::groupKey)
            for ((key, group) in groups) {
                val first = group.first()
                if (first.albumArtUri?.startsWith("http") == true) continue
                val cached = cache.getString(key, null)
                if (cached != null) {
                    if (cached.startsWith("http")) { apply(group, cached); continue }
                    val triedAt = cached.substringAfter("none:", "0").toLongOrNull() ?: 0L
                    if (System.currentTimeMillis() - triedAt < 14L * 86_400_000) continue   // retry misses after 2 weeks
                }
                if (hasLocalArt(first.albumArtUri)) continue
                if (lookups >= maxLookups) break
                lookups++
                val url = runCatching { search(first, key.startsWith("album:")) }.getOrNull()
                cache.edit().putString(key, url ?: "none:${System.currentTimeMillis()}").apply()
                if (url != null) { apply(group, url); found++ }
                delay(1_300)   // be polite to the free API
            }
        } finally {
            _found.value = found
            running.set(false)
        }
    }

    private suspend fun apply(group: List<SongEntity>, url: String) {
        group.forEach { songDao.updateAlbumArtUri(it.id, url) }
    }

    private fun search(e: SongEntity, byAlbum: Boolean): String? {
        val book = e.mediaType == SongEntity.MEDIA_AUDIOBOOK
        val artist = if (unknown(e.artist)) "" else clean(e.artist)
        val name = clean(if (byAlbum || book) e.album.takeUnless { unknown(it) } ?: e.title else e.title)
        if (words(name).isEmpty()) return null
        val term = listOf(name, artist).filter { it.isNotBlank() }.joinToString(" ")
        val entity = when { book -> "audiobook"; byAlbum -> "album"; else -> "song" }
        for (country in listOf("US", "IN")) {
            val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", term).addQueryParameter("entity", entity)
                .addQueryParameter("limit", "5").addQueryParameter("country", country).build()
            val body = client.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: continue
            val results = JSONObject(body).optJSONArray("results") ?: continue
            val want = words(name).toSet()
            val wantArtist = words(artist).toSet()
            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                val got = words(r.optString(if (entity == "song") "trackName" else "collectionName")).toSet()
                val gotArtist = words(r.optString("artistName")).toSet()
                val nameOk = want.isNotEmpty() && (want.intersect(got).size * 2 >= minOf(want.size, 4))
                val artistOk = wantArtist.isEmpty() || wantArtist.intersect(gotArtist).isNotEmpty()
                if (nameOk && artistOk) {
                    val art = r.optString("artworkUrl100").takeIf { it.isNotBlank() } ?: continue
                    return art.replace("100x100bb", "600x600bb")
                }
            }
        }
        return null
    }

    /** Forget cached misses (used after the user turns covers back on). */
    fun resetMisses() {
        val e = cache.edit()
        cache.all.forEach { (k, v) -> if ((v as? String)?.startsWith("none:") == true) e.remove(k) }
        e.apply()
    }
}
