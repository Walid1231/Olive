package com.waleve.player.presentation.player

import androidx.compose.material.icons.rounded.Bedtime
import com.waleve.player.presentation.components.springClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.waleve.player.presentation.components.Artwork
import com.waleve.player.presentation.components.GlassGradient
import com.waleve.player.presentation.components.GlassGradientH
import com.waleve.player.presentation.components.glass
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassSub
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.waleve.player.domain.model.PlayerState
import com.waleve.player.presentation.components.GlassCircleButton
import com.waleve.player.presentation.theme.GhibliForestMid
import com.waleve.player.presentation.theme.GhibliLeaf
import com.waleve.player.presentation.theme.GhibliNightCard
import com.waleve.player.presentation.theme.GhibliNightMid
import com.waleve.player.presentation.theme.LocalGhibliColors

@Composable
fun MiniPlayer(
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    onPrevious: () -> Unit = {},
    isNightMode: Boolean = false,
    sleepTimerRemaining: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val song = playerState.shownSong ?: return
    val loading = playerState.isLoading
    val gc = LocalGhibliColors.current

    val progress = if (playerState.duration > 0)
        playerState.currentPosition.toFloat() / playerState.duration.toFloat()
    else 0f

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Colour of the cover → soft glow inside the glass + progress line
    val cover = com.waleve.player.presentation.components.rememberCoverColor(song.albumArtUri, GlassAccent)
    val tint by animateColorAsState(cover, tween(600), label = "miniTint")

    // Swipe the card: left = next, right = previous (it follows the finger, then springs back)
    val dragX = remember { androidx.compose.animation.core.Animatable(0f) }
    val threshold = with(density) { 84.dp.toPx() }

    // Light glass mini player (matches Figma "Light/Mini player")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .graphicsLayer {
                translationX = dragX.value
                alpha = 1f - (kotlin.math.abs(dragX.value) / (threshold * 3f)).coerceIn(0f, 0.45f)
                rotationZ = dragX.value / threshold * 1.2f
            }
            .pointerInput(song.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val x = dragX.value
                        scope.launch {
                            if (kotlin.math.abs(x) >= threshold) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                dragX.animateTo(if (x < 0) -threshold * 2.2f else threshold * 2.2f, tween(140))
                                if (x < 0) onNext() else onPrevious()
                                dragX.snapTo(if (x < 0) threshold * 1.2f else -threshold * 1.2f)
                            }
                            dragX.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 420f))
                        }
                    },
                    onDragCancel = { scope.launch { dragX.animateTo(0f) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { dragX.snapTo(dragX.value + amount) }
                }
            }
            .springClick(onClick = onClick)
            .glass(RoundedCornerShape(22.dp), alpha = 0.82f)
            .drawBehind {
                // Cover-coloured glow on the left, fading out to the right
                drawRect(
                    Brush.horizontalGradient(
                        0f to tint.copy(alpha = 0.30f), 0.55f to tint.copy(alpha = 0.10f), 1f to Color.Transparent,
                    )
                )
            },
    ) {
        Row(
            modifier = Modifier.padding(start = 9.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.albumArtUri, Modifier.size(48.dp), corner = 12.dp, seed = song.title.hashCode())
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleanMiniTitle(song.title),
                    color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = 30.dp),
                )
                if (playerState.loadingSong != null) {
                    Text("Loading…", color = GlassAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                } else Text(
                    text = cleanMiniArtist(song.artist) + if (song.isAudiobook) "  ·  Audiobook" else "",
                    color = GlassSub, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val sleepVm: SleepTimerViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val sleepEot by sleepVm.endOfTrack.collectAsStateWithLifecycle()
            var sleepSheet by remember { mutableStateOf(false) }
            if (sleepTimerRemaining > 0 || sleepEot) {
                Row(
                    Modifier.springClick { sleepSheet = true }.clip(RoundedCornerShape(12.dp)).background(GlassAccent.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Bedtime, "Sleep timer", tint = GlassAccent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(if (sleepEot) "End" else formatSleep(sleepTimerRemaining), color = GlassAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (sleepSheet) SleepTimerSheet(onDismiss = { sleepSheet = false })
            Box(
                modifier = Modifier.springClick {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onPlayPause()
                }.size(40.dp).clip(CircleShape).background(GlassGradient),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    // spinning ring while the song is being fetched / buffering
                    androidx.compose.material3.CircularProgressIndicator(
                        Modifier.size(26.dp), color = Color.White, strokeWidth = 2.5.dp,
                        trackColor = Color.White.copy(alpha = 0.25f),
                    )
                } else Icon(
                    imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    tint = Color.White, modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onNext()
            }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = GlassInk, modifier = Modifier.size(24.dp))
            }
        }
        // progress line along the bottom edge
        Box(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp).fillMaxWidth().height(3.dp)
                .clip(RoundedCornerShape(2.dp)).background(GlassInk.copy(alpha = 0.06f))
        ) {
            Box(
                Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp)
                    .background(Brush.horizontalGradient(listOf(tint, GlassAccent)))
            )
        }
    }
}

private fun cleanMiniTitle(raw: String): String {
    return raw
        .replace(Regex("_+"), " ")
        .replace(Regex("""\s*-\s*"""), " \u2013 ")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
        .ifBlank { "No title" }
}

private fun cleanMiniArtist(raw: String): String =
    if (raw.isBlank() || raw.trim() == "<unknown>") "Unknown Artist" else raw.trim()
