package com.waleve.player.presentation.download

import androidx.compose.foundation.layout.statusBarsPadding
import com.waleve.player.presentation.components.springClick
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import com.waleve.player.presentation.components.GlassGradientH
import com.waleve.player.presentation.components.glass
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassMuted
import com.waleve.player.presentation.theme.GlassSub
import com.waleve.player.presentation.theme.GlassInk
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
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val gc = LocalGhibliColors.current

    val isActive = uiState is DownloadUiState.Downloading || uiState is DownloadUiState.Done

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header: just words ──
        Column(Modifier.padding(horizontal = 22.dp).padding(top = 10.dp, bottom = 16.dp)) {
            Text("Download", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
            Text("Paste a link, or share one to Olive from any app", color = GlassSub, fontSize = 13.5.sp)
        }

        // ── One smart link bar (hidden while downloading) ──
        AnimatedVisibility(
            visible = !isActive,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            LinkBar(
                url = url,
                busy = uiState is DownloadUiState.Loading,
                onChange = viewModel::updateUrl,
                onPaste = { viewModel.pasteAndGo(clipboard.getText()?.text) },
                onGo = viewModel::extractInfo,
                onClear = viewModel::reset,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // -- State Content --
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            when (val state = uiState) {
                is DownloadUiState.Idle -> { IdleState(recent = recent, onPlay = viewModel::playRecent) }
                is DownloadUiState.Loading -> { /* handled in button */ }
                is DownloadUiState.Ready -> {
                    FormatSelectionContent(info = state.info, onDownload = { f, ab -> viewModel.download(f, ab) })
                }
                is DownloadUiState.PlaylistReady -> {
                    PlaylistSelectionContent(items = state.items, onDownload = { urls, audio, ab -> viewModel.downloadBatch(urls, audio, ab) })
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

// ── Link bar ─────────────────────────────────────────────────────────────────

/** What kind of link this is, in plain words ("YouTube playlist"), or null if it isn't one. */
private fun linkSource(url: String): String? {
    val u = url.trim()
    if (!u.startsWith("http://") && !u.startsWith("https://")) return null
    val host = runCatching { java.net.URI(u).host?.removePrefix("www.")?.removePrefix("m.") }.getOrNull() ?: return null
    val playlist = "list=" in u && "watch?v=" !in u || "/playlist" in u || "/sets/" in u
    val name = when {
        host == "music.youtube.com" -> "YouTube Music"
        host.endsWith("youtube.com") || host == "youtu.be" -> "YouTube"
        host.endsWith("soundcloud.com") -> "SoundCloud"
        host.endsWith("instagram.com") -> "Instagram"
        host.endsWith("facebook.com") || host == "fb.watch" -> "Facebook"
        host.endsWith("tiktok.com") -> "TikTok"
        host == "x.com" || host.endsWith("twitter.com") -> "X"
        host.endsWith("vimeo.com") -> "Vimeo"
        host.endsWith("bandcamp.com") -> "Bandcamp"
        else -> host
    }
    return if (playlist) "$name playlist" else "$name link"
}

@Composable
private fun LinkBar(
    url: String,
    busy: Boolean,
    onChange: (String) -> Unit,
    onPaste: () -> Unit,
    onGo: () -> Unit,
    onClear: () -> Unit,
) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val source = remember(url) { linkSource(url) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().height(60.dp)
                .glass(RoundedCornerShape(30.dp), alpha = 0.82f)
                .padding(start = 18.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Link, null, tint = if (url.isBlank()) GlassMuted else GlassAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (url.isEmpty()) Text("Paste a link", color = GlassMuted, fontSize = 15.5.sp)
                androidx.compose.foundation.text.BasicTextField(
                    value = url,
                    onValueChange = onChange,
                    singleLine = true,
                    enabled = !busy,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = GlassInk, fontSize = 15.5.sp,
                        fontFamily = com.waleve.player.presentation.theme.OutfitFamily,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(GlassAccent),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Go,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onGo = { focus.clearFocus(); if (url.isNotBlank()) onGo() },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(6.dp))
            androidx.compose.animation.AnimatedContent(
                targetState = when { busy -> 2; url.isBlank() -> 0; else -> 1 },
                transitionSpec = {
                    (fadeIn(androidx.compose.animation.core.tween(180)) + androidx.compose.animation.scaleIn(initialScale = 0.85f))
                        .togetherWith(fadeOut(androidx.compose.animation.core.tween(120)))
                },
                label = "linkAction",
            ) { mode ->
                when (mode) {
                    0 -> Row(
                        Modifier.springClick(onClick = onPaste).height(46.dp).clip(RoundedCornerShape(23.dp))
                            .background(GlassGradientH).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.ContentPaste, null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Paste", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                    1 -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClear),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, "Clear", tint = GlassMuted, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(4.dp))
                        Box(
                            Modifier.springClick { focus.clearFocus(); onGo() }.size(46.dp).clip(CircleShape).background(GlassGradientH),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Get formats", tint = Color.White, modifier = Modifier.size(22.dp)) }
                    }
                    else -> Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = GlassAccent, strokeWidth = 2.4.dp)
                    }
                }
            }
        }
        // What Olive recognised
        AnimatedVisibility(visible = source != null || busy, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(Modifier.padding(start = 20.dp, top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(GlassAccent))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (busy) "Reading ${source ?: "the link"}…" else source.orEmpty(),
                    color = GlassSub, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ── Empty page: how it works + recent downloads ──────────────────────────────

@Composable
private fun IdleState(recent: List<com.waleve.player.domain.model.Song>, onPlay: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Three steps, one line each — no clutter
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), alpha = 0.6f, elevation = 0.dp).padding(vertical = 16.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            listOf(
                Icons.Rounded.ContentPaste to "Paste a link",
                Icons.Rounded.Tune to "Pick the quality",
                Icons.Rounded.LibraryMusic to "It's in your library",
            ).forEachIndexed { i, (icon, label) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(GlassAccent.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, tint = GlassAccent, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.height(8.dp))
                    Text("${i + 1}", color = GlassMuted, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        label, color = GlassInk, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 15.sp,
                    )
                }
            }
        }

        // Share tip
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GlassAccent.copy(alpha = 0.09f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.IosShare, null, tint = GlassAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Quicker: in YouTube or your browser, tap Share → Olive. The formats open right away.",
                color = GlassSub, fontSize = 12.5.sp, lineHeight = 16.sp,
            )
        }

        if (recent.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "RECENT DOWNLOADS", color = GlassSub, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp,
                    letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
                )
                recent.forEachIndexed { i, song ->
                    Row(
                        Modifier.springClick { onPlay(i) }.fillMaxWidth()
                            .glass(RoundedCornerShape(18.dp), alpha = 0.6f, elevation = 0.dp)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.waleve.player.presentation.components.Artwork(
                            song.albumArtUri, Modifier.size(48.dp), corner = 12.dp, seed = song.title.hashCode(),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(song.artist.takeIf { it.isNotBlank() && it != "<unknown>" }, if (song.isAudiobook) "Audiobook" else null)
                                    .filterNotNull().joinToString(" · ").ifBlank { "Downloaded" },
                                color = GlassSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(Icons.Rounded.PlayArrow, "Play", tint = GlassAccent, modifier = Modifier.padding(horizontal = 8.dp).size(24.dp))
                    }
                }
            }
        }
    }
}

// ── Format Selection (Two-Tiered) ────────────────────────────────────────────




@Composable
private fun FormatSelectionContent(
    info: MediaInfo,
    onDownload: (MediaFormat, Boolean) -> Unit,
) {
    // Long uploads (> 40 min) are probably audiobooks / lectures — preselect that
    var asAudiobook by remember(info.title) { mutableStateOf(info.duration >= 40 * 60) }
    val ink = com.waleve.player.presentation.theme.GlassInk
    val sub = com.waleve.player.presentation.theme.GlassSub

    Column {
        // ── What you're downloading ──
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(com.waleve.player.presentation.theme.GlassFill.copy(alpha = if (com.waleve.player.presentation.theme.GlassMode.dark) 0.7f else 0.78f))
                .border(1.dp, com.waleve.player.presentation.components.GlassEdge, RoundedCornerShape(22.dp))
                .padding(10.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp))) {
                AsyncImage(
                    model = info.thumbnailUrl,
                    contentDescription = info.title,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight()
                        .background(com.waleve.player.presentation.theme.GlassAccent.copy(alpha = 0.12f)),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f),
                        )
                    )
                )
                if (info.duration > 0) {
                    Text(
                        formatDuration(info.duration),
                        color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
                            .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                if (info.duration >= 40 * 60) {
                    Text(
                        "Looks like an audiobook",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.5.sp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                            .clip(RoundedCornerShape(8.dp)).background(com.waleve.player.presentation.components.GlassGradientH)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                info.title, color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 21.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                info.artist, color = sub, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        com.waleve.player.presentation.components.FormatPicker(
            audio = info.quickFormats.filter { it.isAudioOnly },
            video = info.quickFormats.filter { !it.isAudioOnly },
            asAudiobook = asAudiobook,
            onAsAudiobook = { asAudiobook = it },
            onPick = onDownload,
            advanced = info.advancedFormats,
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
    val isNight = false   // glass in both modes: let the app background show through

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
            modifier = Modifier.springClick { onReset() }
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(gc.accent, gc.accentLight, gc.accent)
                    )
                )
                ,
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
    val h = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, mins, secs) else "%d:%02d".format(mins, secs)
}

// ── Playlist Selection ────────────────────────────────────────────

@Composable
private fun PlaylistSelectionContent(
    items: List<com.waleve.player.data.extractor.PlaylistItem>,
    onDownload: (List<String>, Boolean, Boolean) -> Unit,
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
                    modifier = Modifier.springClick {
                            val set = selectedUrls.value.toMutableSet()
                            if (set.contains(item.url)) set.remove(item.url) else set.add(item.url)
                            selectedUrls.value = set
                        }
                        .fillMaxWidth()
                        
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
        var asAudiobook by remember { mutableStateOf(false) }
        com.waleve.player.presentation.components.SaveAsToggle(
            asAudiobook = asAudiobook,
            onChange = { asAudiobook = it },
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onDownload(selectedUrls.value.toList(), true, asAudiobook) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = gc.accent, contentColor = Color.White),
                enabled = selectedUrls.value.isNotEmpty()
            ) {
                Text("Download Audio")
            }
            Button(
                onClick = { onDownload(selectedUrls.value.toList(), false, false) },
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