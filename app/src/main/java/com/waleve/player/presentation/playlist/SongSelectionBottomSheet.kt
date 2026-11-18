package com.waleve.player.presentation.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.waleve.player.domain.model.Song
import com.waleve.player.presentation.theme.LocalGhibliColors

/**
 * A ModalBottomSheet that displays the full song library and lets the user
 * select multiple songs to add to a playlist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongSelectionBottomSheet(
    allSongs: List<Song>,
    existingSongIds: Set<Long>,
    onAddSongs: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val gc = LocalGhibliColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val selectedIds = remember { mutableListOf<Long>().toMutableStateList() }

    val filteredSongs by remember(allSongs, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) allSongs
            else allSongs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = gc.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(gc.textMuted.copy(alpha = 0.4f)),
            )
        },
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Add songs",
                    style = MaterialTheme.typography.titleMedium,
                    color = gc.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                if (selectedIds.isNotEmpty()) {
                    Text(
                        text = "${selectedIds.size} selected",
                        style = MaterialTheme.typography.labelMedium,
                        color = gc.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // ── Search bar ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search songs…", color = gc.textMuted) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = gc.textMuted) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(gc.surfaceCard, RoundedCornerShape(14.dp)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = gc.accent,
                    unfocusedBorderColor = gc.textMuted.copy(alpha = 0.4f),
                    cursorColor = gc.accent,
                    focusedTextColor = gc.textPrimary,
                    unfocusedTextColor = gc.textPrimary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedPlaceholderColor = gc.textMuted,
                    unfocusedPlaceholderColor = gc.textMuted,
                ),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = gc.textPrimary),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Song list ──
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).height(400.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(filteredSongs, key = { it.id }) { song ->
                    val alreadyInPlaylist = song.id in existingSongIds
                    val isSelected = song.id in selectedIds

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) gc.accent.copy(alpha = 0.08f)
                                else Color.Transparent,
                            )
                            .then(
                                if (alreadyInPlaylist) Modifier
                                else Modifier.clip(RoundedCornerShape(0.dp))
                            )
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Album art thumbnail
                        if (song.albumArtUri != null) {
                            AsyncImage(
                                model = song.albumArtUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(gc.surfaceCard),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(gc.surfaceCard),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("♪", color = gc.textMuted)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (alreadyInPlaylist) gc.textMuted else gc.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = gc.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (alreadyInPlaylist) {
                            Text(
                                "Added",
                                style = MaterialTheme.typography.labelSmall,
                                color = gc.textMuted,
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    if (isSelected) selectedIds.remove(song.id)
                                    else selectedIds.add(song.id)
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Rounded.CheckCircle
                                    else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = if (isSelected) "Deselect" else "Select",
                                    tint = if (isSelected) gc.accent else gc.textMuted,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Add button ──
            Button(
                onClick = {
                    onAddSongs(selectedIds.toList())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = gc.accent,
                    contentColor = Color.White,
                ),
                enabled = selectedIds.isNotEmpty(),
            ) {
                Text(
                    text = if (selectedIds.isEmpty()) "Select songs to add"
                    else "Add ${selectedIds.size} song${if (selectedIds.size > 1) "s" else ""}",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
