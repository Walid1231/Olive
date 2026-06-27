package com.waleve.player.presentation.library

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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
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

    val tabs = listOf("Songs", "Albums", "Artists", "Genres", "Folders")
    val gc   = LocalGhibliColors.current

    // Animated colors reacting to mode changes
    val bgColor by animateColorAsState(
        targetValue = gc.surface, animationSpec = tween(1200), label = "libraryBg",
    )
    val accentColor by animateColorAsState(
        targetValue = gc.accent, animationSpec = tween(800), label = "accent",
    )
    val textPrimary by animateColorAsState(
        targetValue = gc.textPrimary, animationSpec = tween(800), label = "textPrimary",
    )
    val textMuted by animateColorAsState(
        targetValue = gc.textMuted, animationSpec = tween(800), label = "textMuted",
    )
    val cardBg by animateColorAsState(
        targetValue = gc.surfaceCard, animationSpec = tween(800), label = "cardBg",
    )

    Column(
        modifier = modifier.fillMaxSize().background(bgColor),
    ) {
        // ── Search Bar ────────────────────────────────────
        androidx.compose.material3.OutlinedTextField(
            value = searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            placeholder = { androidx.compose.material3.Text("Search library...", color = textMuted) },
            leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Search, null, tint = textMuted) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .background(cardBg, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = textMuted.copy(alpha = 0.5f),
                cursorColor = accentColor,
                focusedTextColor = textPrimary,
                unfocusedTextColor = textPrimary,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedLabelColor = accentColor,
                unfocusedLabelColor = textMuted,
                focusedPlaceholderColor = textMuted,
                unfocusedPlaceholderColor = textMuted,
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            singleLine = true,
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = textPrimary),
        )

        // ── Shortcut chips ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShortcutChip(
                icon  = Icons.Filled.Favorite,
                label = "Favorites",
                bgColor = cardBg,
                accentColor = accentColor,
                textColor = textPrimary,
                onClick = onFavoritesClick,
                modifier = Modifier.weight(1f),
            )
            ShortcutChip(
                icon  = Icons.AutoMirrored.Filled.QueueMusic,
                label = "Playlists",
                bgColor = cardBg,
                accentColor = accentColor,
                textColor = textPrimary,
                onClick = onPlaylistsClick,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Tab row ───────────────────────────────────────
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor   = Color.Transparent,
            contentColor     = accentColor,
            edgePadding      = 16.dp,
            divider          = {},
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick  = { viewModel.selectTab(index) },
                    text = {
                        Text(
                            text  = title,
                            color = if (selectedTab == index) accentColor else textMuted,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                )
            }
        }

        // ── Content ───────────────────────────────────────
        when (selectedTab) {
            0 -> SongsTab(songs, onSongClick, viewModel, accentColor, textPrimary, textMuted, currentPlayingSongId, playlistViewModel)
            1 -> AlbumsTab(albums, onAlbumClick)
            2 -> ArtistsTab(artists, onArtistClick)
            3 -> GenresTab(genres, onGenreClick, textPrimary)
            4 -> FoldersTab(folders, onFolderClick, textPrimary)
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
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
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
                        onDeleteClick = { viewModel.deleteSong(song.id) },
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
                onShare = { uids ->
                    shareVm.shareSong(song, uids)
                    songToShare = null
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
    val gc = LocalGhibliColors.current
    val cardBg by animateColorAsState(
        targetValue = gc.surfaceCard, animationSpec = tween(800), label = "gridCardBg",
    )
    val textCol by animateColorAsState(
        targetValue = gc.textPrimary, animationSpec = tween(800), label = "gridCardText",
    )
    val subCol by animateColorAsState(
        targetValue = gc.textMuted, animationSpec = tween(800), label = "gridCardSub",
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = song.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(accentColor.copy(alpha = 0.15f)),
            contentScale = ContentScale.Crop,
        )
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.labelMedium,
                color = textCol,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    velocity = 40.dp,
                ),
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.labelSmall,
                color = subCol,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Albums Tab ────────────────────────────────────────────────────────────────

@Composable
private fun AlbumsTab(
    albums: List<com.waleve.player.domain.model.Album>,
    onAlbumClick: (String, String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(gc.surfaceCard)
                    .clickable { onGenreClick(genre) }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(gc.surfaceCard)
                    .clickable { onFolderClick(folder) }
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
