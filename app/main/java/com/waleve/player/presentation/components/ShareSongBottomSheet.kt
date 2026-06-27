package com.waleve.player.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waleve.player.domain.model.Friend
import com.waleve.player.domain.model.Song
import com.waleve.player.presentation.theme.ErrorRed
import com.waleve.player.presentation.theme.LocalGhibliColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSongBottomSheet(
    song: Song,
    friends: List<Friend>,
    isSharing: Boolean,
    onShare: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val gc = LocalGhibliColors.current
    val selectedUids = remember { mutableStateListOf<String>() }

    // Check if the song has a videoId OR is a stream, otherwise it's local only
    val isRemote = song.source == "stream" && song.path.startsWith("http")
    val isLocalOnly = song.videoId == null && !isRemote

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = gc.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Share, null, tint = gc.accent)
                Text(
                    "Share Song",
                    style = MaterialTheme.typography.titleMedium,
                    color = gc.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Song Info Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(gc.surfaceCard, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (song.albumArtUri != null) {
                    coil.compose.AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(gc.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Share, null, tint = gc.accent)
                    }
                }
                Column {
                    Text(song.title, color = gc.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = gc.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Warning for local songs
            if (isLocalOnly) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ErrorRed.copy(alpha = 0.08f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("⚠", fontSize = 14.sp)
                        Text(
                            "This song is local-only and cannot be shared directly. Friends will not be able to download it.",
                            color = ErrorRed,
                            fontSize = 12.sp,
                        )
                    }
                }
            } else {
                Text(
                    "Select friends to share with:",
                    color = gc.textSecondary,
                    fontSize = 13.sp,
                )

                if (friends.isEmpty()) {
                    Text(
                        "No friends yet. Go to the Friends tab to add some!",
                        color = gc.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        friends.forEach { friend ->
                            val isSelected = friend.uid in selectedUids
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selectedUids.remove(friend.uid)
                                        else selectedUids.add(friend.uid)
                                    }
                                    .background(
                                        if (isSelected) gc.accent.copy(alpha = 0.08f) else Color.Transparent,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // Avatar
                                Box(
                                    modifier = Modifier.size(36.dp).background(gc.accent.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(friend.displayName.take(1).uppercase(), color = gc.accent, fontWeight = FontWeight.Bold)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(friend.displayName, color = gc.textPrimary, fontWeight = FontWeight.Medium)
                                    Text(friend.email, color = gc.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(
                                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) gc.accent else gc.textSecondary,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Share button
                Button(
                    onClick = { onShare(selectedUids.toList()) },
                    enabled = selectedUids.isNotEmpty() && !isSharing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = gc.accent, contentColor = gc.surface),
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = gc.surface, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Sharing…")
                    } else {
                        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (selectedUids.isEmpty()) "Select friends to share"
                            else "Share with ${selectedUids.size} friend${if (selectedUids.size > 1) "s" else ""}",
                        )
                    }
                }
            }
        }
    }
}
