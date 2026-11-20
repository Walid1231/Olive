package com.waleve.player.presentation.search

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.waleve.player.data.remote.YouTubeMusicBrowser
import com.waleve.player.data.remote.YtCollection
import com.waleve.player.data.remote.YtHome
import com.waleve.player.data.remote.YtItem
import com.waleve.player.data.remote.YtSection
import com.waleve.player.data.remote.YtType
import com.waleve.player.domain.model.Song
import com.waleve.player.presentation.components.Artwork
import com.waleve.player.presentation.components.GlassButton
import com.waleve.player.presentation.components.GlassChip
import com.waleve.player.presentation.components.GlassIconButton
import com.waleve.player.presentation.components.GradientButton
import com.waleve.player.presentation.components.PhotoCard
import com.waleve.player.presentation.components.glass
import com.waleve.player.presentation.components.springClick
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassMuted
import com.waleve.player.presentation.theme.GlassSub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What Search shows before you type (like Echo / YouTube Music): mood chips and shelves of
 * playlists, albums, artists and "Quick picks" songs. Saved copy shows instantly.
 */
@Composable
fun DiscoverContent(
    home: YtHome?,
    loading: Boolean,
    error: Boolean,
    selectedChip: String?,
    onChip: (com.waleve.player.data.remote.YtChip?) -> Unit,
    onRetry: () -> Unit,
    onPlay: (List<YtItem>, Int) -> Unit,
    onOpen: (YtItem) -> Unit,
    onSearch: (String) -> Unit,
    onDownload: (YtItem) -> Unit = {},
    loadingId: String? = null,
    playingId: String? = null,
    downloads: Map<String, DownloadProgress> = emptyMap(),
    saved: Set<String> = emptySet(),
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // Mood chips
        val chips = home?.chips.orEmpty()
        if (chips.isNotEmpty()) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { GlassChip("For you", selectedChip == null, { onChip(null) }) }
                items(chips.filter { it.params != null }) { c ->
                    val book = c.params == YouTubeMusicBrowser.AUDIOBOOKS
                    GlassChip(
                        if (book) "Audiobooks" else c.title, selectedChip == c.title, { onChip(c) },
                        icon = if (book) Icons.AutoMirrored.Rounded.MenuBook else null,
                    )
                }
            }
        }
        if (loading) item {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = GlassAccent, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(if (home == null) "Loading music for you…" else "Refreshing…", color = GlassSub, fontSize = 12.sp)
            }
        }
        when {
            home == null && loading -> items(3) { SkeletonShelf() }
            home == null && error -> item {
                Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load YouTube Music", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text("Check your internet, or just type to search.", color = GlassSub, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    GlassButton("Try again", onRetry, icon = Icons.Rounded.Refresh)
                    Spacer(Modifier.height(18.dp))
                    QuickSearches(onSearch)
                }
            }
            else -> {
                // Audiobooks banner on the main feed
                val bookChip = chips.firstOrNull { it.params == YouTubeMusicBrowser.AUDIOBOOKS }
                if (selectedChip == null && bookChip != null) item(key = "bookBanner") { AudiobookBanner { onChip(bookChip) } }
                home?.sections.orEmpty().forEach { section ->
                item(key = "sec_" + section.title) { Shelf(section, onPlay, onOpen, onDownload, loadingId, playingId, downloads, saved) }
                }
            }
        }
    }
}

/** Big tappable banner that opens the Audiobooks shelves. */
@Composable
private fun AudiobookBanner(onClick: () -> Unit) {
    val purple = listOf(Color(0xFF8E7CFF), Color(0xFF5B4FD6), Color(0xFF2E8FA8))
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 112.dp)
            .springClick(onClick = onClick)
            .shadow(14.dp, RoundedCornerShape(26.dp), ambientColor = Color(0xFF5B4FD6), spotColor = Color(0xFF5B4FD6))
            .clip(RoundedCornerShape(26.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(purple)),
    ) {
        // soft glows
        Box(Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-30).dp).size(140.dp).clip(CircleShape)
            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomStart).offset(x = (-20).dp, y = 40.dp).size(120.dp).clip(CircleShape)
            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF46C190).copy(alpha = 0.35f), Color.Transparent))))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AUDIOBOOKS", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(2.dp))
                Text("Stories for every mood", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 22.sp, maxLines = 2)
                Spacer(Modifier.height(2.dp))
                Text("Novels, thrillers, Sunday Suspense, bhuter golpo…", color = Color.White.copy(alpha = 0.9f), fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
        }
    }
}

@Composable
private fun QuickSearches(onSearch: (String) -> Unit) {
    val tries = listOf("Arijit Singh", "Bangla Lofi", "Coke Studio Bangla", "Lo-fi", "Bollywood hits", "Ghazal")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tries.take(3).forEach { GlassChip(it, false, { onSearch(it) }) }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tries.drop(3).forEach { GlassChip(it, false, { onSearch(it) }) }
    }
}

@Composable
private fun ShelfHeader(section: YtSection) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        section.strapline?.let { strap ->
            val book = strap.equals("AUDIOBOOKS", ignoreCase = true)
            Row(
                Modifier.padding(bottom = 4.dp).clip(RoundedCornerShape(10.dp))
                    .background(
                        if (book) androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFA08CFF), Color(0xFF6A5ACD)))
                        else androidx.compose.ui.graphics.SolidColor(com.waleve.player.presentation.theme.GlassFill.copy(alpha = 0.75f))
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (book) { Icon(Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)) }
                Text(strap.uppercase(), color = if (book) Color.White else GlassSub, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, letterSpacing = 1.sp, maxLines = 1)
            }
        }
        Text(section.title, color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Shelf(
    section: YtSection, onPlay: (List<YtItem>, Int) -> Unit, onOpen: (YtItem) -> Unit, onDownload: (YtItem) -> Unit,
    loadingId: String?, playingId: String?,
    downloads: Map<String, DownloadProgress>, saved: Set<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShelfHeader(section)
        if (section.isSongList) {
            // Quick picks: columns of 4 song rows, swipe sideways. Rows fill the screen and
            // leave a slice of the next column showing, so there's room for the download button.
            val rows = minOf(4, section.items.size)
            val rowWidth = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 56.dp).coerceIn(280.dp, 380.dp)
            LazyHorizontalGrid(
                rows = GridCells.Fixed(rows),
                modifier = Modifier.fillMaxWidth().height((rows * 64).dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(section.items, key = { i, it -> "${it.videoId}_$i" }) { i, item ->
                    SongRow(
                        item, Modifier.width(rowWidth), state = stateOf(item, loadingId, playingId),
                        trailing = { DownloadButton(downloads[item.videoId], isSaved(item, saved), { onDownload(item) }) },
                    ) { onPlay(section.items, i) }
                }
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(section.items, key = { i, it -> "${it.videoId ?: it.browseId}_$i" }) { i, item ->
                    CardItem(
                        item, onDownload = { onDownload(item) }, state = stateOf(item, loadingId, playingId),
                        progress = downloads[item.videoId], saved = isSaved(item, saved),
                    ) {
                        if (item.playable) onPlay(section.items.filter { it.playable }, section.items.filter { it.playable }.indexOf(item))
                        else onOpen(item)
                    }
                }
            }
        }
    }
}

/** 0 = nothing, 1 = loading, 2 = playing now. */
fun stateOf(item: YtItem, loadingId: String?, playingId: String?): Int = when {
    item.videoId == null -> 0
    item.videoId == loadingId -> 1
    item.videoId == playingId -> 2
    else -> 0
}

fun isSaved(item: YtItem, saved: Set<String>): Boolean = item.videoId?.let { it in saved } == true

/**
 * Download control for a YouTube song: arrow, then a progress ring while it saves, then a
 * cloud tick once it's on the phone. [onCover] is the white-on-dark version for cover images.
 */
@Composable
fun DownloadButton(
    progress: DownloadProgress?,
    saved: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onCover: Boolean = false,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tap: () -> Unit = {
        when {
            progress != null -> Unit
            saved -> android.widget.Toast.makeText(context, "Already downloaded", android.widget.Toast.LENGTH_SHORT).show()
            else -> onClick()
        }
    }
    if (onCover) {
        Box(
            modifier.size(34.dp).clip(CircleShape)
                .background(if (saved && progress == null) GlassAccent else Color.Black.copy(alpha = 0.45f))
                .springClick(onClick = tap),
            contentAlignment = Alignment.Center,
        ) { DownloadGlyph(progress, saved, idle = Color.White, done = Color.White, size = 18.dp) }
    } else {
        Box(modifier.size(40.dp).clip(CircleShape).springClick(onClick = tap), contentAlignment = Alignment.Center) {
            DownloadGlyph(progress, saved, idle = GlassMuted, done = GlassAccent, size = 22.dp)
        }
    }
}

@Composable
private fun DownloadGlyph(progress: DownloadProgress?, saved: Boolean, idle: Color, done: Color, size: Dp) {
    when {
        progress != null -> {
            val p = progress.progress / 100f
            if (p in 0.01f..0.98f) CircularProgressIndicator(
                progress = { p }, modifier = Modifier.size(size), color = done, strokeWidth = 2.dp, trackColor = idle.copy(alpha = 0.25f),
            ) else CircularProgressIndicator(Modifier.size(size), color = done, strokeWidth = 2.dp)
        }
        saved -> Icon(Icons.Rounded.CloudDone, "Downloaded", tint = done, modifier = Modifier.size(size))
        else -> Icon(Icons.Rounded.CloudDownload, "Download", tint = idle, modifier = Modifier.size(size))
    }
}

/** Dim layer over a cover: a spinner while it loads, bouncing bars while it plays. */
@Composable
private fun PlayStateOverlay(state: Int, corner: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    if (state == 0) return
    Box(modifier.clip(RoundedCornerShape(corner)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        if (state == 1) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        else Bars()
    }
}

@Composable
private fun Bars() {
    val t = rememberInfiniteTransition(label = "np")
    Row(Modifier.height(16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(380, 280, 460).forEachIndexed { i, ms ->
            val h by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(ms, delayMillis = i * 70), RepeatMode.Reverse), label = "b$i")
            Box(Modifier.width(3.dp).fillMaxHeight(h).clip(RoundedCornerShape(1.dp)).background(Color.White))
        }
    }
}

@Composable
fun SongRow(item: YtItem, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null, state: Int = 0, onClick: () -> Unit) {
    Row(modifier.springClick(onClick = onClick).height(60.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Artwork(item.thumb, Modifier.size(52.dp), corner = 10.dp, seed = item.title.hashCode())
            PlayStateOverlay(state, 10.dp, Modifier.size(52.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = if (state == 0) GlassInk else GlassAccent, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.subtitle.takeIf { it.isNotBlank() }, item.duration).joinToString(" • "),
                color = GlassSub, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun CardItem(
    item: YtItem, onDownload: () -> Unit = {}, state: Int = 0,
    progress: DownloadProgress? = null, saved: Boolean = false, onClick: () -> Unit,
) {
    val artist = item.type == YtType.ARTIST
    val wide = item.type == YtType.VIDEO
    val w = if (wide) 240.dp else 150.dp
    Column(Modifier.width(w).springClick(onClick = onClick)) {
        Box {
            Artwork(
                item.thumb,
                Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 9f else 1f).then(if (artist) Modifier.clip(CircleShape) else Modifier),
                corner = if (artist) 999.dp else 16.dp, seed = item.title.hashCode(),
            )
            if (item.playable) Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp).clip(CircleShape)
                    .background(if (state != 0) GlassAccent else Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                when (state) {
                    1 -> CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    2 -> Bars()
                    else -> Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            if (item.playable) DownloadButton(progress, saved, onDownload, Modifier.align(Alignment.BottomStart).padding(8.dp), onCover = true)
            if (item.audiobook) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFA08CFF), Color(0xFF6A5ACD))))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Audiobook", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title, color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = if (artist) Modifier.fillMaxWidth() else Modifier,
            textAlign = if (artist) androidx.compose.ui.text.style.TextAlign.Center else null,
        )
        if (item.subtitle.isNotBlank()) Text(
            item.subtitle, color = GlassSub, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = if (artist) Modifier.fillMaxWidth() else Modifier,
            textAlign = if (artist) androidx.compose.ui.text.style.TextAlign.Center else null,
        )
    }
}

@Composable
private fun SkeletonShelf() {
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.35f, 0.75f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "ska")
    Column(Modifier.graphicsLayer { alpha = a }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(horizontal = 20.dp).width(160.dp).height(18.dp).clip(RoundedCornerShape(6.dp)).background(GlassInk.copy(alpha = 0.08f)))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(3) { Box(Modifier.size(150.dp).clip(RoundedCornerShape(16.dp)).background(GlassInk.copy(alpha = 0.08f))) }
        }
    }
}

// ─── Playlist / album / artist page ─────────────────────────────────────────

@HiltViewModel
class YtCollectionViewModel @Inject constructor(private val browser: YouTubeMusicBrowser) : ViewModel() {
    private val _state = MutableStateFlow<YtCollection?>(null)
    val state = _state.asStateFlow()
    private val _error = MutableStateFlow(false)
    val error = _error.asStateFlow()
    private var loaded: String? = null

    fun load(browseId: String, force: Boolean = false) {
        if (loaded == browseId && !force) return
        loaded = browseId
        viewModelScope.launch {
            _error.value = false
            runCatching { browser.collection(browseId) }
                .onSuccess { _state.value = it }
                .onFailure { _error.value = true }
        }
    }
}

@Composable
fun YtCollectionScreen(
    browseId: String,
    title: String,
    thumb: String?,
    onBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    vm: YtCollectionViewModel = hiltViewModel(),
    searchVm: SearchViewModel = hiltViewModel(),
) {
    LaunchedEffect(browseId) { vm.load(browseId) }
    val c by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val downloads by searchVm.activeDownloads.collectAsStateWithLifecycle()
    val saved by searchVm.savedIds.collectAsStateWithLifecycle()
    val playerVm: com.waleve.player.presentation.player.PlayerViewModel = hiltViewModel()
    val ps by playerVm.playerState.collectAsStateWithLifecycle()
    val loadingId = ps.loadingSong?.videoId ?: (if (ps.isBuffering) ps.currentSong?.videoId else null)
    val playingId = if (ps.isPlaying && ps.loadingSong == null) ps.currentSong?.videoId else null
    var downloadFor by remember { mutableStateOf<YtItem?>(null) }
    val songs = c?.songs.orEmpty()

    fun play(start: Int, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val q = searchVm.songsOf(songs)
        if (shuffle) onSongClick(q.shuffled(), 0) else onSongClick(q, start)
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Back")
                Spacer(Modifier.width(10.dp))
                Text("YouTube Music", color = GlassSub, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        item {
            PhotoCard(
                model = c?.thumb ?: thumb,
                title = c?.title?.ifBlank { null } ?: title,
                subtitle = listOfNotNull(c?.subtitle?.takeIf { it.isNotBlank() }, if (songs.isNotEmpty()) "${songs.size} songs" else null).joinToString(" · "),
                seed = title.hashCode(), corner = 28.dp,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.15f),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton("Play", { play(0) }, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow, enabled = songs.isNotEmpty())
                GlassButton("Shuffle", { play(0, true) }, Modifier.weight(1f), icon = Icons.Rounded.Shuffle, enabled = songs.isNotEmpty())
            }
        }
        when {
            c == null && !error -> item {
                Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = GlassAccent) }
            }
            c == null && error -> item {
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), alpha = 0.6f).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't open this", color = GlassInk, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    GlassButton("Try again", { vm.load(browseId, force = true) }, icon = Icons.Rounded.Refresh)
                }
            }
            songs.isEmpty() -> item { Text("No songs here.", color = GlassSub) }
            else -> itemsIndexed(songs, key = { i, s -> "${s.videoId}_$i" }) { i, s ->
                SongRow(
                    s, Modifier.fillMaxWidth(), state = stateOf(s, loadingId, playingId),
                    trailing = { DownloadButton(downloads[s.videoId], isSaved(s, saved), { downloadFor = s }) },
                ) { play(i) }
            }
        }
    }

    downloadFor?.let { s ->
        com.waleve.player.presentation.components.DownloadFormatSheet(
            title = s.title,
            durationSec = s.durationMs / 1000,
            audiobook = s.audiobook,
            onDismiss = { downloadFor = null },
            onPick = { format, asAudiobook ->
                searchVm.downloadSong(s.toSearchResult(), format, asAudiobook)
                downloadFor = null
            },
        )
    }
}
