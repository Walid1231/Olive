package com.waleve.player.data.remote

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.waleve.player.data.extractor.MediaExtractor
import com.waleve.player.domain.model.StreamAttempt
import com.waleve.player.domain.model.StreamLink
import com.waleve.player.domain.model.StreamResolution
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds a playable link for a YouTube song FAST.
 *
 * YouTube now wants a "PO token" for the Android VR client Olive used before, so those links
 * failed and every song fell back to yt-dlp (20–40 s on a phone). Now several methods run
 * at the same time and the first link that really answers wins:
 *  • visionos   — Apple Vision Pro client: direct links, no token, no JavaScript (what
 *                 yt-dlp itself uses when it can't run JavaScript)
 *  • android_vr — kept in case it still works for some songs
 *  • yt-dlp     — joins after 2.5 s only if nothing worked yet (or right away if the fast
 *                 ones have been failing on this network); it keeps a cache so it gets faster
 * Each link is checked with a tiny 1 KB request before it's used.
 */
@Singleton
class StreamResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
    private val extractor: MediaExtractor,
) {
    private val api = okHttpClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).callTimeout(7, TimeUnit.SECONDS)
        .build()
    private val probe = okHttpClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json".toMediaType()
    private val stats = context.getSharedPreferences("stream_stats", Context.MODE_PRIVATE)

    private class Client(
        val id: String,
        val name: String,
        val version: String,
        val nameId: Int,
        val ua: String,
        val extra: Map<String, Any> = emptyMap(),
        val needsJs: Boolean = false,
    ) {
        /** Headers YouTube's video servers expect for this client's links. */
        val playHeaders: Map<String, String>
            get() = if (needsJs) mapOf("User-Agent" to ua, "Origin" to "https://www.youtube.com", "Referer" to "https://www.youtube.com/")
            else mapOf("User-Agent" to ua)
    }

    // Client details as used by yt-dlp (Nov 2026)
    private val visionos = Client(
        "visionos", "VISIONOS", "1.02", 101,
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
        mapOf("deviceMake" to "Apple", "deviceModel" to "RealityDevice17,1", "osName" to "visionOS", "osVersion" to "26.5.23O471"),
    )
    private val androidVr = Client(
        "android_vr", "ANDROID_VR", "1.65.10", 28,
        "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        mapOf("deviceMake" to "Oculus", "deviceModel" to "Quest 3", "androidSdkVersion" to 32, "osName" to "Android", "osVersion" to "12L"),
    )
    private val clients = listOf(visionos, androidVr)

    /** Headers for links we handed out (so links stored in songs still play with the right headers). */
    private val headersByUrl = object : LinkedHashMap<String, Map<String, String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, String>>?) = size > 200
    }

    fun headersFor(url: String): Map<String, String>? = synchronized(headersByUrl) { headersByUrl[url] }

    private val history = ArrayDeque<StreamResolution>()
    fun recent(): List<StreamResolution> = synchronized(history) { history.toList() }

    @Volatile private var warmed = false

    /** Open the connection to YouTube in the background, so the first song skips the handshake. */
    suspend fun warmUp() = withContext(Dispatchers.IO) {
        if (warmed) return@withContext
        warmed = true
        runCatching {
            api.newCall(Request.Builder().url("https://www.youtube.com/generate_204").head().build()).execute().close()
        }
    }

    private val visitorData: String?
        get() = context.getSharedPreferences("ytm", Context.MODE_PRIVATE).getString("visitor_us", null)

    // ── One method ────────────────────────────────────────────────────────────

    private fun player(c: Client, videoId: String): JSONObject {
        val client = JSONObject().put("clientName", c.name).put("clientVersion", c.version).put("hl", "en").put("gl", "US")
        c.extra.forEach { (k, v) -> client.put(k, v) }
        visitorData?.let { client.put("visitorData", it) }
        val body = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("videoId", videoId).put("contentCheckOk", true).put("racyCheckOk", true)
        val req = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(body.toString().toRequestBody(jsonType))
            .header("User-Agent", c.ua)
            .header("X-YouTube-Client-Name", c.nameId.toString())
            .header("X-YouTube-Client-Version", c.version)
            .apply {
                if (c.needsJs) { header("Origin", "https://www.youtube.com"); header("Referer", "https://www.youtube.com/") }
                visitorData?.let { header("X-Goog-Visitor-Id", it) }
            }
            .build()
        api.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return JSONObject(r.body?.string().orEmpty())
        }
    }

    /** Direct links only (these clients don't need YouTube's player code). */
    private fun buildUrl(f: JSONObject): String? = f.optString("url").takeIf { it.isNotBlank() }

    /** Does the link really give audio? (asks for the first 1 KB) */
    private fun works(url: String, headers: Map<String, String>): Boolean {
        val b = Request.Builder().url(url).header("Range", "bytes=0-1023")
        headers.forEach { (k, v) -> b.header(k, v) }
        return runCatching { probe.newCall(b.build()).execute().use { it.code == 206 || it.code == 200 } }.getOrDefault(false)
    }

    private fun viaClient(c: Client, videoId: String): StreamLink {
        val j = player(c, videoId)
        val ps = j.optJSONObject("playabilityStatus")
        val status = ps?.optString("status").orEmpty()
        if (status != "OK") throw IOException(listOf(status, ps?.optString("reason")).filter { !it.isNullOrBlank() }.joinToString(": ").ifBlank { "not playable" })
        val sd = j.optJSONObject("streamingData") ?: throw IOException("no streams")
        val formats = sd.optJSONArray("adaptiveFormats") ?: throw IOException("no streams")
        val audio = (0 until formats.length()).map { formats.getJSONObject(it) }
            .filter { it.optString("mimeType").startsWith("audio/") }
            // normal (not "volume-levelled") audio first, then the best quality
            .sortedWith(compareBy<JSONObject> { if (it.optBoolean("isDrc")) 1 else 0 }.thenByDescending { it.optLong("bitrate") })
        if (audio.isEmpty()) throw IOException("no audio")
        val headers = c.playHeaders
        for (f in audio.take(2)) {
            val url = buildUrl(f) ?: continue
            if (works(url, headers)) {
                val expire = Uri.parse(url).getQueryParameter("expire")?.toLongOrNull()?.times(1000)
                    ?: (System.currentTimeMillis() + 5 * 3_600_000L)
                return StreamLink(url, headers, expire, c.id)
            }
        }
        throw IOException("link refused (403)")
    }

    private suspend fun viaYtDlp(videoId: String): StreamLink {
        val skipVisionos = stats.getInt("fails_visionos", 0) >= 2
        val (url, h) = extractor.extractStreamLink(videoId, skipVisionos) ?: throw IOException("yt-dlp found nothing")
        val headers = h.filterKeys { it.equals("User-Agent", true) || it.equals("Origin", true) || it.equals("Referer", true) }
        val expire = Uri.parse(url).getQueryParameter("expire")?.toLongOrNull()?.times(1000)
            ?: (System.currentTimeMillis() + 5 * 3_600_000L)
        return StreamLink(url, headers, expire, YTDLP)
    }

    private suspend fun attempt(method: String, videoId: String): StreamLink =
        if (method == YTDLP) viaYtDlp(videoId)
        else kotlinx.coroutines.runInterruptible(Dispatchers.IO) { viaClient(clients.first { it.id == method }, videoId) }

    // ── The race ──────────────────────────────────────────────────────────────

    /** A method that failed many times in a row sits out for a while (fewer wasted requests). */
    private fun benched(method: String): Boolean {
        val fails = stats.getInt("fails_$method", 0)
        val last = stats.getLong("lastfail_$method", 0)
        return fails >= 4 && System.currentTimeMillis() - last < 20 * 60_000
    }

    private fun record(method: String, ok: Boolean, ms: Long) {
        stats.edit().apply {
            if (ok) { putInt("fails_$method", 0); putLong("lastok_$method", System.currentTimeMillis()); putLong("ms_$method", ms) }
            else { putInt("fails_$method", stats.getInt("fails_$method", 0) + 1); putLong("lastfail_$method", System.currentTimeMillis()) }
        }.apply()
    }

    /** True when the fast methods haven't been working lately → start yt-dlp at once. */
    private fun fastOnesFailing(): Boolean {
        val recent = recent().take(3)
        return recent.size >= 2 && recent.all { it.winner == null || it.winner == YTDLP }
    }

    /** The method blamed for a link that later failed to play. */
    fun blame(method: String) = record(method, false, 0)

    suspend fun resolve(videoId: String, allowSlow: Boolean = true, avoid: Set<String> = emptySet()): StreamLink? = coroutineScope {
        val started = SystemClock.elapsedRealtime()
        val methods = buildList {
            clients.map { it.id }.filter { it !in avoid && !benched(it) }.forEach(::add)
            if (allowSlow) add(YTDLP)
        }.ifEmpty { clients.map { it.id } }
        val attempts = java.util.Collections.synchronizedList(mutableListOf<StreamAttempt>())
        val results = Channel<StreamLink?>(Channel.UNLIMITED)
        val slowDelay = if (fastOnesFailing() || YTDLP in avoid) 0L else 2_500L
        val jobs = methods.map { m ->
            launch {
                if (m == YTDLP && slowDelay > 0) delay(slowDelay)
                val t0 = SystemClock.elapsedRealtime()
                val link = try {
                    attempt(m, videoId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    val ms = SystemClock.elapsedRealtime() - t0
                    attempts += StreamAttempt(m, false, ms, (e.message ?: e.javaClass.simpleName).take(120))
                    record(m, false, ms)
                    null
                }
                if (link != null) {
                    val ms = SystemClock.elapsedRealtime() - t0
                    attempts += StreamAttempt(m, true, ms)
                    record(m, true, ms)
                }
                results.send(link)
            }
        }
        var winner: StreamLink? = null
        withTimeoutOrNull(90_000) {
            repeat(methods.size) {
                if (winner == null) results.receive()?.let { winner = it }
            }
        }
        jobs.forEach { it.cancel() }
        val total = SystemClock.elapsedRealtime() - started
        winner?.let { w -> synchronized(headersByUrl) { headersByUrl[w.url] = w.headers } }
        synchronized(history) {
            history.addFirst(StreamResolution(videoId, System.currentTimeMillis(), total, winner?.via, attempts.toList()))
            while (history.size > 20) history.removeLast()
        }
        Log.i(TAG, "$videoId → ${winner?.via ?: "nothing"} in ${total}ms ${attempts.joinToString { "${it.via}:${if (it.ok) "ok" else it.note}" }}")
        winner
    }

    /** Streaming check: every method on its own, results as they come in. */
    fun diagnose(videoId: String): Flow<StreamAttempt> = channelFlow {
        (clients.map { it.id } + YTDLP).forEach { m ->
            launch {
                val t0 = SystemClock.elapsedRealtime()
                val r = runCatching { attempt(m, videoId) }
                val ms = SystemClock.elapsedRealtime() - t0
                send(StreamAttempt(m, r.isSuccess, ms, r.exceptionOrNull()?.let { (it.message ?: it.javaClass.simpleName).take(120) }.orEmpty()))
            }
        }
    }

    companion object {
        private const val TAG = "StreamResolver"
        const val YTDLP = "yt-dlp"
        private const val WARM_VIDEO = "jNQXAC9IVRw"
    }
}
