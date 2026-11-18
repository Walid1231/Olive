package com.waleve.player.presentation.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.waleve.player.domain.model.Album

/** Album tile = photo card (cover + white text on a dark gradient, always readable). */
@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PhotoCard(
        model = album.artUri,
        title = album.name.ifBlank { "Unknown album" },
        subtitle = "${album.artist} · ${album.songCount} ${if (album.songCount == 1) "song" else "songs"}",
        seed = album.name.hashCode(),
        onClick = onClick,
        modifier = modifier.fillMaxWidth().aspectRatio(0.85f),
    )
}
