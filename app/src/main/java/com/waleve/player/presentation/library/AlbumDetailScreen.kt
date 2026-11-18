package com.waleve.player.presentation.library

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waleve.player.domain.model.Song
import com.waleve.player.presentation.components.GlassButton
import com.waleve.player.presentation.components.GlassIconButton
import com.waleve.player.presentation.components.GradientButton
import com.waleve.player.presentation.components.PhotoCard
import com.waleve.player.presentation.components.SongListItem
import com.waleve.player.presentation.components.glass
import com.waleve.player.presentation.components.rememberSongDeleter
import com.waleve.player.presentation.theme.GlassDanger
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassSub

/** Album / audiobook page: cover photo card, Play · Shuffle · Delete, then every track or chapter. */
@Composable
fun AlbumDetailScreen(
    albumName: String,
    artistName: String,
    onBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val flow = remember(albumName, artistName) { viewModel.albumSongs(albumName, artistName) }
    val songs by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    val deleteSongs = rememberSongDeleter()
    val isBook = songs.isNotEmpty() && songs.all { it.isAudiobook }
    val totalMin = songs.sumOf { it.duration } / 60_000
    val length = if (totalMin >= 60) "${totalMin / 60}h ${totalMin % 60}m" else "${totalMin}m"

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, onBack, contentDescription = "Back")
                Spacer(Modifier.padding(6.dp))
                Text(if (isBook) "Audiobook" else "Album", color = GlassSub, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        item {
            PhotoCard(
                model = songs.firstOrNull()?.albumArtUri,
                title = albumName.ifBlank { "Untitled" },
                subtitle = "$artistName · ${songs.size} ${if (isBook) "chapters" else "songs"} · $length",
                seed = albumName.hashCode(),
                corner = 28.dp,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.25f),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton(if (isBook) "Play from start" else "Play", { if (songs.isNotEmpty()) onSongClick(songs, 0) }, Modifier.weight(1f), icon = Icons.Filled.PlayArrow, enabled = songs.isNotEmpty())
                GlassButton("Shuffle", { if (songs.isNotEmpty()) onSongClick(songs.shuffled(), 0) }, Modifier.weight(1f), icon = Icons.Filled.Shuffle, enabled = songs.isNotEmpty())
                GlassIconButton(Icons.Filled.Delete, { deleteSongs(songs) }, tint = GlassDanger, contentDescription = "Delete all")
            }
        }
        if (songs.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), alpha = 0.5f).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Nothing here any more.", color = GlassSub)
                }
            }
        } else {
            item {
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), alpha = 0.6f).padding(vertical = 6.dp)) {
                    songs.forEachIndexed { i, song ->
                        SongListItem(
                            song = song,
                            onClick = { onSongClick(songs, i) },
                            onFavoriteClick = { viewModel.toggleFavorite(song.id) },
                            onAddToPlaylist = {},
                            onDeleteClick = { deleteSongs(listOf(song)) },
                            onToggleAudiobook = { viewModel.toggleAudiobook(song) },
                            showTrackNumber = true,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}
