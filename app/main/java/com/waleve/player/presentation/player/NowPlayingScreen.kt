package com.waleve.player.presentation.player

import android.media.AudioManager
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.waleve.player.R
import com.waleve.player.domain.model.PlayerState
import com.waleve.player.domain.model.RepeatMode
import com.waleve.player.domain.model.Song
import com.waleve.player.presentation.components.ShareSongBottomSheet
import com.waleve.player.presentation.components.formatDuration
import com.waleve.player.presentation.playlist.AddToPlaylistBottomSheet
import com.waleve.player.presentation.playlist.PlaylistViewModel
import com.waleve.player.presentation.playlist.SharePlaylistViewModel
import com.waleve.player.presentation.theme.GhibliForestDeep
import com.waleve.player.presentation.theme.GhibliForestLight
import com.waleve.player.presentation.theme.GhibliLeaf
import com.waleve.player.presentation.theme.GhibliNightDeep
import com.waleve.player.presentation.theme.GhibliNightMid
import com.waleve.player.presentation.theme.GhibliNightSky
import com.waleve.player.presentation.theme.LocalGhibliColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playerState: PlayerState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onToggleFavorite: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    isFavorite: Boolean = false,
    isNightMode: Boolean = false,
    sleepTimerRemaining: Long = 0L,
    modifier: Modifier = Modifier,
    playlistViewModel: PlaylistViewModel? = null,
) {
    val song = playerState.currentSong
    val gc = LocalGhibliColors.current
    val context = LocalContext.current

    var showPlaylistSheet by remember { mutableStateOf(false) }
    var songToShare by remember { mutableStateOf<Song?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showTimerMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    val shareVm: SharePlaylistViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val playerViewModel: PlayerViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val lyrics by playerViewModel.lyrics.collectAsStateWithLifecycle()

    val displayTitle  = cleanTrackTitle(song?.title)
    val displayArtist = cleanArtistName(song?.artist)
    // Context label: show album name like Spotify shows playlist name
    val contextLabel  = song?.album?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Now Playing"

    // Detect audio output for cosmetic display
    val audioOutput = remember(context) { detectAudioOutput(context) }

    // ── Animated colours ─────────────────────────────────────────────────────
    val bgTop by animateColorAsState(
        targetValue = if (isNightMode) GhibliNightSky else GhibliForestDeep,
        animationSpec = tween(1400), label = "bgTop",
    )
    val bgBottom by animateColorAsState(
        targetValue = if (isNightMode) GhibliNightDeep else Color(0xFF0D2B1D),
        animationSpec = tween(1400), label = "bgBottom",
    )
    val accentColor by animateColorAsState(
        targetValue = if (isNightMode) GhibliLeaf else GhibliForestLight,
        animationSpec = tween(1000), label = "accent",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // ── Top Bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "PLAYING FROM",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.50f),
                        letterSpacing = 1.5.sp,
                        fontSize = 9.sp,
                    )
                    Text(
                        text = contextLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // ··· overflow menu
                Box {
                    IconButton(onClick = { showOverflowMenu = true }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        modifier = Modifier.background(gc.surfaceCard),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = gc.textPrimary) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = gc.accent) },
                            onClick = { showPlaylistSheet = true; showOverflowMenu = false },
                        )
                        DropdownMenuItem(
                            text = { Text(if (showLyrics) "Hide Lyrics" else "Show Lyrics", color = gc.textPrimary) },
                            leadingIcon = {
                                Icon(
                                    if (showLyrics) Icons.AutoMirrored.Filled.List else Icons.AutoMirrored.Filled.Notes,
                                    null, tint = gc.accent,
                                )
                            },
                            onClick = { showLyrics = !showLyrics; showOverflowMenu = false },
                        )
                    }
                }
            }

            // ── Lyrics or Album Art ───────────────────────────────────────────
            if (showLyrics) {
                LyricsView(
                    lyrics = lyrics,
                    currentPosition = playerState.currentPosition,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                // Large square album art — signature Spotify look
                val appIconPainter = androidx.compose.ui.res.painterResource(R.mipmap.ic_launcher)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(elevation = 24.dp, shape = RoundedCornerShape(16.dp), ambientColor = accentColor.copy(alpha = 0.4f))
                        .clip(RoundedCornerShape(16.dp))
                        .background(gc.surfaceCard),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!song?.albumArtUri.isNullOrEmpty()) {
                        AsyncImage(
                            model = song?.albumArtUri,
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            error = appIconPainter,
                            fallback = appIconPainter,
                        )
                    } else {
                        // Placeholder with a music note
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.radialGradient(
                                    listOf(accentColor.copy(alpha = 0.25f), gc.surfaceCard)
                                )
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.size(96.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Song Info + Heart ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 22.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            velocity = 40.dp,
                        ),
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = displayArtist,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favourite",
                        tint = if (isFavorite) accentColor else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Seek Bar ─────────────────────────────────────────────────────
            var isSeeking by remember { mutableStateOf(false) }
            var seekPosition by remember { mutableFloatStateOf(0f) }
            val displayPosition = if (isSeeking) seekPosition else playerState.currentPosition.toFloat()
            val duration = playerState.duration.toFloat().coerceAtLeast(1f)
            val fraction = (displayPosition / duration).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val newPos = (offset.x / size.width.toFloat() * duration).coerceIn(0f, duration)
                            seekPosition = newPos
                            onSeek(newPos.toLong())
                        }
                    }
                    .pointerInput(duration) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isSeeking = true
                                seekPosition = (offset.x / size.width.toFloat() * duration).coerceIn(0f, duration)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                seekPosition = (change.position.x / size.width.toFloat() * duration).coerceIn(0f, duration)
                            },
                            onDragEnd = {
                                onSeek(seekPosition.toLong())
                                isSeeking = false
                            },
                            onDragCancel = { isSeeking = false },
                        )
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                // Track background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
                // Progress fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(0.003f))
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                // Small thumb dot
                if (fraction > 0.005f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .wrapContentWidth(Alignment.End),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                    }
                }
            }

            // Timestamps: elapsed left, negative remaining right (Spotify style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatDuration(displayPosition.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.50f),
                )
                val remaining = (playerState.duration - displayPosition.toLong()).coerceAtLeast(0L)
                Text(
                    "-${formatDuration(remaining)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.50f),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Transport Controls ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Shuffle
                IconButton(onClick = onToggleShuffle, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Filled.Shuffle, "Shuffle",
                        tint = if (playerState.shuffleEnabled) accentColor else Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(22.dp),
                    )
                }
                // Previous
                IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                // Play / Pause — large white circle
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = if (isNightMode) GhibliNightMid else GhibliForestDeep,
                        modifier = Modifier.size(34.dp),
                    )
                }
                // Next
                IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                // Repeat
                IconButton(onClick = onCycleRepeat, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = when (playerState.repeatMode) {
                            RepeatMode.ONE -> Icons.Filled.RepeatOne
                            else -> Icons.Filled.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = when (playerState.repeatMode) {
                            RepeatMode.OFF -> Color.White.copy(alpha = 0.45f)
                            else -> accentColor
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Audio Output Indicator ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = audioOutput.icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = audioOutput.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 11.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                // Sleep timer inline
                val isSleepActive = sleepTimerRemaining > 0
                val sleepLabel = if (isSleepActive) {
                    val s = sleepTimerRemaining / 1000
                    "💤 %d:%02d".format(s / 60, s % 60)
                } else null
                if (sleepLabel != null) {
                    Text(
                        text = sleepLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Bottom Action Row: Sleep · Speed · Share · Queue ─────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Sleep Timer
                val isSleepActive = sleepTimerRemaining > 0
                Box {
                    SpotifyActionButton(
                        icon = Icons.Filled.Timer,
                        label = if (isSleepActive) {
                            val s = sleepTimerRemaining / 1000
                            "%d:%02d".format(s / 60, s % 60)
                        } else "Sleep",
                        tint = if (isSleepActive) accentColor else Color.White.copy(alpha = 0.55f),
                        isActive = isSleepActive,
                        onClick = { showTimerMenu = true },
                    )
                    DropdownMenu(
                        expanded = showTimerMenu,
                        onDismissRequest = { showTimerMenu = false },
                        modifier = Modifier.background(gc.surfaceCard),
                    ) {
                        listOf(0, 5, 10, 15, 30, 45, 60).forEach { mins ->
                            DropdownMenuItem(
                                text = { Text(if (mins == 0) "Off" else "$mins min", color = gc.textPrimary) },
                                onClick = { onSetSleepTimer(mins); showTimerMenu = false },
                            )
                        }
                    }
                }

                // Speed
                val isSpeedActive = playerState.playbackSpeed != 1f
                Box {
                    SpotifyActionButton(
                        icon = Icons.Filled.Speed,
                        label = if (isSpeedActive) "${playerState.playbackSpeed}×" else "Speed",
                        tint = if (isSpeedActive) accentColor else Color.White.copy(alpha = 0.55f),
                        isActive = isSpeedActive,
                        onClick = { showSpeedMenu = true },
                    )
                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false },
                        modifier = Modifier.background(gc.surfaceCard),
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${speed}×", color = gc.textPrimary) },
                                onClick = { onSetSpeed(speed); showSpeedMenu = false },
                            )
                        }
                    }
                }

                // Share
                SpotifyActionButton(
                    icon = Icons.Filled.Share,
                    label = "Share",
                    onClick = { songToShare = song },
                )

                // Add to Playlist / Queue
                SpotifyActionButton(
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    label = "Playlist",
                    onClick = { showPlaylistSheet = true },
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Lyrics chip ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(
                        if (showLyrics) accentColor.copy(alpha = 0.25f)
                        else Color.White.copy(alpha = 0.08f)
                    )
                    .border(
                        1.dp,
                        if (showLyrics) accentColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(50.dp),
                    )
                    .clickable { showLyrics = !showLyrics }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (showLyrics) Icons.AutoMirrored.Filled.List else Icons.AutoMirrored.Filled.Notes,
                        contentDescription = null,
                        tint = if (showLyrics) accentColor else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Lyrics",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (showLyrics) accentColor else Color.White.copy(alpha = 0.6f),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    // ── Bottom Sheets ─────────────────────────────────────────────────────────

    if (showPlaylistSheet && playlistViewModel != null && playerState.currentSong != null) {
        val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()
        AddToPlaylistBottomSheet(
            playlists = playlists,
            onPlaylistSelected = { playlistId ->
                playlistViewModel.addSongToPlaylist(playlistId, playerState.currentSong!!.id)
                showPlaylistSheet = false
            },
            onCreatePlaylist = { name, desc ->
                playlistViewModel.createPlaylist(name, desc)
            },
            onDismiss = { showPlaylistSheet = false },
        )
    }

    songToShare?.let { s ->
        val friends by shareVm.friends.collectAsStateWithLifecycle()
        val isSharing by shareVm.isSharing.collectAsStateWithLifecycle()
        ShareSongBottomSheet(
            song = s,
            friends = friends,
            isSharing = isSharing,
            onShare = { uids ->
                shareVm.shareSong(s, uids)
                songToShare = null
            },
            onDismiss = { songToShare = null },
        )
    }
}

// ── Spotify-style action button (icon + label column) ─────────────────────────

@Composable
private fun SpotifyActionButton(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White.copy(alpha = 0.55f),
    isActive: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            icon, null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

// ── Audio output detection ─────────────────────────────────────────────────────

data class AudioOutputInfo(val icon: ImageVector, val label: String)

private fun detectAudioOutput(context: Context): AudioOutputInfo {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return when {
        am.isBluetoothScoOn || am.isBluetoothA2dpOn ->
            AudioOutputInfo(Icons.Filled.Bluetooth, "Bluetooth Audio")
        am.isWiredHeadsetOn ->
            AudioOutputInfo(Icons.Filled.HeadsetMic, "Wired Headphones")
        else ->
            AudioOutputInfo(Icons.Filled.VolumeUp, "Phone Speaker")
    }
}

// ── Lyrics View ───────────────────────────────────────────────────────────────

@Composable
private fun LyricsView(
    lyrics: com.waleve.player.domain.model.Lyrics?,
    currentPosition: Long,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    if (lyrics == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accentColor, modifier = Modifier.size(36.dp))
        }
        return
    }

    if (lyrics.syncedLyrics.isEmpty()) {
        val plainText = lyrics.plainLyrics
        if (plainText.isNullOrBlank()) {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Text(
                    "No lyrics found for this song",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            val scrollState = rememberScrollState()
            Column(
                modifier = modifier
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = plainText,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    val listState = rememberLazyListState()
    var activeIndex by remember { mutableStateOf(-1) }

    LaunchedEffect(currentPosition, lyrics.syncedLyrics) {
        val newIndex = lyrics.syncedLyrics.indexOfLast { it.timestampMs <= currentPosition }
        if (newIndex != activeIndex) {
            activeIndex = newIndex
            if (newIndex >= 0) {
                listState.animateScrollToItem(index = maxOf(0, newIndex - 3))
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(vertical = 8.dp),
        contentPadding = PaddingValues(vertical = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(lyrics.syncedLyrics) { index, line ->
            val isActive = index == activeIndex
            val isPast = index < activeIndex
            val textColor by animateColorAsState(
                targetValue = when {
                    isActive -> accentColor
                    isPast   -> Color.White.copy(alpha = 0.7f)
                    else     -> Color.White.copy(alpha = 0.35f)
                },
                animationSpec = tween(400),
                label = "lyric_$index",
            )
            val fontSize by animateFloatAsState(
                targetValue = if (isActive) 21f else 17f,
                animationSpec = tween(400),
                label = "lyric_size_$index",
            )
            Text(
                text = line.text,
                color = textColor,
                fontSize = fontSize.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
        }
    }
}

// ── String utilities ──────────────────────────────────────────────────────────

private fun cleanTrackTitle(raw: String?): String {
    if (raw.isNullOrBlank()) return "No song selected"
    return raw
        .replace(Regex("_+"), " ")
        .replace(Regex("""\s*-\s*"""), " – ")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
}

private fun cleanArtistName(raw: String?): String =
    if (raw.isNullOrBlank() || raw.trim() == "<unknown>") "Unknown Artist" else raw.trim()
