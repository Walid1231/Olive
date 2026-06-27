package com.waleve.player.presentation.playlist

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waleve.player.domain.model.Song
import com.waleve.player.presentation.components.SongListItem
import com.waleve.player.presentation.theme.LocalGhibliColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: PlaylistViewModel,
    onBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val gc = LocalGhibliColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favorites", color = gc.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = gc.textPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = gc.surface),
            )
        },
        containerColor = gc.surface,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            items(favorites, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    onClick = { onSongClick(favorites, favorites.indexOf(song)) },
                    onFavoriteClick = { },
                    onAddToPlaylist = { },
                )
            }
        }
    }
}
