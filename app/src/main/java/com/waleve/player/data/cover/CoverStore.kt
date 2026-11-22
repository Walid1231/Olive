package com.waleve.player.data.cover

import android.content.Context
import android.net.Uri
import android.util.Log
import com.waleve.player.data.local.dao.SongDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the cover of every song / audiobook downloaded from YouTube as an image file on the
 * phone, so it shows offline too (a web link only showed while online), and makes sure the
 * library entry gets it even when Android adds the new file to its media list a bit later.
 */
@Singleton
class CoverStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    okHttpClient: OkHttpClient,
) {
    private val client = okHttpClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
    private val dir get() = File(context.filesDir, "covers").apply { mkdirs() }
    /** savedPath → "coverUri \u0001 videoId \u0001 time", applied after each library scan. */
    private val pending = context.getSharedPreferences("pending_covers", Context.MODE_PRIVATE)

    /** Download the cover for [videoId] (best size first) and return it as a file:// link, or null. */
    suspend fun save(videoId: String?, thumbnailUrl: String?): String? = withContext(Dispatchers.IO) {
        val name = (videoId ?: thumbnailUrl?.hashCode()?.toString() ?: return@withContext null).replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "$name.jpg")
        if (file.length() > 1_000) return@withContext Uri.fromFile(file).toString()
        val candidates = buildList {
            thumbnailUrl?.takeIf { it.startsWith("http") }?.let { add(bigger(it)) }
            if (videoId != null) {
                add("https://i.ytimg.com/vi/$videoId/maxresdefault.jpg")
                add("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
            }
            thumbnailUrl?.takeIf { it.startsWith("http") }?.let { add(it) }
        }.distinct()
        for (url in candidates) {
            val ok = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    val body = resp.body
                    if (!resp.isSuccessful || body == null) return@use false
                    val tmp = File(dir, "$name.tmp")
                    body.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    // YouTube answers a missing size with a tiny grey placeholder
                    if (tmp.length() < 2_000) { tmp.delete(); false } else tmp.renameTo(file)
                }
            }.onFailure { Log.w(TAG, "cover download failed: $url", it) }.getOrDefault(false)
            if (ok) return@withContext Uri.fromFile(file).toString()
        }
        null
    }

    /** Larger version of a YouTube Music cover link ("=w120-h120…" → 544 px). */
    private fun bigger(url: String): String =
        url.replace(Regex("=w\\d+-h\\d+"), "=w544-h544").replace(Regex("=s\\d+"), "=s544")

    /** Note that the file at [savedPath] should get [cover] and [videoId] once it is in the library. */
    fun remember(savedPath: String?, cover: String?, videoId: String?) {
        if (savedPath.isNullOrBlank() || (cover == null && videoId == null)) return
        pending.edit().putString(savedPath, listOf(cover.orEmpty(), videoId.orEmpty(), System.currentTimeMillis()).joinToString(SEP)).apply()
    }

    /** Give waiting covers / video ids to songs that are now in the library. Called after every scan. */
    suspend fun applyPending() = withContext(Dispatchers.IO) {
        val week = System.currentTimeMillis() - 7 * 86_400_000L
        for ((path, value) in pending.all) {
            val parts = (value as? String)?.split(SEP) ?: continue
            val cover = parts.getOrNull(0).orEmpty()
            val videoId = parts.getOrNull(1).orEmpty()
            val at = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            val song = runCatching { songDao.getSongByPath(path) }.getOrNull()
            if (song != null) {
                if (videoId.isNotEmpty() && song.videoId == null) songDao.updateVideoId(song.id, videoId)
                if (cover.isNotEmpty()) songDao.updateAlbumArtUri(song.id, cover)
                pending.edit().remove(path).apply()
            } else if (at < week) {
                pending.edit().remove(path).apply()
            }
        }
    }

    private var backfilled = false

    /** Songs downloaded before covers were kept as files: fetch theirs once (a few per scan). */
    suspend fun backfill(max: Int = 40) = withContext(Dispatchers.IO) {
        if (backfilled) return@withContext
        backfilled = true
        val todo = runCatching { songDao.getLocalSongs() }.getOrDefault(emptyList())
            .filter { it.videoId != null && it.albumArtUri?.startsWith("http") == true }
            .take(max)
        for (s in todo) {
            val cover = save(s.videoId, s.albumArtUri) ?: continue
            songDao.updateAlbumArtUri(s.id, cover)
        }
    }

    private companion object {
        const val TAG = "CoverStore"
        const val SEP = "\u0001"
    }
}
