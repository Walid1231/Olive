package com.waleve.player.data.remote

import android.content.Context
import com.waleve.player.domain.model.SearchResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ─── What the Discover page shows ───────────────────────────────────────────

@Serializable
enum class YtType { SONG, VIDEO, PLAYLIST, ALBUM, ARTIST }

@Serializable
data class YtItem(
    val type: YtType,
    val title: String,
    val subtitle: String = "",
    val thumb: String? = null,
    val videoId: String? = null,
    val browseId: String? = null,
    val duration: String? = null,
    val durationMs: Long = 0,
    /** Long spoken content: played and saved as an audiobook. */
    val audiobook: Boolean = false,
) {
    val playable: Boolean get() = videoId != null
    /** Artist for a song row ("Arijit Singh • Album • 4:05" → "Arijit Singh"). */
    val artist: String get() = subtitle.split(" • ").firstOrNull { it.isNotBlank() && it !in setOf("Song", "Video", "Single", "EP", "Album") } ?: ""
}

@Serializable
data class YtSection(val title: String, val strapline: String? = null, val items: List<YtItem>) {
    /** "Quick picks" style shelves are lists of songs. */
    val isSongList: Boolean get() = items.isNotEmpty() && items.all { it.playable } && items.count { it.type == YtType.SONG } > items.size / 2
}

@Serializable
data class YtChip(val title: String, val params: String?)

@Serializable
data class YtHome(val chips: List<YtChip> = emptyList(), val sections: List<YtSection> = emptyList(), val fetchedAt: Long = 0)

data class YtCollection(val title: String, val subtitle: String, val thumb: String?, val songs: List<YtItem>)

/**
 * Talks to YouTube Music's own web API (the same one music.youtube.com uses) for the
 * Discover page: home shelves, mood chips, playlists / albums / artists, search
 * suggestions and fast song search. Everything is parsed by walking the JSON, so small
 * layout changes on YouTube's side don't break it. The home page is saved to disk and shown
 * instantly next time.
 */
@Singleton
class YouTubeMusicBrowser @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
) {
    private val client = okHttpClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val base = "https://music.youtube.com/youtubei/v1/"
    private val cacheFile get() = File(context.filesDir, "ytm_home_v2.json")

    /** Region for recommendations: the US catalogue = mostly English music. */
    private val gl: String = "US"

    /** YouTube's visitor id: needed for "more shelves" pages; kept so recommendations stay consistent. */
    private val prefs by lazy { context.getSharedPreferences("ytm", Context.MODE_PRIVATE) }
    @Volatile private var visitorData: String? = null
        get() = field ?: prefs.getString("visitor_us", null).also { field = it }

    private fun bodyWith(extra: Map<String, String?>): String = buildJsonObject {
        putJsonObject("context") {
            putJsonObject("client") {
                put("clientName", "WEB_REMIX")
                put("clientVersion", "1.20251110.01.00")
                put("hl", "en")
                put("gl", gl)
                visitorData?.let { put("visitorData", it) }
            }
        }
        extra.forEach { (k, v) -> if (v != null) put(k, v) }
    }.toString()

    private suspend fun post(endpoint: String, body: String, query: String = ""): JsonObject = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$base$endpoint?prettyPrint=false$query")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", "1.20251110.01.00")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("YouTube Music HTTP ${resp.code}")
            val obj = json.parseToJsonElement(resp.body!!.string()) as JsonObject
            obj["responseContext"]["visitorData"].str?.let { v ->
                if (v != visitorData) { visitorData = v; prefs.edit().putString("visitor_us", v).apply() }
            }
            obj
        }
    }

    // ─── JSON helpers ────────────────────────────────────────────────────────
    private operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
    private operator fun JsonElement?.get(i: Int): JsonElement? = (this as? JsonArray)?.getOrNull(i)
    private val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.contentOrNull
    private val JsonElement?.arr: List<JsonElement> get() = (this as? JsonArray).orEmpty()

    /** "runs" → plain text. */
    private fun JsonElement?.text(): String =
        this["runs"].arr.joinToString("") { it["text"].str.orEmpty() }.ifEmpty { this["simpleText"].str.orEmpty() }

    /** Every object stored under [key], anywhere in the tree. */
    private fun findAll(el: JsonElement?, key: String, out: MutableList<JsonObject> = mutableListOf()): List<JsonObject> {
        when (el) {
            is JsonObject -> for ((k, v) in el) { if (k == key && v is JsonObject) out += v else findAll(v, key, out) }
            is JsonArray -> el.forEach { findAll(it, key, out) }
            else -> {}
        }
        return out
    }
    private fun findFirst(el: JsonElement?, key: String): JsonObject? = findAll(el, key).firstOrNull()

    /** First array stored under [key], anywhere in the tree. */
    private fun firstArray(el: JsonElement?, key: String): JsonArray? = when (el) {
        is JsonObject -> (el[key] as? JsonArray)?.takeIf { it.isNotEmpty() } ?: el.values.firstNotNullOfOrNull { firstArray(it, key) }
        is JsonArray -> el.firstNotNullOfOrNull { firstArray(it, key) }
        else -> null
    }

    /** Largest thumbnail, upscaled for crisp cards. */
    private fun thumbOf(el: JsonElement?): String? {
        val list = firstArray(el, "thumbnails")
        val url = list?.lastOrNull()?.get("url").str ?: return null
        return when {
            url.contains("googleusercontent.com") || url.contains("ggpht.com") ->
                url.replace(Regex("=w\\d+-h\\d+"), "=w544-h544").replace(Regex("=s\\d+"), "=s544")
            else -> url
        }.let { if (it.startsWith("//")) "https:$it" else it }
    }

    private fun parseDuration(t: String?): Long {
        if (t.isNullOrBlank() || !t.matches(Regex("\\d+(:\\d{2}){1,2}"))) return 0
        return t.split(":").fold(0L) { acc, p -> acc * 60 + (p.toLongOrNull() ?: 0) } * 1000
    }

    private fun pageType(nav: JsonElement?): String? =
        nav["browseEndpoint"]["browseEndpointContextSupportedConfigs"]["browseEndpointContextMusicConfig"]["pageType"].str

    private fun parseTwoRow(r: JsonObject): YtItem? {
        val title = r["title"].text().ifBlank { return null }
        val nav = r["navigationEndpoint"]
        val subtitle = r["subtitle"].text()
        val thumb = thumbOf(r["thumbnailRenderer"])
        nav["watchEndpoint"]["videoId"].str?.let { vid ->
            val video = nav["watchEndpoint"]["watchEndpointMusicSupportedConfigs"]["watchEndpointMusicConfig"]["musicVideoType"].str
            return YtItem(if (video == "MUSIC_VIDEO_TYPE_ATV") YtType.SONG else YtType.VIDEO, title, subtitle, thumb, videoId = vid)
        }
        val browseId = nav["browseEndpoint"]["browseId"].str ?: return null
        val type = when (pageType(nav)) {
            "MUSIC_PAGE_TYPE_ALBUM", "MUSIC_PAGE_TYPE_AUDIOBOOK" -> YtType.ALBUM
            "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_USER_CHANNEL" -> YtType.ARTIST
            else -> YtType.PLAYLIST
        }
        return YtItem(type, title, subtitle, thumb, browseId = browseId)
    }

    private fun parseListItem(r: JsonObject, fallbackThumb: String? = null): YtItem? {
        val cols = r["flexColumns"].arr.map { it["musicResponsiveListItemFlexColumnRenderer"]["text"] }
        val title = cols.getOrNull(0).text().ifBlank { return null }
        val vid = r["playlistItemData"]["videoId"].str
            ?: cols.getOrNull(0)["runs"][0]["navigationEndpoint"]["watchEndpoint"]["videoId"].str
            ?: r["overlay"].let { findFirst(it, "watchEndpoint")?.get("videoId").str }
        val rest = cols.drop(1).map { it.text() }.filter { it.isNotBlank() }.joinToString(" • ")
        val fixed = r["fixedColumns"].arr.firstOrNull()?.get("musicResponsiveListItemFixedColumnRenderer")?.get("text").text().takeIf { it.isNotBlank() }
        val parts = rest.split(" • ").map { it.trim() }.filter { it.isNotBlank() && it != "Song" && it != "Video" }
        val duration = fixed ?: parts.lastOrNull { it.matches(Regex("\\d+(:\\d{2}){1,2}")) }
        val subtitle = parts.filterNot { it == duration || it.contains(" plays") || it.contains(" views") }.joinToString(" • ")
        val thumb = thumbOf(r["thumbnail"]) ?: fallbackThumb
        if (vid == null) {
            // a playlist / album / artist row (in search results)
            val nav = r["navigationEndpoint"]
            val browseId = nav["browseEndpoint"]["browseId"].str ?: return null
            val type = when (pageType(nav)) {
                "MUSIC_PAGE_TYPE_ALBUM" -> YtType.ALBUM
                "MUSIC_PAGE_TYPE_ARTIST" -> YtType.ARTIST
                else -> YtType.PLAYLIST
            }
            return YtItem(type, title, subtitle, thumb, browseId = browseId)
        }
        return YtItem(YtType.SONG, title, subtitle, thumb, videoId = vid, duration = duration, durationMs = parseDuration(duration))
    }

    private fun parseShelves(root: JsonElement, into: MutableList<YtSection>) {
        findAll(root, "musicCarouselShelfRenderer").forEach { shelf ->
            val header = shelf["header"]["musicCarouselShelfBasicHeaderRenderer"]
            val title = header["title"].text().ifBlank { return@forEach }
            val strap = header["strapline"].text().takeIf { it.isNotBlank() }
            val items = shelf["contents"].arr.mapNotNull { c ->
                (c["musicTwoRowItemRenderer"] as? JsonObject)?.let(::parseTwoRow)
                    ?: (c["musicResponsiveListItemRenderer"] as? JsonObject)?.let { parseListItem(it) }
            }
            if (items.isNotEmpty()) into += YtSection(title, strap, items)
        }
    }

    // ─── Home ────────────────────────────────────────────────────────────────

    /** Last saved home page (instant), or null. */
    fun cachedHome(params: String? = null): YtHome? = runCatching {
        if (params != null) null else json.decodeFromString<YtHome>(cacheFile.readText())
    }.getOrNull()

    /** Fresh home page (optionally for a mood chip). Saves it for next time. */
    suspend fun home(params: String? = null): YtHome = kotlinx.coroutines.coroutineScope {
        if (params == AUDIOBOOKS) {
            return@coroutineScope YtHome(sections = audiobookSections(AUDIOBOOK_SHELVES_ALL), fetchedAt = System.currentTimeMillis())
        }
        // Audiobook shelves load alongside the music ones and go on top
        val books: kotlinx.coroutines.Deferred<List<YtSection>>? =
            if (params == null) async { runCatching { audiobookSections(AUDIOBOOK_SHELVES_HOME) }.getOrDefault(emptyList<YtSection>()) } else null
        val first = post("browse", bodyWith(mapOf("browseId" to "FEmusic_home", "params" to params)))
        val sections = mutableListOf<YtSection>()
        parseShelves(first, sections)
        val chips = findAll(first, "chipCloudChipRenderer").mapNotNull { c ->
            val t = c["text"].text().ifBlank { return@mapNotNull null }
            YtChip(t, c["navigationEndpoint"]["browseEndpoint"]["params"].str)
        }
        // A couple more pages of shelves
        var token = findFirst(first, "nextContinuationData")?.get("continuation").str
        repeat(3) {
            val t = token ?: return@repeat
            val next = runCatching {
                post("browse", bodyWith(mapOf("continuation" to t)), "&ctoken=$t&continuation=$t&type=next")
            }.getOrNull() ?: return@repeat
            parseShelves(next, sections)
            token = findFirst(next, "nextContinuationData")?.get("continuation").str
        }
        // English first: drop Indian-language shelves
        val music = sections.distinctBy { it.title }.filterNot { INDIAN.containsMatchIn(it.title) || INDIAN.containsMatchIn(it.strapline.orEmpty()) }
        val all = books?.await().orEmpty() + music
        val home = YtHome(listOf(YtChip("Audiobooks", AUDIOBOOKS)) + chips.filter { it.title != "Podcasts" }, all, System.currentTimeMillis())
        if (params == null && all.isNotEmpty()) runCatching { cacheFile.writeText(json.encodeToString(home)) }
        home
    }

    // ─── Audiobooks (long YouTube readings) ─────────────────────────────────

    /** Long videos for [query] (≥ 20 min), as audiobook items. */
    suspend fun searchBooks(query: String, minMs: Long = 20 * 60_000L): List<YtItem> {
        val root = post("search", bodyWith(mapOf("query" to query, "params" to "EgWKAQIQAWoMEA4QChADEAQQCRAF")))
        return findAll(root, "musicResponsiveListItemRenderer").mapNotNull { parseListItem(it) }
            .filter { it.playable && it.durationMs >= minMs }
            .distinctBy { it.videoId }
            .map { it.copy(type = YtType.VIDEO, audiobook = true, subtitle = listOfNotNull(it.subtitle.substringBefore(" • ").takeIf { s -> s.isNotBlank() }, it.duration).joinToString(" • ")) }
    }

    private suspend fun audiobookSections(shelves: List<Pair<String, String>>): List<YtSection> = kotlinx.coroutines.coroutineScope {
        shelves.map { (title, q) ->
            async { runCatching { YtSection(title, "AUDIOBOOKS", searchBooks(q)) }.getOrNull() }
        }.awaitAll().filterNotNull().filter { it.items.size >= 3 }
    }

    companion object {
        const val AUDIOBOOKS = "__audiobooks"
        private val INDIAN = Regex("(?i)bollywood|hindi|punjabi|tamil|telugu|desi|indian|bhojpuri|malayalam|kannada|marathi|haryanvi|ghazal|sufi|qawwali|filmi|t-series")
        private val AUDIOBOOK_SHELVES_HOME = listOf(
            "Audiobooks for you" to "full audiobook english",
            "Bangla audio golpo" to "bangla audio golpo full",
            "Sunday Suspense" to "sunday suspense full story",
        )
        private val AUDIOBOOK_SHELVES_ALL = listOf(
            "Popular audiobooks" to "full audiobook english",
            "Classic novels" to "classic novel full audiobook",
            "Mystery & thrillers" to "mystery thriller full audiobook",
            "Sunday Suspense" to "sunday suspense full story",
            "Bangla audio golpo" to "bangla audio golpo full",
            "Bhuter golpo" to "bhuter golpo audio story",
            "Sherlock Holmes" to "sherlock holmes full audiobook",
            "Self-help & mindset" to "self help full audiobook",
            "Sci-fi & fantasy" to "science fiction full audiobook",
            "Bedtime stories" to "bedtime stories for kids audiobook",
        )
    }

    // ─── Radio ("Up next" mix that YouTube builds from one song) ────────────

    /** YouTube Music's own radio for [videoId]: songs people who like it also play. */
    suspend fun radio(videoId: String): List<YtItem> {
        val root = post("next", bodyWith(mapOf("videoId" to videoId, "playlistId" to "RDAMVM$videoId")))
        return findAll(root, "playlistPanelVideoRenderer").mapNotNull { r ->
            val vid = r["videoId"].str ?: return@mapNotNull null
            val title = r["title"].text().ifBlank { return@mapNotNull null }
            val by = r["longBylineText"].text().ifBlank { r["shortBylineText"].text() }
            val dur = r["lengthText"].text().takeIf { it.isNotBlank() }
            val parts = by.split(" • ").map { it.trim() }
                .filter { it.isNotBlank() && !it.matches(Regex("\\d{4}")) && !it.contains(" views") && !it.contains(" likes") }
            YtItem(YtType.SONG, title, parts.take(2).joinToString(" • "), thumbOf(r["thumbnail"]), videoId = vid, duration = dur, durationMs = parseDuration(dur))
        }.distinctBy { it.videoId }.distinctBy { it.title.lowercase() + "|" + it.artist.lowercase() }
    }

    // ─── Playlist / album / artist ──────────────────────────────────────────

    suspend fun collection(browseId: String): YtCollection {
        val root = post("browse", bodyWith(mapOf("browseId" to browseId)))
        val header = listOf("musicResponsiveHeaderRenderer", "musicDetailHeaderRenderer", "musicImmersiveHeaderRenderer", "musicVisualHeaderRenderer", "musicEditablePlaylistDetailHeaderRenderer")
            .firstNotNullOfOrNull { findFirst(root, it) }
        val title = header?.get("title").text()
        val subtitle = listOf(header?.get("straplineTextOne").text(), header?.get("subtitle").text(), header?.get("secondSubtitle").text())
            .filter { it.isNotBlank() }.joinToString(" · ")
        val thumb = thumbOf(header)
        val songs = findAll(root, "musicResponsiveListItemRenderer")
            .mapNotNull { parseListItem(it, thumb) }
            .filter { it.playable }
            .distinctBy { it.videoId }
            .map { if (it.subtitle.isBlank() && subtitle.isNotBlank()) it.copy(subtitle = subtitle.substringBefore(" · ")) else it }
        return YtCollection(title, subtitle, thumb, songs)
    }

    // ─── Direct audio link (fast path for playback) ─────────────────────────

    private val vrUa = "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

    /**
     * Asks YouTube's player for the audio stream the way its Android VR app does: no JavaScript
     * puzzle, usually well under a second. Null if YouTube says no (then yt-dlp takes over).
     */
    suspend fun audioUrl(videoId: String): Pair<String, Long>? = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID_VR"); put("clientVersion", "1.62.27")
                    put("deviceMake", "Oculus"); put("deviceModel", "Quest 3")
                    put("androidSdkVersion", 32); put("osName", "Android"); put("osVersion", "12L")
                    put("hl", "en"); put("gl", gl)
                    visitorData?.let { put("visitorData", it) }
                }
            }
            put("videoId", videoId); put("contentCheckOk", true); put("racyCheckOk", true)
        }.toString()
        val req = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", vrUa)
            .header("X-YouTube-Client-Name", "28")
            .header("X-YouTube-Client-Version", "1.62.27")
            .build()
        val root = runCatching {
            client.newBuilder().callTimeout(6, TimeUnit.SECONDS).build().newCall(req).execute().use { r ->
                if (!r.isSuccessful) null else json.parseToJsonElement(r.body!!.string())
            }
        }.getOrNull() ?: return@withContext null
        if (root["playabilityStatus"]["status"].str != "OK") return@withContext null
        val best = root["streamingData"]["adaptiveFormats"].arr
            .filter { it["mimeType"].str.orEmpty().startsWith("audio/") && it["url"].str != null }
            .maxByOrNull { (it["bitrate"].str?.toLongOrNull()) ?: 0L } ?: return@withContext null
        val url = best["url"].str ?: return@withContext null
        val expire = Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()?.times(1000)
            ?: (System.currentTimeMillis() + 5 * 3_600_000L)
        url to expire
    }

    // ─── Search ──────────────────────────────────────────────────────────────

    suspend fun suggestions(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        val root = post("music/get_search_suggestions", bodyWith(mapOf("input" to query)))
        return findAll(root, "searchSuggestionRenderer").map { it["suggestion"].text() }.filter { it.isNotBlank() }.distinct().take(8)
    }

    /** Songs only (fast). Empty list if YouTube changed something — caller falls back to yt-dlp. */
    suspend fun searchSongs(query: String): List<SearchResult> {
        val root = post("search", bodyWith(mapOf("query" to query, "params" to "EgWKAQIIAWoMEA4QChADEAQQCRAF")))
        return findAll(root, "musicResponsiveListItemRenderer").mapNotNull { parseListItem(it) }
            .filter { it.playable }
            .distinctBy { it.videoId }
            .map { s ->
                val parts = s.subtitle.split(" • ")
                SearchResult(
                    videoId = s.videoId!!, title = s.title, artist = parts.firstOrNull().orEmpty(),
                    album = parts.getOrNull(1), thumbnailUrl = s.thumb, duration = s.duration, durationMs = s.durationMs,
                )
            }
    }
}
