package com.waleve.player.presentation.library

import androidx.compose.ui.unit.sp
import com.waleve.player.presentation.components.GlassGradientH
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import com.waleve.player.presentation.components.springClick
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import com.waleve.player.presentation.components.Artwork
import com.waleve.player.presentation.components.GlassChip
import com.waleve.player.presentation.components.GlassGradient
import com.waleve.player.presentation.components.GlassIconButton
import com.waleve.player.presentation.components.GlassProgress
import com.waleve.player.presentation.components.GlassSegmented
import com.waleve.player.presentation.components.Overline
import com.waleve.player.presentation.components.PhotoCard
import com.waleve.player.presentation.components.SegmentOption
import com.waleve.player.presentation.components.glass
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassDanger
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassMuted
import com.waleve.player.presentation.theme.GlassSub
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.model.SortOption
import com.waleve.player.domain.model.SortOrder
import com.waleve.player.presentation.components.AlbumCard
import com.waleve.player.presentation.components.ArtistCard
import com.waleve.player.presentation.components.ShareSongBottomSheet
import com.waleve.player.presentation.components.SongListItem
import com.waleve.player.presentation.playlist.AddToPlaylistBottomSheet
import com.waleve.player.presentation.playlist.PlaylistViewModel
import com.waleve.player.presentation.playlist.SharePlaylistViewModel
import com.waleve.player.presentation.theme.LocalGhibliColors

// Human-readable sort option labels
private fun SortOption.label() = when (this) {
    SortOption.DATE_ADDED -> "Date Added"
    SortOption.NAME       -> "Name (A–Z)"
    SortOption.SIZE       -> "File Size"
    SortOption.DURATION   -> "Duration"
    SortOption.ARTIST     -> "Artist"
    SortOption.ALBUM      -> "Album"
}

private fun SortOption.icon() = when (this) {
    SortOption.DATE_ADDED -> "🕐"
    SortOption.NAME       -> "🔤"
    SortOption.SIZE       -> "📦"
    SortOption.DURATION   -> "⏱"
    SortOption.ARTIST     -> "🎤"
    SortOption.ALBUM      -> "💿"
}

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onSongClick: (List<Song>, Int) -> Unit,
    onAlbumClick: (String, String) -> Unit,
    onArtistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onFolderClick: (String) -> Unit,
    onFavoritesClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    isNightMode: Boolean,
    currentPlayingSongId: Long? = null,
    playlistViewModel: PlaylistViewModel? = null,
    modifier: Modifier = Modifier,
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val songs   by viewModel.songs.collectAsStateWithLifecycle()
    val albums  by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val genres  by viewModel.genres.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val audiobookAlbums by viewModel.audiobookAlbums.collectAsStateWithLifecycle()
    val audiobooks by viewModel.audiobooks.collectAsStateWithLifecycle()
    val favoriteCount by viewModel.favoriteCount.collectAsStateWithLifecycle()
    val playlistCount = playlistViewModel?.playlists?.collectAsStateWithLifecycle()?.value?.size ?: 0
    val hiddenCount by viewModel.hiddenCount.collectAsStateWithLifecycle()

    val textPrimary = GlassInk
    val textMuted = GlassMuted
    val accentColor = GlassAccent
    var showSearch by rememberSaveable { mutableStateOf(searchQuery.isNotEmpty()) }

    // Tabs: 0 Songs · 1 Audiobooks · 2 Albums · 3 Artists · 4 Genres · 5 Folders
    val isBooks = selectedTab == 1

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        // ── Header ──
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, modifier = Modifier.weight(1f))
            var settings by remember { mutableStateOf(false) }
            GlassIconButton(Icons.Filled.Tune, { settings = true }, contentDescription = "Library settings")
            if (settings) LibrarySettingsSheet(onDismiss = { settings = false })
            Spacer(Modifier.width(8.dp))
            GlassIconButton(
                if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                onClick = { if (showSearch) viewModel.updateSearchQuery(""); showSearch = !showSearch },
                contentDescription = "Search library",
            )
        }
        if (showSearch) {
            Row(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp).fillMaxWidth()
                    .glass(RoundedCornerShape(24.dp), alpha = 0.75f, elevation = 0.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = GlassSub, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) Text(if (isBooks) "Search audiobooks…" else "Search songs, artists, albums…", color = GlassMuted, fontSize = 14.sp)
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery, onValueChange = viewModel::updateSearchQuery, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = GlassInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(GlassAccent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        // ── Songs | Audiobooks ──
        KindSwitch(
            songs = songs.size, books = audiobooks.size, booksSelected = isBooks,
            onSelect = { viewModel.selectTab(if (it) 1 else 0) },
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))

        // Song-only shortcuts slide away smoothly for audiobooks (no jump)
        androidx.compose.animation.AnimatedVisibility(
            visible = !isBooks,
            enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(260)) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200, delayMillis = 60)),
            exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(240)) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)),
        ) {
          Column {
            // Favorites / Playlists tiles
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LibraryTile(Icons.Filled.Favorite, "Favorites", "$favoriteCount songs", Color(0xFFFFB3C1), onFavoritesClick, Modifier.weight(1f))
                LibraryTile(Icons.AutoMirrored.Filled.QueueMusic, "Playlists", "$playlistCount lists", Color(0xFFB9D7FF), onPlaylistsClick, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            // Sub-filters
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0 to "All", 2 to "Albums", 3 to "Artists", 4 to "Genres", 5 to "Folders").forEach { (i, l) ->
                    GlassChip(l, selectedTab == i, { viewModel.selectTab(i) })
                }
            }
            Spacer(Modifier.height(6.dp))
          }
        }
        if (hiddenCount > 0 && selectedTab == 0) {
            Text(
                "$hiddenCount hidden · Show again",
                color = GlassAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                modifier = Modifier.springClick { viewModel.restoreHidden() }.padding(horizontal = 24.dp, vertical = 2.dp),
            )
        }

        com.waleve.player.presentation.components.StoragePermissionCard(
            onGranted = viewModel::rescan,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )

        // ── Content (cross-fades when switching tabs) ──
        androidx.compose.animation.AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                // old page fades out first, new one fades + slides in right after → no double image / flash
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 90)) +
                    androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(280, delayMillis = 60)) { w -> dir * w / 10 }) togetherWith
                    (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)) +
                        androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(200)) { w -> -dir * w / 14 }) using
                    androidx.compose.animation.SizeTransform(clip = false)
            },
            label = "libraryTab",
            modifier = Modifier.weight(1f),
        ) { tabIndex ->
        when (tabIndex) {
            0 -> SongsTab(songs, onSongClick, viewModel, accentColor, textPrimary, textMuted, currentPlayingSongId, playlistViewModel)
            1 -> AudiobooksTab(audiobookAlbums, audiobooks, onAlbumClick, onSongClick, viewModel, textPrimary, textMuted, currentPlayingSongId)
            2 -> AlbumsTab(albums, onAlbumClick)
            3 -> ArtistsTab(artists, onArtistClick)
            4 -> GenresTab(genres, onGenreClick, textPrimary)
            5 -> FoldersTab(folders, onFolderClick, textPrimary)
        }
        }
    }
}

@Composable
private fun LibraryTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.springClick(onClick = onClick).glass(RoundedCornerShape(20.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = GlassInk.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Text(sub, color = GlassSub, fontSize = 11.5.sp)
        }
    }
}

// ── Shortcut Chip ────────────────────────────────────────────────────────────

@Composable
private fun ShortcutChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bgColor: Color,
    accentColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.springClick(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = textColor)
    }
}

// ── Songs Tab (with Sort + View-mode toggle) ─────────────────────────────────

@Composable
private fun SongsTab(
    songs: List<Song>,
    onSongClick: (List<Song>, Int) -> Unit,
    viewModel: LibraryViewModel,
    accentColor: Color,
    textPrimary: Color,
    textMuted: Color,
    currentPlayingSongId: Long? = null,
    playlistViewModel: PlaylistViewModel? = null,
) {
    var songIdForPlaylist by remember { mutableStateOf<Long?>(null) }
    var songToShare by remember { mutableStateOf<Song?>(null) }
    val shareVm: SharePlaylistViewModel = hiltViewModel()
    val deleteSongs = com.waleve.player.presentation.components.rememberSongDeleter()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Share events
    LaunchedEffect(Unit) {
        shareVm.events.collect { event ->
            when (event) {
                is com.waleve.player.presentation.playlist.ShareUiEvent.Success -> snackbarHostState.showSnackbar(event.message)
                is com.waleve.player.presentation.playlist.ShareUiEvent.Error   -> snackbarHostState.showSnackbar("⚠ ${event.message}")
            }
        }
    }

    // Persists across recompositions but resets on process death (intentional)
    var isGridView by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val currentSortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val currentSortOrder  by viewModel.sortOrder.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Auto-scroll to the currently playing song when entering the list
    LaunchedEffect(currentPlayingSongId, songs) {
        if (currentPlayingSongId != null && songs.isNotEmpty() && !isGridView) {
            val index = songs.indexOfFirst { it.id == currentPlayingSongId }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Toolbar: count + sort + view toggle ──────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Song count + active sort chip
            Column {
                Text(
                    text = "${songs.size} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = textMuted,
                )
                Text(
                    text = "${currentSortOption.icon()} ${currentSortOption.label()} • " +
                        if (currentSortOrder == SortOrder.ASCENDING) "↑ A–Z" else "↓ Z–A",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Medium,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // View toggle button
                IconButton(onClick = { isGridView = !isGridView }) {
                    Icon(
                        imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
                        contentDescription = if (isGridView) "Switch to List" else "Switch to Grid",
                        tint = accentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }

                // Sort button
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            tint = accentColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(LocalGhibliColors.current.surfaceCard),
                    ) {
                        SortOption.entries.forEach { option ->
                            val isActive = currentSortOption == option
                            DropdownMenuItem(
                                leadingIcon = {
                                    Text(option.icon(), style = MaterialTheme.typography.bodyMedium)
                                },
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = option.label(),
                                            color = if (isActive) accentColor else textPrimary,
                                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                        if (isActive) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = if (currentSortOrder == SortOrder.ASCENDING) "↑" else "↓",
                                                color = accentColor,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    if (isActive) {
                                        viewModel.setSortOrder(
                                            if (currentSortOrder == SortOrder.ASCENDING)
                                                SortOrder.DESCENDING
                                            else
                                                SortOrder.ASCENDING,
                                        )
                                    } else {
                                        viewModel.setSortOption(option)
                                        viewModel.setSortOrder(SortOrder.ASCENDING)
                                    }
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── Song content: List or Grid ────────────────────
        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(songs, key = { it.id }) { song ->
                    SongGridCard(
                        song = song,
                        accentColor = accentColor,
                        onClick = { onSongClick(songs, songs.indexOf(song)) },
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                items(songs, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        onClick = { onSongClick(songs, songs.indexOf(song)) },
                        onFavoriteClick = { viewModel.toggleFavorite(song.id) },
                        onAddToPlaylist = { songIdForPlaylist = song.id },
                        onShareClick = { songToShare = song },
                        onDeleteClick = { deleteSongs(listOf(song)) },
                        onToggleAudiobook = { viewModel.toggleAudiobook(song) },
                        isPlaying = song.id == currentPlayingSongId,
                    )
                }
            }
        }

        if (songIdForPlaylist != null && playlistViewModel != null) {
            val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()
            AddToPlaylistBottomSheet(
                playlists = playlists,
                onPlaylistSelected = { playlistId ->
                    playlistViewModel.addSongToPlaylist(playlistId, songIdForPlaylist!!)
                    songIdForPlaylist = null
                },
                onCreatePlaylist = { name, desc ->
                    playlistViewModel.createPlaylist(name, desc)
                },
                onDismiss = { songIdForPlaylist = null },
            )
        }

        // ── Share Song Bottom Sheet ──
        songToShare?.let { song ->
            val friends by shareVm.friends.collectAsStateWithLifecycle()
            val isSharing by shareVm.isSharing.collectAsStateWithLifecycle()
            ShareSongBottomSheet(
                song = song,
                friends = friends,
                isSharing = isSharing,
                uploadProgress = shareVm.uploadProgress.collectAsStateWithLifecycle().value,
                onShare = { uids ->
                    shareVm.shareSong(song, uids)
                },
                onDismiss = { songToShare = null }
            )
        }
    }
}

// ── Song Grid Card ────────────────────────────────────────────────────────────

@Composable
private fun SongGridCard(
    song: Song,
    accentColor: Color,
    onClick: () -> Unit,
) {
    PhotoCard(
        model = song.albumArtUri, title = song.title, subtitle = song.artist,
        seed = song.title.hashCode(), onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(0.9f),
    )
}

// ── Audiobooks Tab ────────────────────────────────────────────────────────────

@Composable
private fun AudiobooksTab(
    books: List<com.waleve.player.domain.model.Album>,
    files: List<Song>,
    onBookClick: (String, String) -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    viewModel: LibraryViewModel,
    textPrimary: Color,
    textMuted: Color,
    currentPlayingSongId: Long?,
) {
    var bookToShare by remember { mutableStateOf<Song?>(null) }
    val deleteSongs = com.waleve.player.presentation.components.rememberSongDeleter()
    var filter by rememberSaveable { mutableStateOf(0) } // 0 all · 1 in progress · 2 not started · 3 finished
    bookToShare?.let { song ->
        val shareVm: SharePlaylistViewModel = hiltViewModel()
        val friends by shareVm.friends.collectAsStateWithLifecycle()
        val isSharing by shareVm.isSharing.collectAsStateWithLifecycle()
        val uploadProgress by shareVm.uploadProgress.collectAsStateWithLifecycle()
        ShareSongBottomSheet(
            song = song, friends = friends, isSharing = isSharing, uploadProgress = uploadProgress,
            onShare = { uids -> shareVm.shareSong(song, uids) },
            onDismiss = { bookToShare = null },
        )
    }
    if (files.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("📚", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            Text("No audiobooks yet", style = MaterialTheme.typography.titleMedium, color = textPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Put files in an \"Audiobooks\" folder, download as Audiobook, or use ⋮ → Move to Audiobooks on any song.",
                style = MaterialTheme.typography.bodySmall, color = textMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        return
    }
    // Per-book chapters + progress (chapters you've played / total)
    val byBook = remember(files) { files.groupBy { it.album to it.artist } }
    fun chaptersOf(b: com.waleve.player.domain.model.Album) = byBook[b.name to b.artist].orEmpty()
    fun progressOf(b: com.waleve.player.domain.model.Album): Float { val ch = chaptersOf(b); return if (ch.isEmpty()) 0f else ch.count { it.playCount > 0 }.toFloat() / ch.size }
    val resume = remember(files) { files.filter { it.lastPlayed != null }.maxByOrNull { it.lastPlayed ?: 0L } }
    val shown = books.filter { b ->
        val p = progressOf(b)
        when (filter) { 1 -> p > 0f && p < 1f; 2 -> p == 0f; 3 -> p >= 1f; else -> true }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        resume?.let { ch ->
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                val book = files.filter { it.album == ch.album && it.artist == ch.artist }
                val idx = book.indexOfFirst { it.id == ch.id }.coerceAtLeast(0)
                val done = if (book.isEmpty()) 0f else book.count { it.playCount > 0 }.toFloat() / book.size
                // Hero "continue" card: the cover fills it, white text on a dark fade
                Box(
                    Modifier.springClick { onSongClick(book, idx) }
                        .fillMaxWidth().height(132.dp)
                        .shadow(12.dp, RoundedCornerShape(26.dp), ambientColor = Color(0x3323305A), spotColor = Color(0x3323305A))
                        .clip(RoundedCornerShape(26.dp)),
                ) {
                    Artwork(ch.albumArtUri, Modifier.fillMaxSize(), corner = 0.dp, seed = ch.album.hashCode())
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.15f)))
                        )
                    )
                    Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(ch.albumArtUri, Modifier.size(84.dp).border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(16.dp)), corner = 16.dp, seed = ch.album.hashCode())
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("CONTINUE LISTENING", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 1.2.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(ch.album.ifBlank { ch.title }, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 6f)))
                            Text("Chapter ${idx + 1} of ${book.size}  ·  ${ch.title}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f))) {
                                Box(Modifier.fillMaxWidth(done.coerceIn(0.03f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(
                            Modifier.size(48.dp).shadow(10.dp, CircleShape, ambientColor = GlassAccent, spotColor = GlassAccent).clip(CircleShape).background(GlassGradient),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.PlayArrow, "Resume", tint = Color.White, modifier = Modifier.size(26.dp)) }
                    }
                }
            }
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "In progress", "Not started", "Finished").forEachIndexed { i, l -> GlassChip(l, filter == i, { filter = i }) }
            }
        }
        items(shown, key = { "book_${it.name}_${it.artist}" }) { book ->
            val chapters = chaptersOf(book)
            val p = progressOf(book)
            var menu by remember { mutableStateOf(false) }
            PhotoCard(
                model = book.artUri,
                title = book.name.ifBlank { "Untitled" },
                subtitle = "${chapters.size} chapter${if (chapters.size == 1) "" else "s"} · ${formatHours(chapters.sumOf { it.duration })}",
                progress = p,
                seed = book.name.hashCode(),
                onClick = { onBookClick(book.name, book.artist) },
                modifier = Modifier.fillMaxWidth().aspectRatio(0.78f),
                topEnd = {
                    Box(
                        Modifier.springClick { menu = true }.padding(8.dp).size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.MoreVert, "Book options", tint = Color.White, modifier = Modifier.size(18.dp)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Color.White) {
                        DropdownMenuItem(text = { Text("Play from start", color = GlassInk) }, leadingIcon = { Icon(Icons.Filled.PlayArrow, null, tint = GlassAccent) },
                            onClick = { menu = false; if (chapters.isNotEmpty()) onSongClick(chapters, 0) })
                        DropdownMenuItem(text = { Text("Share with friend", color = GlassInk) }, leadingIcon = { Icon(Icons.Filled.Share, null, tint = GlassAccent) },
                            onClick = { menu = false; chapters.firstOrNull()?.let { bookToShare = it } })
                        DropdownMenuItem(text = { Text("Move to Songs", color = GlassInk) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = GlassAccent) },
                            onClick = { menu = false; chapters.forEach { viewModel.toggleAudiobook(it) } })
                        DropdownMenuItem(text = { Text("Delete book", color = GlassDanger) }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = GlassDanger) },
                            onClick = { menu = false; deleteSongs(chapters) })
                    }
                },
            )
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text("All chapters", style = MaterialTheme.typography.titleSmall, color = textPrimary, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        }
        items(files, key = { "abfile_${it.id}" }, span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { song ->
            SongListItem(
                song = song,
                onClick = { onSongClick(files, files.indexOf(song)) },
                onFavoriteClick = { viewModel.toggleFavorite(song.id) },
                onAddToPlaylist = {},
                onShareClick = { bookToShare = song },
                onDeleteClick = { deleteSongs(listOf(song)) },
                onToggleAudiobook = { viewModel.toggleAudiobook(song) },
                isPlaying = song.id == currentPlayingSongId,
            )
        }
    }
}

private fun formatHours(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

// ── Albums Tab ────────────────────────────────────────────────────────────────

@Composable
private fun AlbumsTab(
    albums: List<com.waleve.player.domain.model.Album>,
    onAlbumClick: (String, String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(albums, key = { "${it.name}_${it.artist}" }) { album ->
            AlbumCard(album = album, onClick = { onAlbumClick(album.name, album.artist) })
        }
    }
}

// ── Artists Tab ───────────────────────────────────────────────────────────────

@Composable
private fun ArtistsTab(
    artists: List<com.waleve.player.domain.model.Artist>,
    onArtistClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(artists, key = { it.name }) { artist ->
            ArtistCard(artist = artist, onClick = { onArtistClick(artist.name) })
        }
    }
}

// ── Genres Tab ────────────────────────────────────────────────────────────────

@Composable
private fun GenresTab(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    textPrimary: Color,
) {
    val gc = LocalGhibliColors.current
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        items(genres) { genre ->
            Row(
                modifier = Modifier.springClick { onGenreClick(genre) }
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(gc.surfaceCard)
                    
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(gc.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🎵", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = genre,
                    style = MaterialTheme.typography.titleSmall,
                    color = textPrimary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = gc.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ── Folders Tab ───────────────────────────────────────────────────────────────

@Composable
private fun FoldersTab(
    folders: List<String>,
    onFolderClick: (String) -> Unit,
    textPrimary: Color,
) {
    val gc = LocalGhibliColors.current
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        items(folders) { folder ->
            Row(
                modifier = Modifier.springClick { onFolderClick(folder) }
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(gc.surfaceCard)
                    
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(gc.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("📁", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.substringAfterLast('/'),
                        style = MaterialTheme.typography.titleSmall,
                        color = textPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = folder,
                        style = MaterialTheme.typography.bodySmall,
                        color = gc.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = gc.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Songs | Audiobooks in ONE glass box: a gradient pill slides under the chosen side; each side
 * shows an icon bubble, the name and its count together.
 */
@Composable
private fun KindSwitch(songs: Int, books: Int, booksSelected: Boolean, onSelect: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val hf = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), alpha = 0.65f, elevation = 0.dp).padding(5.dp)
    ) {
        val half = maxWidth / 2
        val x by androidx.compose.animation.core.animateDpAsState(
            if (booksSelected) half else 0.dp,
            androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
            label = "kindX",
        )
        Box(
            Modifier.offset(x = x).width(half).height(60.dp)
                .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = GlassAccent, spotColor = GlassAccent)
                .clip(RoundedCornerShape(22.dp)).background(GlassGradientH)
        )
        Row(Modifier.fillMaxWidth().height(60.dp)) {
            listOf(
                Triple(false, Icons.AutoMirrored.Filled.QueueMusic, "Songs" to "$songs ${if (songs == 1) "song" else "songs"}"),
                Triple(true, Icons.AutoMirrored.Filled.MenuBook, "Audiobooks" to "$books ${if (books == 1) "chapter" else "chapters"}"),
            ).forEach { (isBook, icon, text) ->
                val on = isBook == booksSelected
                val fg by androidx.compose.animation.animateColorAsState(if (on) Color.White else GlassInk, androidx.compose.animation.core.tween(220), label = "kindFg")
                val sub by androidx.compose.animation.animateColorAsState(if (on) Color.White.copy(alpha = 0.85f) else GlassSub, androidx.compose.animation.core.tween(220), label = "kindSub")
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null, enabled = !on,
                        ) { hf.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onSelect(isBook) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(if (on) Color.White.copy(alpha = 0.22f) else GlassAccent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, tint = if (on) Color.White else GlassAccent, modifier = Modifier.size(19.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(text.first, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1)
                        Text(text.second, color = sub, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
