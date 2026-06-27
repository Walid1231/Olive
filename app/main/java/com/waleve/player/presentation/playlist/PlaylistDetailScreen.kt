package com.waleve.player.presentation.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waleve.player.domain.model.Friend
import com.waleve.player.domain.model.Song
import com.waleve.player.presentation.components.ShareSongBottomSheet
import com.waleve.player.presentation.components.SongListItem
import com.waleve.player.presentation.theme.LocalGhibliColors
import com.waleve.player.presentation.theme.ErrorRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    viewModel: PlaylistViewModel,
    onBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
) {
    LaunchedEffect(playlistId) { viewModel.selectPlaylist(playlistId) }
    val playlist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val gc = LocalGhibliColors.current

    // Share ViewModel (scoped to this composable)
    val shareVm: SharePlaylistViewModel = hiltViewModel()
    val friends by shareVm.friends.collectAsStateWithLifecycle()
    val isSharing by shareVm.isSharing.collectAsStateWithLifecycle()

    var showSongSelection by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var songToShare by remember { mutableStateOf<Song?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Collect share events
    LaunchedEffect(Unit) {
        shareVm.events.collect { event ->
            when (event) {
                is ShareUiEvent.Success -> snackbarHostState.showSnackbar(event.message)
                is ShareUiEvent.Error   -> snackbarHostState.showSnackbar("⚠ ${event.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist", color = gc.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = gc.textPrimary)
                    }
                },
                actions = {
                    // Share button
                    IconButton(onClick = { showShareSheet = true }) {
                        Icon(Icons.Rounded.Share, "Share playlist", tint = gc.accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = gc.surface),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSongSelection = true },
                containerColor = gc.accent,
                contentColor = gc.surface,
            ) {
                Icon(Icons.Rounded.Add, "Add songs")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = gc.surface,
    ) { paddingValues ->
        val songs = playlist?.songs ?: emptyList()
        LazyColumn(
            modifier = Modifier.padding(paddingValues).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    onClick = { onSongClick(songs, songs.indexOf(song)) },
                    onFavoriteClick = { },
                    onAddToPlaylist = { },
                    onShareClick = { songToShare = song },
                )
            }
        }
    }

    // ── Song Selection Bottom Sheet ──────────────────────────────────────────
    if (showSongSelection) {
        val existingSongIds = (playlist?.songs ?: emptyList()).map { it.id }.toSet()
        SongSelectionBottomSheet(
            allSongs = allSongs,
            existingSongIds = existingSongIds,
            onAddSongs = { songIds ->
                songIds.forEach { songId -> viewModel.addSongToPlaylist(playlistId, songId) }
                showSongSelection = false
                scope.launch { snackbarHostState.showSnackbar("${songIds.size} song${if (songIds.size > 1) "s" else ""} added!") }
            },
            onDismiss = { showSongSelection = false },
        )
    }

    // ── Share Playlist Bottom Sheet ──────────────────────────────────────────
    if (showShareSheet) {
        val songs = playlist?.songs ?: emptyList()
        val localOnlyCount = songs.count { it.source != "stream" || !it.path.startsWith("http") }
        SharePlaylistBottomSheet(
            playlistName        = playlist?.name ?: "Playlist",
            playlistDescription = playlist?.description,
            songs               = songs,
            friends             = friends,
            localOnlyCount      = localOnlyCount,
            isSharing           = isSharing,
            onShare             = { selectedUids ->
                shareVm.sharePlaylist(
                    playlistName        = playlist?.name ?: "Playlist",
                    playlistDescription = playlist?.description,
                    songs               = songs,
                    selectedFriendUids  = selectedUids,
                )
                showShareSheet = false
            },
            onDismiss = { showShareSheet = false },
        )
    }

    // ── Share Song Bottom Sheet ──
    songToShare?.let { song ->
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

// ─── Share Playlist Bottom Sheet ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePlaylistBottomSheet(
    playlistName: String,
    playlistDescription: String?,
    songs: List<Song>,
    friends: List<Friend>,
    localOnlyCount: Int,
    isSharing: Boolean,
    onShare: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val gc = LocalGhibliColors.current
    val selectedUids = remember { mutableStateListOf<String>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = gc.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Group, null, tint = gc.accent)
                Text(
                    "Share \"$playlistName\"",
                    style = MaterialTheme.typography.titleMedium,
                    color = gc.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Warning for local songs
            if (localOnlyCount > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ErrorRed.copy(alpha = 0.08f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("⚠", fontSize = 14.sp)
                        Text(
                            "$localOnlyCount local-only track${if (localOnlyCount > 1) "s" else ""} will be visible but not downloadable for friends.",
                            color = ErrorRed,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Text(
                "Select friends to share with:",
                color = gc.textSecondary,
                fontSize = 13.sp,
            )

            if (friends.isEmpty()) {
                Text(
                    "No friends yet. Go to the Friends tab to add some!",
                    color = gc.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    friends.forEach { friend ->
                        val isSelected = friend.uid in selectedUids
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) selectedUids.remove(friend.uid)
                                    else selectedUids.add(friend.uid)
                                }
                                .background(
                                    if (isSelected) gc.accent.copy(alpha = 0.08f) else Color.Transparent,
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier.size(36.dp).background(gc.accent.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(friend.displayName.take(1).uppercase(), color = gc.accent, fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(friend.displayName, color = gc.textPrimary, fontWeight = FontWeight.Medium)
                                Text(friend.email, color = gc.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) gc.accent else gc.textSecondary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Share button
            Button(
                onClick = { onShare(selectedUids.toList()) },
                enabled = selectedUids.isNotEmpty() && !isSharing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = gc.accent, contentColor = gc.surface),
            ) {
                if (isSharing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = gc.surface, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Sharing…")
                } else {
                    Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (selectedUids.isEmpty()) "Select friends to share"
                        else "Share with ${selectedUids.size} friend${if (selectedUids.size > 1) "s" else ""}",
                    )
                }
            }
        }
    }
}

