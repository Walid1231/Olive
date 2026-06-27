package com.waleve.player.presentation.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.waleve.player.data.extractor.MediaFormat
import com.waleve.player.data.extractor.MediaInfo
import com.waleve.player.presentation.theme.GhibliColors
import com.waleve.player.presentation.theme.LocalGhibliColors



@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val url by viewModel.url.collectAsStateWithLifecycle()
    val gc = LocalGhibliColors.current
    val isNight = gc.isNight

    val isActive = uiState is DownloadUiState.Downloading || uiState is DownloadUiState.Done

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isNight)
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0xFF060D1E),
                            0.40f to Color(0xFF0A1628),
                            1.00f to Color(0xFF060D1E),
                        )
                    )
                else
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(gc.surface, gc.surface))
            )
            .verticalScroll(rememberScrollState()),
    ) {
        // -- Premium Header --
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isNight)
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color(0xFF060D1E),
                                0.75f to Color(0xFF060D1E),
                                1.0f to Color.Transparent,
                            )
                        )
                    else
                        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(gc.surface, gc.surface))
                )
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(gc.accent, gc.accentLight)
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.CloudDownload, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Column {
                    Text(
                        "Download",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = gc.textPrimary,
                    )
                    Text(
                        "YouTube, SoundCloud & more",
                        style = MaterialTheme.typography.bodySmall,
                        color = gc.textMuted,
                    )
                }
            }
        }

        // -- Glass Input Card (hidden during active download/done) --
        AnimatedVisibility(
            visible = !isActive,
            enter = expandVertically() + fadeIn(),
            exit  = shrinkVertically() + fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isNight) Color.White.copy(alpha = 0.05f)
                        else Color.White.copy(alpha = 0.75f)
                    )
                    .border(
                        width = 1.dp,
                        color = gc.accent.copy(alpha = if (isNight) 0.20f else 0.25f),
                        shape = RoundedCornerShape(20.dp),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(gc.accent),
                        )
                        Text(
                            "Paste a link",
                            style = MaterialTheme.typography.labelMedium,
                            color = gc.accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    OutlinedTextField(
                        value = url,
                        onValueChange = viewModel::updateUrl,
                        placeholder = { Text("https://youtu.be/...", color = gc.textMuted) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Link, null,
                                tint = if (url.isNotBlank()) gc.accent else gc.textMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor        = gc.accent,
                            unfocusedBorderColor      = gc.accent.copy(alpha = 0.25f),
                            cursorColor               = gc.accent,
                            focusedTextColor          = gc.textPrimary,
                            unfocusedTextColor        = gc.textPrimary,
                            focusedContainerColor     = Color.Transparent,
                            unfocusedContainerColor   = Color.Transparent,
                            focusedLabelColor         = gc.accent,
                            unfocusedLabelColor       = gc.textMuted,
                            focusedPlaceholderColor   = gc.textMuted,
                            unfocusedPlaceholderColor = gc.textMuted,
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(color = gc.textPrimary, fontSize = 14.sp),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )

                    val isExtracting = uiState is DownloadUiState.Loading
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (url.isNotBlank() && !isExtracting)
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(gc.accent, gc.accentLight, gc.accent)
                                    )
                                else
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(gc.accent.copy(alpha = 0.4f), gc.accent.copy(alpha = 0.4f))
                                    )
                            )
                            .clickable(enabled = url.isNotBlank() && !isExtracting) { viewModel.extractInfo() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isExtracting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                                Text("Extracting info...", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            } else {
                                Icon(Icons.Filled.CloudDownload, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Text("Download", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // -- State Content --
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            when (val state = uiState) {
                is DownloadUiState.Idle -> { IdleEmptyState(gc = gc) }
                is DownloadUiState.Loading -> { /* handled in button */ }
                is DownloadUiState.Ready -> {
                    FormatSelectionContent(info = state.info, onDownload = viewModel::download)
                }
                is DownloadUiState.PlaylistReady -> {
                    PlaylistSelectionContent(items = state.items, onDownload = viewModel::downloadBatch)
                }
                is DownloadUiState.Downloading -> {
                    DownloadingContent(progress = state.progress, eta = state.eta, onCancel = viewModel::cancelDownload)
                }
                is DownloadUiState.Done -> {
                    DoneContent(
                        message      = state.message,
                        trackTitle   = state.trackTitle,
                        trackArtist  = state.trackArtist,
                        thumbnailUrl = state.thumbnailUrl,
                        isBatch      = state.isBatch,
                        batchCount   = state.batchCount,
                        onReset      = viewModel::reset,
                    )
                }
                is DownloadUiState.Error -> {
                    ErrorContent(message = state.message, onRetry = viewModel::extractInfo)
                }
            }
        }

        Spacer(modifier = Modifier.height(120.dp))
    }
}

// -- Idle Empty State --
@Composable
private fun IdleEmptyState(gc: GhibliColors) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "idleFloat")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -10f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(2000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ), label = "float",
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.offset(y = floatOffset.dp)) {
            Box(
                modifier = Modifier.size(100.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(gc.accent.copy(alpha = 0.07f))
            )
            Box(
                modifier = Modifier.size(72.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(gc.accent.copy(alpha = 0.20f), gc.accentLight.copy(alpha = 0.12f))
                        )
                    )
                    .border(1.dp, gc.accent.copy(alpha = 0.30f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CloudDownload, null, tint = gc.accent, modifier = Modifier.size(34.dp))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Ready to download", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = gc.textPrimary)
            Text("Paste any YouTube or video link above", style = MaterialTheme.typography.bodySmall, color = gc.textMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("🎵 Audio", "🎞️ Video", "📋 Playlist").forEach { label ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = gc.accent.copy(alpha = 0.10f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, gc.accent.copy(alpha = 0.20f)),
                ) {
                    Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = gc.accent, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}




// ── Format Selection (Two-Tiered) ────────────────────────────────────────────




@Composable
private fun FormatSelectionContent(
    info: MediaInfo,
    onDownload: (MediaFormat) -> Unit,
) {
    val gc = LocalGhibliColors.current
    var showAdvanced by remember { mutableStateOf(false) }

    Column(modifier = Modifier.animateContentSize(animationSpec = tween(400))) {
        // ── Video Info Card ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(gc.surfaceCard)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            AsyncImage(
                model = info.thumbnailUrl,
                contentDescription = info.title,
                modifier = Modifier
                    .size(width = 100.dp, height = 56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(gc.accent.copy(alpha = 0.1f)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = gc.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${info.artist} • ${formatDuration(info.duration)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = gc.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Quick Download Options ─────────────────────
        Text(
            text = "Quick Download",
            style = MaterialTheme.typography.titleSmall,
            color = gc.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        info.quickFormats.forEach { format ->
            FormatRow(
                format = format,
                onClick = { onDownload(format) },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Toggle Advanced ────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { showAdvanced = !showAdvanced }
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = gc.accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (showAdvanced) "Hide Advanced" else "More Formats (${info.advancedFormats.size})",
                style = MaterialTheme.typography.labelMedium,
                color = gc.accent,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // ── Advanced Format List ───────────────────────
        AnimatedVisibility(
            visible = showAdvanced,
            enter = expandVertically(animationSpec = tween(400)),
            exit = shrinkVertically(animationSpec = tween(300)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 120.dp)
            ) {
                info.advancedFormats.forEach { format ->
                    FormatRow(
                        format = format,
                        onClick = { onDownload(format) },
                    )
                }
            }
        }
    }
}

// ── Single Format Row ────────────────────────────────────────────────────────

@Composable
private fun FormatRow(
    format: MediaFormat,
    onClick: () -> Unit,
) {
    val gc = LocalGhibliColors.current
    val icon: ImageVector = when {
        format.formatId == "mp3_320"        -> Icons.Filled.MusicNote
        format.isAudioOnly                  -> Icons.Filled.AudioFile
        format.height >= 720                -> Icons.Filled.HighQuality
        !format.isAudioOnly                 -> Icons.Filled.Videocam
        else                                -> Icons.Filled.CloudDownload
    }
    val iconTint = when {
        format.formatId == "mp3_320" -> Color(0xFFE57373)
        format.isAudioOnly           -> gc.accent
        format.height >= 720         -> Color(0xFF64B5F6)
        else                         -> gc.textMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(gc.surfaceCard)
            .border(1.dp, gc.surfaceCard, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Label + tag
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = format.label,
                style = MaterialTheme.typography.titleSmall,
                color = gc.textPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tag badge
                Text(
                    text = format.tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = gc.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(gc.accent.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                if (format.fileSize > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatFileSize(format.fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = gc.textMuted,
                    )
                }
            }
        }

        // Download arrow
        Icon(
            Icons.Filled.CloudDownload,
            null,
            tint = gc.accent,
            modifier = Modifier.size(22.dp),
        )
    }
}

// ── Downloading Content ──────────────────────────────────────────────────────

@Composable
private fun DownloadingContent(progress: Float, eta: String, onCancel: () -> Unit) {
    val gc = LocalGhibliColors.current
    val fraction = (progress / 100f).coerceIn(0f, 1f)

    // Animate the progress smoothly
    val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = fraction,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 400,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "dl_progress",
    )

    // Pulsing glow effect for the thumb
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "dlPulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Status label ──
        Text(
            text = "Downloading…",
            style = MaterialTheme.typography.titleMedium,
            color = gc.textPrimary,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Large animated percentage ──
        Text(
            text = "${progress.toInt()}%",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = gc.accent,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Premium progress bar with slider thumb ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(gc.surfaceCard),
        ) {
            // Filled portion with gradient
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction.coerceAtLeast(0.01f))
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                gc.accent,
                                gc.accentLight,
                                gc.accent,
                            )
                        )
                    ),
            )

            // Sliding thumb indicator
            if (animatedFraction > 0.02f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedFraction)
                        .wrapContentWidth(Alignment.End),
                    contentAlignment = Alignment.Center,
                ) {
                    // Glow ring behind thumb
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(gc.accent.copy(alpha = glowAlpha * 0.3f)),
                    )
                    // Thumb dot
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.White)
                            .border(
                                width = 3.dp,
                                color = gc.accent,
                                shape = androidx.compose.foundation.shape.CircleShape,
                            ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── ETA / status row ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${(progress * 1).toInt()}% complete",
                style = MaterialTheme.typography.labelMedium,
                color = gc.textMuted,
            )
            Text(
                text = eta,
                style = MaterialTheme.typography.labelMedium,
                color = gc.textMuted,
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Red Cancel button ──
        Button(
            onClick = onCancel,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFEF4444),
                contentColor = Color.White,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        ) {
            Icon(
                Icons.Filled.CloudDownload,
                null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cancel Download", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DoneContent(
    message: String,
    trackTitle: String,
    trackArtist: String,
    thumbnailUrl: String?,
    isBatch: Boolean,
    batchCount: Int,
    onReset: () -> Unit,
) {
    val gc = LocalGhibliColors.current
    val isNight = gc.isNight

    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "successPulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue  = 1.08f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation  = androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "glowScale",
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Rich success card ─────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(
                            gc.accent.copy(alpha = if (isNight) 0.18f else 0.12f),
                            gc.accent.copy(alpha = if (isNight) 0.06f else 0.04f),
                        )
                    )
                )
                .border(
                    1.5.dp,
                    gc.accent.copy(alpha = if (isNight) 0.35f else 0.25f),
                    RoundedCornerShape(24.dp),
                )
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Animated pulsing success icon
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .scale(glowScale)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(gc.accent.copy(alpha = 0.10f)),
                    )
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(gc.accent, gc.accentLight)
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }

                // Track info (when available)
                if (trackTitle.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isNight) Color.White.copy(alpha = 0.05f)
                                else Color.White.copy(alpha = 0.65f)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!thumbnailUrl.isNullOrEmpty()) {
                            coil.compose.AsyncImage(
                                model = thumbnailUrl,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(gc.accent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.MusicNote, null, tint = gc.accent, modifier = Modifier.size(24.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                trackTitle,
                                color = gc.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (trackArtist.isNotBlank()) {
                                Text(
                                    trackArtist,
                                    color = gc.textMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                } else if (isBatch && batchCount > 0) {
                    Text(
                        "Downloaded $batchCount tracks",
                        color = gc.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }

                // "Saved to Library" chip
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(gc.accent.copy(alpha = 0.18f))
                        .border(1.dp, gc.accent.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(Icons.Rounded.LibraryMusic, null, tint = gc.accent, modifier = Modifier.size(16.dp))
                    Text(
                        "Saved to Library",
                        color = gc.accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        letterSpacing = 0.3.sp,
                    )
                }
            }
        }

        // ── Gradient Download Another CTA ─────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(gc.accent, gc.accentLight, gc.accent)
                    )
                )
                .clickable { onReset() },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.CloudDownload, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(
                    "Download Another",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

// ── Error Content ────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    val gc = LocalGhibliColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFEF4444).copy(alpha = if (gc.isNight) 0.12f else 0.08f))
            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.30f), RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Error, null, tint = Color(0xFFEF4444), modifier = Modifier.size(40.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = gc.textPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444),
                    contentColor   = Color.White,
                ),
            ) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Try Again", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1000) "%.1f GB".format(mb / 1024.0)
    else "%.1f MB".format(mb)
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}

// ── Playlist Selection ────────────────────────────────────────────

@Composable
private fun PlaylistSelectionContent(
    items: List<com.waleve.player.data.extractor.PlaylistItem>,
    onDownload: (List<String>, Boolean) -> Unit,
) {
    val gc = LocalGhibliColors.current
    val selectedUrls = remember { mutableStateOf(items.map { it.url }.toSet()) }

    Column(modifier = Modifier.animateContentSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Select items to download",
                style = MaterialTheme.typography.titleSmall,
                color = gc.textPrimary
            )
            androidx.compose.material3.TextButton(
                onClick = {
                    if (selectedUrls.value.size == items.size) {
                        selectedUrls.value = emptySet()
                    } else {
                        selectedUrls.value = items.map { it.url }.toSet()
                    }
                }
            ) {
                Text(
                    if (selectedUrls.value.size == items.size) "Deselect All" else "Select All",
                    color = gc.accent
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(gc.surfaceCard)
        ) {
            items(items, key = { it.url }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val set = selectedUrls.value.toMutableSet()
                            if (set.contains(item.url)) set.remove(item.url) else set.add(item.url)
                            selectedUrls.value = set
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = selectedUrls.value.contains(item.url),
                        onCheckedChange = null,
                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                            checkedColor = gc.accent,
                            uncheckedColor = gc.textMuted
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = gc.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.uploader,
                            style = MaterialTheme.typography.bodySmall,
                            color = gc.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onDownload(selectedUrls.value.toList(), true) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = gc.accent, contentColor = Color.White),
                enabled = selectedUrls.value.isNotEmpty()
            ) {
                Text("Download Audio")
            }
            Button(
                onClick = { onDownload(selectedUrls.value.toList(), false) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = gc.surfaceCard, contentColor = gc.textPrimary),
                enabled = selectedUrls.value.isNotEmpty()
            ) {
                Text("Download Video")
            }
        }
    }
}