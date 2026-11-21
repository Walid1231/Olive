package com.waleve.player.data.history

import android.content.Context
import android.util.Log
import com.waleve.player.data.remote.YouTubeMusicBrowser
import com.waleve.player.data.remote.YtItem
import com.waleve.player.data.remote.YtSection
import com.waleve.player.data.remote.YtType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/** Lower-case name without "- Topic", "VEVO" and punctuation, so "Arijit Singh - Topic" = "arijit singh". */
fun normName(s: String): String = s.lowercase()
    .replace(" - topic", "").replace("vevo", "").replace("official", "")
    .replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

/** What the user said "Not interested" to. */
data class Blocks(
    val items: Set<String> = emptySet(),
    val artists: Set<String> = emptySet(),
    val shelves: Set<String> = emptySet(),
    /** Artists with two or more items marked "Not interested": shown less (left out of your shelves). */
    val softArtists: Set<String> = emptySet(),
) {
    fun hides(item: YtItem): Boolean =
        (item.videoId != null && item.videoId in items) || (item.artist.isNotBlank() && normName(item.artist) in artists)

    fun hidesShelf(title: String): Boolean = normName(title) in shelves

    /** Remove hidden shelves and items; drop shelves left nearly empty. */
    fun filter(sections: List<YtSection>): List<YtSection> =
        if (items.isEmpty() && artists.isEmpty() && shelves.isEmpty()) sections
        else sections.filterNot { hidesShelf(it.title) }
            .map { s -> s.copy(items = s.items.filterNot(::hides)) }
            .filter { it.items.size >= 2 }
}

enum class NotInterested { ITEM, ARTIST, SHELF }

/**
 * Builds the personal shelves at the top of For you from your listening history
 * ([ListeningTracker]): a mix from your most-played songs, "Because you listened to …",
 * more from your top artist, audiobooks like the ones you listen to, and what to continue.
 * Song ideas come from YouTube Music's own radio for those songs, so they feel like YouTube's.
 * Anything marked "Not interested" is left out everywhere on the page.
 */
@Singleton
class ForYouEngine @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: ListeningDao,
    private val yt: YouTubeMusicBrowser,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val cacheFile = File(context.filesDir, "for_you_v1.json")
    private val mutex = Mutex()

    @Serializable
    private data class Cache(val sections: List<YtSection>, val builtAt: Long, val eventCount: Int)

    private var cache: Cache? = runCatching { json.decodeFromString<Cache>(cacheFile.readText()) }.getOrNull()

    private val _sections = MutableStateFlow(cache?.sections.orEmpty())
    /** Personal shelves (unfiltered; apply [blocks] before showing). */
    val sections: StateFlow<List<YtSection>> = _sections.asStateFlow()

    val blocks: StateFlow<Blocks> = dao.blocked().map { rows ->
        val items = rows.filter { it.blockKey.startsWith("item:") }
        Blocks(
            items = items.map { it.blockKey.removePrefix("item:") }.toSet(),
            artists = rows.filter { it.blockKey.startsWith("artist:") }.map { it.blockKey.removePrefix("artist:") }.toSet(),
            shelves = rows.filter { it.blockKey.startsWith("shelf:") }.map { it.blockKey.removePrefix("shelf:") }.toSet(),
            softArtists = items.mapNotNull { it.label.substringAfter(SEP, "").takeIf { a -> a.isNotBlank() } }
                .groupingBy { it }.eachCount().filterValues { it >= 2 }.keys,
        )
    }.stateIn(scope, SharingStarted.Eagerly, Blocks())

    /** Rebuild when it's old or you've listened to a few new things since. */
    suspend fun refresh(force: Boolean = false) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val count = runCatching { dao.count() }.getOrDefault(0)
            val c = cache
            val stale = c == null || System.currentTimeMillis() - c.builtAt > 3 * HOUR || count - c.eventCount >= 3 || count < c.eventCount
            if (!force && !stale) return@withContext
            val built = runCatching { build() }.onFailure { Log.w(TAG, "build failed", it) }.getOrNull() ?: return@withContext
            val fresh = Cache(built, System.currentTimeMillis(), count)
            cache = fresh
            _sections.value = built
            runCatching { cacheFile.writeText(json.encodeToString(fresh)) }
            Unit
        }
    }

    /** Mark [item] (or its artist / author, or the [shelf] it is in) as not interesting. Returns the key, for Undo. */
    suspend fun block(item: YtItem, what: NotInterested, shelf: String): String {
        val entry = when (what) {
            NotInterested.ITEM -> NotInterestedEntity("item:${item.videoId ?: item.title}", item.title + SEP + normName(item.artist), now())
            NotInterested.ARTIST -> NotInterestedEntity("artist:${normName(item.artist)}", item.artist, now())
            NotInterested.SHELF -> NotInterestedEntity("shelf:${normName(shelf)}", shelf, now())
        }
        dao.block(entry)
        return entry.blockKey
    }

    suspend fun unblock(key: String) = dao.unblock(key)

    suspend fun clearBlocked() = dao.clearBlocked()

    suspend fun clearAll() {
        cache = null
        _sections.value = emptyList()
        runCatching { cacheFile.delete() }
    }

    // ─── Building the shelves ───────────────────────────────────────────────

    private fun now() = System.currentTimeMillis()

    private suspend fun build(): List<YtSection> = coroutineScope {
        val now = now()
        val events = dao.since(now - 120 * DAY)
        if (events.isEmpty()) return@coroutineScope emptyList()
        val b = blocks.value
        val songs = events.filter { !it.audiobook }
        val books = events.filter { it.audiobook }

        fun decay(e: PlayEventEntity, halfLifeDays: Double) = 0.5.pow((now - e.startedAt) / DAY.toDouble() / halfLifeDays)
        fun weight(e: PlayEventEntity) = when {
            e.completed -> 1.0
            e.skipped -> -0.7
            else -> 0.3 + 0.7 * min(1.0, if (e.durationMs > 0) e.listenedMs.toDouble() / e.durationMs else 0.0)
        }

        // ── Songs: what you play, finish, skip ──
        val latest = songs.distinctBy { it.itemKey }   // newest first
        val itemScore = songs.groupBy { it.itemKey }.mapValues { (_, l) -> l.sumOf { weight(it) * decay(it, 21.0) } }
        val artistScore = songs.groupBy { normName(it.artist) }.mapValues { (_, l) -> l.sumOf { weight(it) * decay(it, 30.0) } }
        val badArtists = artistScore.filterValues { it < -1.0 }.keys + b.softArtists + b.artists
        val disliked = songs.groupBy { it.videoId }.filter { (id, l) -> id != null && l.count { it.skipped } >= 2 && l.none { it.completed } }.keys
        fun wanted(item: YtItem) = !b.hides(item) && item.videoId !in disliked && normName(item.artist) !in badArtists

        val seeds = itemScore.filterValues { it > 0.5 }.entries.sortedByDescending { it.value }
            .mapNotNull { (k, _) -> latest.first { it.itemKey == k }.takeIf { it.videoId != null && it.videoId !in b.items } }
            .take(3)
        val recent = songs.firstOrNull { it.completed && it.videoId != null && it.videoId !in b.items && seeds.none { s -> s.itemKey == it.itemKey } }
            ?: songs.firstOrNull { it.completed && it.videoId != null && it.videoId !in b.items }
        val topArtistKey = artistScore.filterKeys { it.isNotBlank() && it !in badArtists }.maxByOrNull { it.value }?.takeIf { it.value >= 1.5 }?.key
        val topArtist = topArtistKey?.let { k -> songs.first { normName(it.artist) == k }.artist }

        // ── Audiobooks: time spent per book, the authors / channels and topics ──
        val bookTime = books.groupBy { it.itemKey }.mapValues { (_, l) -> l.sumOf { it.listenedMs / 60_000.0 * decay(it, 45.0) } }
        val topBooks = bookTime.entries.sortedByDescending { it.value }.map { (k, _) -> books.first { it.itemKey == k } }.take(5)
        val finishedBooks = books.filter { it.completed }.map { it.itemKey }.toSet()
        val inProgress = books.distinctBy { it.itemKey }
            .filter { it.videoId != null && it.itemKey !in finishedBooks && now - it.startedAt < 30 * DAY && it.videoId !in b.items }
            .take(8)
        val author = topBooks.map { it.artist }.filter { it.isNotBlank() && normName(it) !in badArtists }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val keywords = keywordsOf(topBooks)
        val bookQueries = buildList {
            if (keywords.isNotEmpty()) add(keywords.take(2).joinToString(" ") + " full audiobook")
            topBooks.firstOrNull()?.let { tokens(it.title).take(4).joinToString(" ").takeIf { q -> q.isNotBlank() }?.let { q -> add("$q audiobook") } }
        }.distinct()

        // ── Ask YouTube (all at once) ──
        val mixD = async { seeds.map { s -> async { safe { yt.radio(s.videoId!!) } } }.awaitAll() }
        val becauseD = recent?.let { r -> async { safe { yt.radio(r.videoId!!) } } }
        val artistD = topArtist?.let { a ->
            async {
                safe {
                    yt.searchSongs(a).map { r ->
                        YtItem(YtType.SONG, r.title, listOfNotNull(r.artist, r.album).filter { it.isNotBlank() }.joinToString(" • "),
                            r.thumbnailUrl, videoId = r.videoId, duration = r.duration, durationMs = r.durationMs)
                    }
                }
            }
        }
        val booksD = if (bookQueries.isNotEmpty()) async { bookQueries.map { q -> async { safe { yt.searchBooks(q) } } }.awaitAll().flatten() } else null
        val authorD = author?.let { a -> async { safe { yt.searchBooks("$a audiobook") } } }

        val out = mutableListOf<YtSection>()

        // Continue listening: books you haven't finished
        val cont = inProgress.map { it.toItem() }.filter(::wanted)
        if (cont.isNotEmpty()) out += YtSection("Continue listening", BOOKS, cont)

        // Your mix: YouTube's radio of your top songs, woven together
        val radios = mixD.await().map { l -> l.filter(::wanted) }
        val mix = weave(radios).distinctBy { it.videoId }.take(25)
        if (mix.size >= 5) out += YtSection("Your mix", "Made from what you play", mix)

        // Because you listened to …
        val because = becauseD?.await().orEmpty().filter(::wanted).filter { i -> mix.none { it.videoId == i.videoId } }.take(20)
        if (recent != null && because.size >= 5) out += YtSection(recent.title, "Because you listened to", because)

        // Audiobooks like yours
        val bookIds = books.mapNotNull { it.videoId }.toSet()
        val bookPicks = booksD?.await().orEmpty().filter { wanted(it) && it.videoId !in bookIds }.distinctBy { it.videoId }.take(15)
        val bookShelf = if (bookPicks.size >= 3) YtSection("Audiobooks for you", BOOKS, bookPicks) else null
        val byAuthor = authorD?.await().orEmpty()
            .filter { wanted(it) && it.videoId !in bookIds && bookPicks.none { p -> p.videoId == it.videoId } }
            .distinctBy { it.videoId }.take(15)
        val authorShelf = if (author != null && byAuthor.size >= 3) YtSection("More from $author", BOOKS, byAuthor) else null

        // Listen again: your favourites lately
        val again = itemScore.filterValues { it > 0.8 }.entries.sortedByDescending { it.value }
            .mapNotNull { (k, _) -> latest.first { it.itemKey == k }.takeIf { it.videoId != null } }
            .map { it.toItem() }.filter(::wanted).take(12)
        val againShelf = if (again.size >= 4) YtSection("Listen again", null, again) else null

        val fromArtist = artistD?.await().orEmpty()
            .filter { wanted(it) && normName(it.artist).contains(topArtistKey.orEmpty()) }
            .distinctBy { it.videoId }.take(15)
        val artistShelf = if (topArtist != null && fromArtist.size >= 4) YtSection("More from $topArtist", null, fromArtist) else null

        // At the hours you usually listen to books, books come first
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        fun nearNow(e: PlayEventEntity) = abs(e.hour - hour).let { min(it, 24 - it) } <= 2
        val bookTimeNow = books.filter(::nearNow).sumOf { it.listenedMs }
        val songTimeNow = songs.filter(::nearNow).sumOf { it.listenedMs }
        val bookFirst = bookTimeNow > songTimeNow
        val bookShelves = listOfNotNull(bookShelf, authorShelf)
        if (bookFirst) out.addAll(minOf(1, out.size), bookShelves) else out += bookShelves
        out += listOfNotNull(againShelf, artistShelf)
        out
    }

    private suspend fun <T> safe(block: suspend () -> List<T>): List<T> =
        runCatching { block() }.onFailure { Log.w(TAG, "fetch failed", it) }.getOrDefault(emptyList())

    /** A, B, C, A, B, C… so no one song's radio takes over the mix. */
    private fun weave(lists: List<List<YtItem>>): List<YtItem> {
        val out = mutableListOf<YtItem>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
        return out
    }

    private fun PlayEventEntity.toItem() = YtItem(
        type = if (audiobook) YtType.VIDEO else YtType.SONG,
        title = title,
        subtitle = if (audiobook) artist else listOf(artist, album).filter { it.isNotBlank() }.joinToString(" • "),
        thumb = thumb,
        videoId = videoId,
        duration = durationText(durationMs),
        durationMs = durationMs,
        audiobook = audiobook,
    )

    private fun durationText(ms: Long): String? {
        if (ms <= 0) return null
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    private fun tokens(title: String): List<String> =
        title.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 3 && it !in STOP }

    /** Words that keep coming back in the titles of the books you listen to most. */
    private fun keywordsOf(books: List<PlayEventEntity>): List<String> {
        val score = mutableMapOf<String, Double>()
        books.forEachIndexed { i, b -> tokens(b.title).distinct().forEach { score[it] = (score[it] ?: 0.0) + 1.0 / (i + 1) } }
        return score.entries.sortedByDescending { it.value }.map { it.key }.take(3)
    }

    private companion object {
        const val TAG = "ForYouEngine"
        const val BOOKS = "AUDIOBOOKS"
        const val SEP = "\u0001"
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
        val STOP = setOf(
            "the", "and", "for", "with", "from", "this", "that", "full", "audiobook", "audiobooks", "audio", "book", "books",
            "part", "chapter", "episode", "unabridged", "abridged", "narrated", "complete", "free", "english", "story", "stories",
            "novel", "read", "reading", "listen", "best", "new", "official", "video", "hindi", "volume", "vol", "series",
        )
    }
}
