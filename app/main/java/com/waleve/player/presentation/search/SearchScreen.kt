package com.waleve.player.presentation.search


import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.waleve.player.domain.model.SearchResult
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.repository.AudioQuality
import com.waleve.player.presentation.theme.LocalGhibliColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onSongClick: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val query          by viewModel.query.collectAsStateWithLifecycle()
    val searchResults  by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching    by viewModel.isSearching.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val qualities      by viewModel.qualities.collectAsStateWithLifecycle()
    val isLoadingQualities by viewModel.isLoadingQualities.collectAsStateWithLifecycle()

    val gc = LocalGhibliColors.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var streamingVideoId  by remember { mutableStateOf<String?>(null) }
    var showQualityDialogFor by remember { mutableStateOf<SearchResult?>(null) }
    var isSearchBarFocused by remember { mutableStateOf(false) }

    // Breathing glow for idle state
    val inf = rememberInfiniteTransition(label = "searchGlow")
    val glowPulse by inf.animateFloat(
        0.30f, 0.65f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        "sg",
    )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SearchUiEvent.Error           -> snackbarHostState.showSnackbar(event.message)
                is SearchUiEvent.DownloadStarted -> snackbarHostState.showSnackbar("Download started: ${event.title}")
                is SearchUiEvent.DownloadComplete -> snackbarHostState.showSnackbar("Downloaded: ${event.title}")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize().background(gc.surface),
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {

            // ── Header ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Search Music",
                        style = MaterialTheme.typography.headlineSmall,
                        fontSize = 24.sp,
                        color = gc.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    // "STREAM" source badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(gc.accent.copy(alpha = 0.14f))
                            .border(1.dp, gc.accent.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.MusicNote, null, tint = gc.accent, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("YouTube Music", style = MaterialTheme.typography.labelSmall, color = gc.accent, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
                    }
                }
            }

            // ── Premium Search Bar ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(
                        elevation = if (isSearchBarFocused) 12.dp else 4.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = gc.accent.copy(alpha = 0.15f),
                        spotColor   = gc.accent.copy(alpha = 0.10f),
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(gc.surfaceCard)
                    .border(
                        width = if (isSearchBarFocused) 1.5.dp else 1.dp,
                        color  = if (isSearchBarFocused) gc.accent.copy(alpha = 0.55f)
                                 else gc.textMuted.copy(alpha = 0.20f),
                        shape  = RoundedCornerShape(16.dp),
                    ),
            ) {
                TextField(
                    value = query,
                    onValueChange = viewModel::updateQuery,
                    placeholder = {
                        Text(
                            "Search artists, songs, albums…",
                            color = gc.textMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            null,
                            tint = if (isSearchBarFocused) gc.accent else gc.textMuted,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateQuery("") }) {
                                Icon(Icons.Default.Clear, null, tint = gc.textMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isSearchBarFocused = it.isFocused },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor      = Color.Transparent,
                        unfocusedContainerColor    = Color.Transparent,
                        focusedIndicatorColor      = Color.Transparent,
                        unfocusedIndicatorColor    = Color.Transparent,
                        cursorColor                = gc.accent,
                        focusedTextColor           = gc.textPrimary,
                        unfocusedTextColor         = gc.textPrimary,
                        focusedPlaceholderColor    = gc.textMuted,
                        unfocusedPlaceholderColor  = gc.textMuted,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(color = gc.textPrimary, fontSize = 15.sp),
                    singleLine = true,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Main content ─────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    isSearching -> {
                        // Loading
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = gc.accent, modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("Searching…", style = MaterialTheme.typography.bodyMedium, color = gc.textMuted)
                            }
                        }
                    }
                    searchResults.isNotEmpty() -> {
                        // Results
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                Text(
                                    "${searchResults.size} result${if (searchResults.size != 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = gc.textMuted,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                )
                            }
                            items(searchResults, key = { it.videoId }) { result ->
                                SearchResultCard(
                                    result          = result,
                                    isStreaming     = streamingVideoId == result.videoId,
                                    downloadProgress = activeDownloads[result.videoId],
                                    accent          = gc.accent,
                                    onPlayClick = {
                                        scope.launch {
                                            streamingVideoId = result.videoId
                                            val song = viewModel.getStreamSong(result)
                                            if (song != null) onSongClick(listOf(song), 0)
                                            streamingVideoId = null
                                        }
                                    },
                                    onDownloadClick = {
                                        showQualityDialogFor = result
                                        viewModel.loadQualities(result.videoId)
                                    },
                                )
                            }
                        }
                    }
                    else -> {
                        // Idle / empty state
                        SearchIdleState(glowPulse = glowPulse, accentColor = gc.accent, textPrimary = gc.textPrimary, textMuted = gc.textMuted, onGenreClick = viewModel::updateQuery)
                    }
                }
            }
        }
    }

    showQualityDialogFor?.let { result ->
        AudioQualityDialog(
            title    = result.title,
            qualities = qualities,
            isLoading = isLoadingQualities,
            onDismiss = { viewModel.clearQualities(); showQualityDialogFor = null },
            onQualitySelected = { quality -> viewModel.downloadSong(result, quality); viewModel.clearQualities(); showQualityDialogFor = null },
        )
    }
}

// ── Idle / discover state ─────────────────────────────────────────────────────

@Composable
private fun SearchIdleState(
    glowPulse: Float,
    accentColor: Color,
    textPrimary: Color,
    textMuted: Color,
    onGenreClick: (String) -> Unit,
) {
    val genres = listOf("Lo-fi 🎵", "Jazz 🎷", "Pop ✨", "Anime 🌸", "Classical 🎻", "Hip-hop 🎤")
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Glowing orb icon
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(bottom = 22.dp)) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(accentColor.copy(alpha = glowPulse * 0.50f), Color.Transparent))
                    )
            )
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.35f), accentColor.copy(alpha = 0.08f))))
                    .border(1.5.dp, accentColor.copy(alpha = 0.40f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Search, null, tint = accentColor, modifier = Modifier.size(32.dp))
            }
        }

        Text(
            "Discover music",
            style = MaterialTheme.typography.titleLarge,
            color = textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Search YouTube Music worldwide",
            style = MaterialTheme.typography.bodyMedium,
            color = textMuted,
        )
        Spacer(modifier = Modifier.height(26.dp))

        // Genre suggestion chips
        Text("Try searching for", style = MaterialTheme.typography.labelMedium, color = textMuted)
        Spacer(modifier = Modifier.height(12.dp))
        genres.chunked(3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                row.forEach { genre ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(accentColor.copy(alpha = 0.10f))
                            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                            .clickable { onGenreClick(genre.split(" ").first()) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(genre, style = MaterialTheme.typography.labelMedium, color = accentColor)
                    }
                }
            }
        }
    }
}

// ── Result card ───────────────────────────────────────────────────────────────

@Composable
private fun SearchResultCard(
    result: SearchResult,
    isStreaming: Boolean,
    downloadProgress: DownloadProgress?,
    accent: Color,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    val gc = LocalGhibliColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(14.dp), ambientColor = accent.copy(alpha = 0.08f))
            .clip(RoundedCornerShape(14.dp))
            .background(gc.surfaceCard)
            .border(1.dp, if (isStreaming) accent.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
        Box {
            AsyncImage(
                model = result.thumbnailUrl,
                contentDescription = result.title,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.10f)),
                contentScale = ContentScale.Crop,
            )
            // Streaming badge on thumbnail
            if (isStreaming) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = 0.90f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text("▶ LIVE", fontSize = 7.sp, color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Track info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleSmall,
                color = gc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${result.artist} · ${formatDuration(result.durationMs / 1000)}",
                style = MaterialTheme.typography.bodySmall,
                color = gc.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Actions
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Play / stream button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isStreaming) accent else accent.copy(alpha = 0.15f))
                    .border(1.dp, accent.copy(alpha = 0.35f), CircleShape)
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center,
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.PlayArrow, "Stream", tint = accent, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Download button / progress
            if (downloadProgress != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                    CircularProgressIndicator(
                        progress = { (downloadProgress.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.size(24.dp),
                        color = accent, strokeWidth = 2.5.dp,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(downloadProgress.status, fontSize = 9.sp, color = accent, fontWeight = FontWeight.Bold)
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(gc.surfaceCard)
                        .border(1.dp, gc.textMuted.copy(alpha = 0.25f), CircleShape)
                        .clickable(onClick = onDownloadClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CloudDownload, "Download", tint = gc.textMuted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ── Quality dialog ────────────────────────────────────────────────────────────

@Composable
private fun AudioQualityDialog(
    title: String,
    qualities: List<AudioQuality>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onQualitySelected: (AudioQuality?) -> Unit,
) {
    val gc = LocalGhibliColors.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = gc.surfaceCard),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Select Audio Quality", style = MaterialTheme.typography.titleMedium, color = gc.textPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(title, style = MaterialTheme.typography.bodySmall, color = gc.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(18.dp))

                if (isLoading) {
                    CircularProgressIndicator(color = gc.accent, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (qualities.isEmpty()) {
                    Text("Unable to fetch quality options.", style = MaterialTheme.typography.bodyMedium, color = gc.textPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { onQualitySelected(null) }, colors = ButtonDefaults.buttonColors(containerColor = gc.accent), shape = RoundedCornerShape(12.dp)) {
                        Text("Download Best Available")
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        qualities.forEach { quality ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(gc.accent.copy(alpha = 0.07f))
                                    .border(1.dp, gc.accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .clickable { onQualitySelected(quality) }
                                    .padding(vertical = 13.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(quality.label, style = MaterialTheme.typography.titleSmall, color = gc.textPrimary, fontWeight = FontWeight.Medium)
                                    Text(quality.mimeType, style = MaterialTheme.typography.bodySmall, color = gc.textMuted)
                                }
                                if (quality.contentLength > 0) {
                                    Text(formatFileSize(quality.contentLength), style = MaterialTheme.typography.labelMedium, color = gc.accent, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                TextButton(onClick = onDismiss) { Text("Cancel", color = gc.textMuted) }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    return "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
