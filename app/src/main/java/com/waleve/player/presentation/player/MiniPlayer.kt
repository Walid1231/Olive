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
    isNightMode: Boolean = false,
    sleepTimerRemaining: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val song = playerState.currentSong ?: return
    val gc = LocalGhibliColors.current

    val progress = if (playerState.duration > 0)
        playerState.currentPosition.toFloat() / playerState.duration.toFloat()
    else 0f

    // Light glass mini player (matches Figma "Light/Mini player")
    Box(
        modifier = modifier.springClick(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .glass(RoundedCornerShape(22.dp), alpha = 0.82f)
            ,
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
                Text(
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
                modifier = Modifier.springClick(onClick = onPlayPause).size(40.dp).clip(CircleShape).background(GlassGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    tint = Color.White, modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onNext, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = GlassInk, modifier = Modifier.size(24.dp))
            }
        }
        // progress line along the bottom edge
        Box(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp).fillMaxWidth().height(3.dp)
                .clip(RoundedCornerShape(2.dp)).background(GlassInk.copy(alpha = 0.06f))
        ) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(GlassGradientH))
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
