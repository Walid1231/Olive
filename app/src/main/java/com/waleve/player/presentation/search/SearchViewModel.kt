package com.waleve.player.presentation.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waleve.player.data.download.SongDownloadEvent
import com.waleve.player.data.download.SongDownloads
import com.waleve.player.data.extractor.MediaFormat
import com.waleve.player.domain.model.SearchResult
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.repository.AudioQuality
import com.waleve.player.domain.repository.StreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SearchUiEvent {
    data class Error(val message: String) : SearchUiEvent()
    data class DownloadStarted(val title: String) : SearchUiEvent()
    data class DownloadComplete(val title: String) : SearchUiEvent()
    /** "Not interested" saved: show [message] with Undo (which removes [key]). */
    data class Blocked(val message: String, val key: String) : SearchUiEvent()
}

typealias DownloadProgress = com.waleve.player.data.download.DownloadProgress

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    private val downloads: SongDownloads,
    @ApplicationContext private val context: Context,
    private val ytMusic: com.waleve.player.data.remote.YouTubeMusicBrowser,
    private val forYou: com.waleve.player.data.history.ForYouEngine,
) : ViewModel() {

    // ── Discover (YouTube Music home shelves, shown before typing) ──
    private val _home = MutableStateFlow(ytMusic.cachedHome())
    private val _homeLoading = MutableStateFlow(false)
    val homeLoading: StateFlow<Boolean> = _homeLoading.asStateFlow()
    private val _homeError = MutableStateFlow(false)
    val homeError: StateFlow<Boolean> = _homeError.asStateFlow()
    private val _chip = MutableStateFlow<com.waleve.player.data.remote.YtChip?>(null)
    val chip: StateFlow<com.waleve.player.data.remote.YtChip?> = _chip.asStateFlow()

    /**
     * What Discover shows: your personal shelves on top (For you; book ones on Audiobooks),
     * then YouTube's. Everything marked "Not interested" is filtered out.
     */
    val home: StateFlow<com.waleve.player.data.remote.YtHome?> =
        combine(_home, forYou.sections, forYou.blocks, _chip) { h, personal, blocks, chip ->
            val mine = when {
                chip == null -> personal
                chip.params == com.waleve.player.data.remote.YouTubeMusicBrowser.AUDIOBOOKS ->
                    personal.filter { it.strapline.equals("AUDIOBOOKS", ignoreCase = true) }
                else -> emptyList()
            }
            if (h == null && mine.isEmpty()) null
            else {
                val base = h ?: com.waleve.player.data.remote.YtHome()
                val theirs = base.sections.filterNot { s -> mine.any { it.title == s.title } }
                base.copy(sections = blocks.filter(mine + theirs))
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, _home.value)

    /** Hide [item] / its artist / the [shelf] from recommendations. */
    fun notInterested(item: com.waleve.player.data.remote.YtItem, what: com.waleve.player.data.history.NotInterested, shelf: String) {
        viewModelScope.launch {
            val key = forYou.block(item, what, shelf)
            val msg = when (what) {
                com.waleve.player.data.history.NotInterested.ITEM -> "You won't see “${item.title}” here again"
                com.waleve.player.data.history.NotInterested.ARTIST -> "Won't recommend ${item.artist} anymore"
                com.waleve.player.data.history.NotInterested.SHELF -> "Hid “$shelf”"
            }
            _events.emit(SearchUiEvent.Blocked(msg, key))
        }
    }

    fun undoNotInterested(key: String) {
        viewModelScope.launch { forYou.unblock(key) }
    }
    /** Main home (no chip), so tapping "For you" again is instant. */
    private var mainHome: com.waleve.player.data.remote.YtHome? = _home.value
    private var homeJob: Job? = null

    /** Refresh shelves if they're missing or older than 30 minutes (or [force]). */
    fun loadHome(force: Boolean = false) {
        // Personal shelves: rebuilt when old or after a few new listens
        viewModelScope.launch { forYou.refresh() }
        val fresh = mainHome?.let { System.currentTimeMillis() - it.fetchedAt < 30 * 60_000 } ?: false
        if (!force && fresh && _chip.value == null) return
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _homeLoading.value = true; _homeError.value = false
            runCatching { ytMusic.home(_chip.value?.params) }
                .onSuccess { h ->
                    if (_chip.value == null) mainHome = h
                    // keep the chip row from the main page when a mood page has none
                    _home.value = if (h.chips.isEmpty()) h.copy(chips = mainHome?.chips.orEmpty()) else h
                }
                .onFailure { _homeError.value = _home.value == null }
            _homeLoading.value = false
        }
    }

    fun selectChip(chip: com.waleve.player.data.remote.YtChip?) {
        if (chip == _chip.value) return
        _chip.value = chip
        if (chip == null && mainHome != null) { _home.value = mainHome; homeJob?.cancel(); _homeLoading.value = false; return }
        loadHome(force = true)
    }

    /** Queue for playing a shelf / playlist: YouTube ids are turned into streams by the player. */
    fun songsOf(items: List<com.waleve.player.data.remote.YtItem>): List<Song> = items.filter { it.playable }.map { it.toSong() }

    // ── Suggestions while typing ──
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    /** Run a search right away (suggestion / chip tap). */
    fun searchNow(q: String) {
        _query.value = q
        _suggestions.value = emptyList()
        performSearch(q)
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _qualities = MutableStateFlow<List<AudioQuality>>(emptyList())
    val qualities: StateFlow<List<AudioQuality>> = _qualities.asStateFlow()

    private val _isLoadingQualities = MutableStateFlow(false)
    val isLoadingQualities: StateFlow<Boolean> = _isLoadingQualities.asStateFlow()

    /** Downloads waiting or running (shared by every screen, so progress shows everywhere). */
    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = downloads.active
    /** YouTube ids already saved on the phone: rows show a tick for these. */
    val savedIds: StateFlow<Set<String>> = downloads.saved

    private val _events = MutableSharedFlow<SearchUiEvent>()
    val events: SharedFlow<SearchUiEvent> = _events.asSharedFlow()

    private var searchJob: Job? = null
    private var qualityVideoId: String? = null

    init {
        observeQuery()
        observeSuggestions()
        loadHome()
        viewModelScope.launch {
            downloads.events.collect { e ->
                _events.emit(
                    when (e) {
                        is SongDownloadEvent.Queued -> SearchUiEvent.DownloadStarted(e.title)
                        is SongDownloadEvent.Done -> SearchUiEvent.DownloadComplete(e.title)
                        is SongDownloadEvent.Failed -> SearchUiEvent.Error("Couldn't download ${e.title}")
                    }
                )
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSuggestions() {
        viewModelScope.launch {
            _query.debounce(200).distinctUntilChanged().collect { q ->
                _suggestions.value = if (q.isBlank()) emptyList() else runCatching { ytMusic.suggestions(q) }.getOrDefault(emptyList())
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeQuery() {
        viewModelScope.launch {
            _query
                .debounce(400)
                .distinctUntilChanged()
                .filter { it.length >= 2 }
                .collect { query ->
                    performSearch(query)
                }
        }
    }

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
        if (newQuery.length < 2) {
            _searchResults.value = emptyList()
            if (newQuery.isEmpty()) _suggestions.value = emptyList()
        }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            try {
                val results = streamRepository.searchSongs(query)
                _searchResults.value = results
                // Get links for the top results now, so tapping one starts instantly
                streamRepository.prefetch(results.map { it.videoId }.take(4))
            } catch (e: Exception) {
                val message = when {
                    e is java.net.UnknownHostException || e.message?.contains("Unable to resolve host") == true ->
                        "No internet connection. Please check your network."
                    else -> "Search failed. Please try again."
                }
                _events.emit(SearchUiEvent.Error(message))
            }
            _isSearching.value = false
        }
    }

    /**
     * Called when user taps Play on a search result.
     * Creates a Song from the stream URL and returns it.
     */
    suspend fun getStreamSong(result: SearchResult): Song? {
        return try {
            val streamUrl = streamRepository.getStreamUrl(result.videoId)
            if (streamUrl != null) {
                Song(
                    id = result.videoId.hashCode().toLong(),
                    title = result.title,
                    artist = result.artist,
                    album = result.album ?: "",
                    albumArtUri = result.thumbnailUrl,
                    duration = result.durationMs,
                    path = streamUrl,
                    source = "stream",
                    videoId = result.videoId,   // carry videoId so sharing works
                )
            } else null
        } catch (e: Exception) {
            _events.emit(SearchUiEvent.Error("Failed to start streaming."))
            null
        }
    }

    /**
     * Fetch available audio qualities before downloading.
     */
    fun loadQualities(videoId: String) {
        qualityVideoId = videoId
        _qualities.value = emptyList()
        _isLoadingQualities.value = true
        viewModelScope.launch {
            try {
                val q = streamRepository.getAvailableQualities(videoId)
                _qualities.value = q
            } catch (e: Exception) {
                _events.emit(SearchUiEvent.Error("Failed to load audio qualities."))
            }
            _isLoadingQualities.value = false
        }
    }

    fun clearQualities() {
        _qualities.value = emptyList()
        qualityVideoId = null
    }

    /** Download a song with yt-dlp. It keeps going if you leave this page. */
    fun downloadSong(result: SearchResult, chosenFormat: MediaFormat? = null, asAudiobook: Boolean = false) {
        downloads.start(result, chosenFormat, asAudiobook)
    }
}

/** A YouTube Music song as a queue entry (the player resolves ytm://stream/ ids). */
fun com.waleve.player.data.remote.YtItem.toSong(): Song = Song(
    id = (videoId ?: title).hashCode().toLong(),
    title = title,
    artist = artist,
    album = subtitle.split(" • ").getOrNull(1).orEmpty(),
    albumArtUri = thumb,
    duration = durationMs,
    path = "ytm://stream/$videoId",
    source = "stream",
    videoId = videoId,
    mediaType = if (audiobook) "audiobook" else "music",
).let { if (audiobook) it.copy(album = title, artist = subtitle.substringBefore(" • ")) else it }

fun com.waleve.player.data.remote.YtItem.toSearchResult(): com.waleve.player.domain.model.SearchResult =
    com.waleve.player.domain.model.SearchResult(
        videoId = videoId ?: "", title = title, artist = artist,
        album = subtitle.split(" • ").getOrNull(1), thumbnailUrl = thumb, duration = duration, durationMs = durationMs,
    )

/** Search result → queue entry; the player fetches the stream itself and shows a spinner meanwhile. */
fun com.waleve.player.domain.model.SearchResult.toQueueSong(): Song = Song(
    id = videoId.hashCode().toLong(),
    title = title, artist = artist, album = album ?: "",
    albumArtUri = thumbnailUrl, duration = durationMs,
    path = "ytm://stream/$videoId", source = "stream", videoId = videoId,
    mediaType = if (durationMs >= 40 * 60_000L) "audiobook" else "music",
)
