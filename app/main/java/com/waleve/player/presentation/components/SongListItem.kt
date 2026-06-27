package com.waleve.player.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import com.waleve.player.domain.model.Song
import com.waleve.player.presentation.theme.LocalGhibliColors

@Composable
fun SongListItem(
    song: Song,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    onDeleteClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
    showTrackNumber: Boolean = false,
    isPlaying: Boolean = false,
) {
    val gc = LocalGhibliColors.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Use derivedStateOf for expensive string building so it only recalculates when song changes
    val subtitle = remember(song.artist, song.duration) {
        "${song.artist} • ${formatDuration(song.duration)}"
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = gc.surfaceCard,
            titleContentColor = gc.textPrimary,
            textContentColor = gc.textMuted,
            title = { Text("Delete Song") },
            text = {
                Text("Remove \"${song.title}\" from your library? This won't delete the file from your device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteClick?.invoke()
                    },
                ) {
                    Text("Delete", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = gc.textMuted)
                }
            },
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isPlaying) Modifier
                    .background(gc.accent.copy(alpha = 0.08f))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Now-playing equalizer indicator or track number
        if (isPlaying) {
            MiniEqualizer(
                color = gc.accent,
                modifier = Modifier.size(28.dp).padding(end = 4.dp),
            )
        } else if (showTrackNumber && song.trackNumber != null) {
            Text(
                text = song.trackNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = gc.textMuted,
                modifier = Modifier.width(28.dp),
            )
        }

        // Album art thumbnail with app icon fallback
        if (!song.albumArtUri.isNullOrEmpty()) {
            val appIconPainter = androidx.compose.ui.res.painterResource(com.waleve.player.R.mipmap.ic_launcher)
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(gc.surfaceCard),
                contentScale = ContentScale.Crop,
                error = appIconPainter,
                fallback = appIconPainter,
            )
        } else {
            // Use app icon as fallback for songs without thumbnails
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(gc.surfaceCard),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.waleve.player.R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Song info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isPlaying) gc.accent else gc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    velocity = 30.dp,
                ),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isPlaying) "♪ $subtitle" else subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isPlaying) gc.accentLight else gc.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Favorite button
        IconButton(onClick = onFavoriteClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (song.isFavorite) gc.accent else gc.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }

        // Overflow menu
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More options",
                    tint = gc.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(gc.surfaceCard),
            ) {
                DropdownMenuItem(
                    text = { Text("Add to playlist", color = gc.textPrimary) },
                    onClick = {
                        menuExpanded = false
                        onAddToPlaylist()
                    }
                )
                if (onShareClick != null) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(Icons.Rounded.Share, null, tint = gc.textPrimary, modifier = Modifier.size(18.dp))
                        },
                        text = { Text("Share with friend", color = gc.textPrimary) },
                        onClick = {
                            menuExpanded = false
                            onShareClick.invoke()
                        }
                    )
                }
                if (onDeleteClick != null) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = { Text("Delete", color = Color(0xFFFF6B6B)) },
                        onClick = {
                            menuExpanded = false
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }
    }
}

/**
 * Mini animated equalizer bars that indicate a song is currently playing.
 */
@Composable
private fun MiniEqualizer(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val bar1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar1",
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.6f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar2",
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar3",
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(bar1, bar2, bar3).forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp * fraction)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
