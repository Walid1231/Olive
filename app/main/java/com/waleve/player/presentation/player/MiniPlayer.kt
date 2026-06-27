package com.waleve.player.presentation.player

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
import androidx.compose.foundation.shape.CircleShape
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

    val bgColor by animateColorAsState(
        targetValue = if (isNightMode) GhibliNightCard else GhibliForestMid,
        animationSpec = tween(1200),
        label = "miniPlayerBg",
    )
    val progressBg by animateColorAsState(
        targetValue = if (isNightMode) GhibliNightMid else Color(0xFF1B4332),
        animationSpec = tween(1200),
        label = "miniProgressBg",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
    ) {
        // Progress bar at top of mini player
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(progressBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress)
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(listOf(GhibliLeaf, Color(0xFF74C69D)))
                    )
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Album art
            val appIconPainter = androidx.compose.ui.res.painterResource(com.waleve.player.R.mipmap.ic_launcher)
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
                contentScale = ContentScale.Crop,
                error = appIconPainter,
                fallback = appIconPainter,
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Song info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleanMiniTitle(song.title),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        velocity = 30.dp,
                    ),
                )
                Text(
                    text = cleanMiniArtist(song.artist),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Sleep timer indicator
            if (sleepTimerRemaining > 0) {
                val totalSec = sleepTimerRemaining / 1000
                val m = totalSec / 60
                val s = totalSec % 60
                Text(
                    text = "💤 %d:%02d".format(m, s),
                    style = MaterialTheme.typography.labelSmall,
                    color = GhibliLeaf,
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Glass play/pause button
            GlassCircleButton(
                onClick = onPlayPause,
                size = 38.dp,
                filled = true,
                fillColor = Color.White,
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = if (isNightMode) GhibliNightCard else GhibliForestMid,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Glass next button
            GlassCircleButton(onClick = onNext, size = 34.dp) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
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
