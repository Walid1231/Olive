package com.waleve.player.presentation.playlist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import com.waleve.player.presentation.theme.LocalGhibliColors

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val gc = LocalGhibliColors.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = gc.surfaceCard,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("New Playlist", style = MaterialTheme.typography.headlineSmall, color = gc.textPrimary)
                Spacer(modifier = Modifier.height(16.dp))

                // Custom icon picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(gc.surface)
                            .clickable { imagePicker.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selectedImageUri != null) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Playlist icon",
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Rounded.AddPhotoAlternate,
                                    contentDescription = "Add icon",
                                    tint = gc.textMuted,
                                    modifier = Modifier.size(24.dp),
                                )
                                Text("Icon", fontSize = 10.sp, color = gc.textMuted)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (selectedImageUri != null) "Tap to change" else "Add custom icon",
                        style = MaterialTheme.typography.bodySmall,
                        color = gc.textMuted,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = gc.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = gc.accent,
                        unfocusedBorderColor = gc.textMuted.copy(alpha = 0.5f),
                        cursorColor = gc.accent,
                        focusedTextColor = gc.textPrimary,
                        unfocusedTextColor = gc.textPrimary,
                        focusedLabelColor = gc.accent,
                        unfocusedLabelColor = gc.textMuted,
                    ),
                    singleLine = true,
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = gc.textPrimary),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)", color = gc.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = gc.accent,
                        unfocusedBorderColor = gc.textMuted.copy(alpha = 0.5f),
                        cursorColor = gc.accent,
                        focusedTextColor = gc.textPrimary,
                        unfocusedTextColor = gc.textPrimary,
                        focusedLabelColor = gc.accent,
                        unfocusedLabelColor = gc.textMuted,
                    ),
                    maxLines = 2,
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = gc.textPrimary),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = gc.textMuted)
                    }
                    TextButton(
                        onClick = { onCreate(name, description.ifBlank { null }) },
                        enabled = name.isNotBlank(),
                    ) {
                        Text("Create", color = gc.accent)
                    }
                }
            }
        }
    }
}
