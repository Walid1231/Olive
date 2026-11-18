package com.waleve.player.presentation.friends

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.waleve.player.domain.model.SharedTrack
import com.waleve.player.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedPlaylistScreen(
    shareId: String,
    viewModel: SharedPlaylistViewModel,
    isNightMode: Boolean,
    onBack: () -> Unit,
    onImported: (localPlaylistId: Long) -> Unit,
) {
    val gc = LocalGhibliColors.current
    val playlist by viewModel.sharedPlaylist.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val events = viewModel.events

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(shareId) { viewModel.loadSharedPlaylist(shareId) }

    LaunchedEffect(Unit) {
        events.collect { event ->
            when (event) {
                is SharedPlaylistUiEvent.Success  -> snackbarHostState.showSnackbar(event.message)
                is SharedPlaylistUiEvent.Error    -> snackbarHostState.showSnackbar("⚠ ${event.message}")
                is SharedPlaylistUiEvent.Imported -> onImported(event.localPlaylistId)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = playlist?.name ?: "Shared Playlist",
                        color = gc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = gc.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = gc.surface),
            )
        },
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = gc.accent)
            }
            return@Scaffold
        }

        val sharedPlaylist = playlist
        if (sharedPlaylist == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Error, null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                    Text("Could not load playlist", color = gc.textSecondary)
                }
            }
            return@Scaffold
        }

        val remoteTracks = sharedPlaylist.tracks.filter { !it.isLocalOnly && it.videoId != null }
        val localOnlyTracks = sharedPlaylist.tracks.filter { it.isLocalOnly }
        val allDownloaded = remoteTracks.all { downloadProgress[it.videoId]?.isComplete == true }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            // Header card
            item {
                SharedPlaylistHeader(
                    ownerName   = sharedPlaylist.ownerName,
                    description = sharedPlaylist.description,
                    trackCount  = sharedPlaylist.tracks.size,
                    remoteCount = remoteTracks.size,
                    localCount  = localOnlyTracks.size,
                    gc          = gc,
                )
            }

            // Action buttons
            item {
                SharedPlaylistActions(
                    isImporting     = isImporting,
                    allDownloaded   = allDownloaded,
                    hasRemoteTracks = remoteTracks.isNotEmpty(),
                    gc              = gc,
                    onImport        = { viewModel.importPlaylist(shareId) },
                    onDownloadAll   = { viewModel.downloadAll() },
                )
            }

            // Remote tracks
            if (remoteTracks.isNotEmpty()) {
                item {
                    Text(
                        "📥 Downloadable Tracks (${remoteTracks.size})",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = gc.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(remoteTracks, key = { it.videoId ?: it.title }) { track ->
                    SharedTrackRow(
                        track    = track,
                        progress = downloadProgress[track.videoId],
                        gc       = gc,
                        onDownload = { viewModel.downloadTrack(track) },
                    )
                }
            }

            // Local-only tracks
            if (localOnlyTracks.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = gc.textSecondary.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))

                    Text(
                        "🔒 Local-Only Tracks (${localOnlyTracks.size}) — cannot be downloaded",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = gc.textSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    )
                }
                items(localOnlyTracks, key = { "local_${it.title}_${it.artist}" }) { track ->
                    LocalOnlyTrackRow(track = track, gc = gc)
                }
            }
        }
    }
}

@Composable
private fun SharedPlaylistHeader(
    ownerName: String,
    description: String?,
    trackCount: Int,
    remoteCount: Int,
    localCount: Int,
    gc: GhibliColors,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = gc.surfaceCard),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Group, null, tint = gc.accent)
                Text("Shared by $ownerName", color = gc.accent, fontWeight = FontWeight.SemiBold)
            }
            if (!description.isNullOrBlank()) {
                Text(description, color = gc.textSecondary, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoChip("$trackCount tracks", gc)
                if (remoteCount > 0) InfoChip("$remoteCount downloadable", gc, isAccent = true)
                if (localCount > 0)  InfoChip("$localCount local only", gc, isAccent = false, isDim = true)
            }
        }
    }
}

@Composable
private fun InfoChip(text: String, gc: GhibliColors, isAccent: Boolean = false, isDim: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = when {
            isAccent -> gc.accent.copy(alpha = 0.15f)
            isDim    -> gc.textSecondary.copy(alpha = 0.08f)
            else     -> gc.surface.copy(alpha = 0.5f)
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = if (isAccent) gc.accent else gc.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SharedPlaylistActions(
    isImporting: Boolean,
    allDownloaded: Boolean,
    hasRemoteTracks: Boolean,
    gc: GhibliColors,
    onImport: () -> Unit,
    onDownloadAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Save to Library button
        Button(
            onClick = onImport,
            enabled = !isImporting,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = gc.accent, contentColor = gc.surface),
        ) {
            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = gc.surface, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.LibraryAdd, null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(if (isImporting) "Saving…" else "Save to Library")
        }

        // Download All button
        if (hasRemoteTracks) {
            Button(
                onClick = onDownloadAll,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GhibliForestMid,
                    contentColor   = Color.White,
                ),
            ) {
                Icon(Icons.Rounded.CloudDownload, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Download All")
            }
        }
    }
}

@Composable
private fun SharedTrackRow(
    track: SharedTrack,
    progress: TrackDownloadProgress?,
    gc: GhibliColors,
    onDownload: () -> Unit,
) {
    val isDownloading = progress != null && !progress.isComplete && !progress.isFailed
    val isComplete    = progress?.isComplete == true
    val isFailed      = progress?.isFailed == true

    val progressFraction by animateFloatAsState(
        targetValue = (progress?.progress ?: 0f) / 100f,
        animationSpec = tween(300),
        label = "dl_progress",
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = gc.surfaceCard),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Thumbnail
                if (!track.thumbnailUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                            .background(gc.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, tint = gc.accent)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, color = gc.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = gc.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isDownloading) {
                        Text(progress!!.status, color = gc.accent, fontSize = 11.sp)
                    }
                }

                // Action icon
                when {
                    isComplete   -> Icon(Icons.Rounded.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(28.dp))
                    isFailed     -> IconButton(onClick = onDownload) { Icon(Icons.Rounded.Refresh, "Retry", tint = ErrorRed) }
                    isDownloading -> CircularProgressIndicator(modifier = Modifier.size(28.dp), color = gc.accent, strokeWidth = 3.dp)
                    else          -> IconButton(onClick = onDownload) {
                        Icon(Icons.Rounded.FileDownload, "Download", tint = gc.accent)
                    }
                }
            }

            // Progress bar
            if (isDownloading && progressFraction > 0f) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = gc.accent,
                    trackColor = gc.accent.copy(alpha = 0.15f),
                )
            }
        }
    }
}

@Composable
private fun LocalOnlyTrackRow(track: SharedTrack, gc: GhibliColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Lock, null, tint = gc.textSecondary.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = gc.textSecondary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = gc.textSecondary.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Text("Local only", color = gc.textSecondary.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}
