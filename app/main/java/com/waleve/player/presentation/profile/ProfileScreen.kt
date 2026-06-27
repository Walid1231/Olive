package com.waleve.player.presentation.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.waleve.player.presentation.theme.GhibliColors
import com.waleve.player.presentation.theme.LocalGhibliColors

/**
 * Profile screen showing user info, Friend Code, bio, and edit/sign-out options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    isNightMode: Boolean,
    onBack: () -> Unit,
) {
    val gc = LocalGhibliColors.current
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Edit fields
    var editName by remember(profile) { mutableStateOf(profile?.displayName ?: "") }
    var editBio by remember(profile) { mutableStateOf(profile?.bio ?: "") }
    var editGenre by remember(profile) { mutableStateOf(profile?.favGenre ?: "") }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileUiEvent.Success -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is ProfileUiEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gc.surface)
            .statusBarsPadding()
    ) {
        // ─── Top Bar ─────────────────────────────────────────────────
        TopAppBar(
            title = { Text("My Profile", color = gc.textPrimary, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = gc.textPrimary)
                }
            },
            actions = {
                if (!isEditing) {
                    IconButton(onClick = { viewModel.toggleEditing() }) {
                        Icon(Icons.Rounded.Edit, "Edit", tint = gc.accent)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )

        val p = profile
        if (p == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = gc.accent)
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            // ─── Avatar (generous top spacing so it's never clipped) ────
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(gc.accent, gc.accentLight))
                    )
                    .border(3.dp, gc.accent.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (p.avatarUrl != null) {
                    AsyncImage(
                        model = p.avatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Rounded.Person,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ─── Name & Email ────────────────────────────────────────
            if (isEditing) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = gc.textPrimary,
                        unfocusedTextColor = gc.textPrimary,
                        focusedBorderColor = gc.accent,
                        cursorColor = gc.accent,
                        focusedLabelColor = gc.accent,
                        unfocusedLabelColor = gc.textSecondary,
                    ),
                )
            } else {
                Text(
                    p.displayName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = gc.textPrimary,
                )
            }
            // Email — use textPrimary with lower alpha for good contrast in both modes
            Text(
                p.email,
                fontSize = 13.sp,
                color = gc.textPrimary.copy(alpha = 0.65f),
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(24.dp))

            // ─── Friend Code (prominent) ─────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = gc.accent.copy(alpha = 0.10f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "MY FRIEND CODE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = gc.accent,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        p.friendCode.ifEmpty { "Generating…" },
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = gc.textPrimary,
                        letterSpacing = 3.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Share this code with friends so they can add you!",
                        fontSize = 12.sp,
                        color = gc.textPrimary.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Copy button — solid accent fill, white text for contrast
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Friend Code", p.friendCode))
                                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = gc.accent,
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp), tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Copy", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        // Share button — outlined with visible accent text + border
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Add me on WaLEve! My friend code: ${p.friendCode}")
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Friend Code"))
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, gc.accent),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = gc.accent),
                        ) {
                            Icon(Icons.Rounded.Share, null, Modifier.size(16.dp), tint = gc.accent)
                            Spacer(Modifier.width(6.dp))
                            Text("Share", color = gc.accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ─── Bio & Genre ─────────────────────────────────────────
            if (isEditing) {
                OutlinedTextField(
                    value = editBio,
                    onValueChange = { editBio = it },
                    label = { Text("Bio") },
                    placeholder = { Text("Tell friends about yourself…") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = gc.textPrimary,
                        unfocusedTextColor = gc.textPrimary,
                        focusedBorderColor = gc.accent,
                        cursorColor = gc.accent,
                        focusedLabelColor = gc.accent,
                        unfocusedLabelColor = gc.textSecondary,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = editGenre,
                    onValueChange = { editGenre = it },
                    label = { Text("Favorite Genre") },
                    placeholder = { Text("e.g. Hip-Hop, Classical, Pop…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = gc.textPrimary,
                        unfocusedTextColor = gc.textPrimary,
                        focusedBorderColor = gc.accent,
                        cursorColor = gc.accent,
                        focusedLabelColor = gc.accent,
                        unfocusedLabelColor = gc.textSecondary,
                    ),
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.toggleEditing() },
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Cancel", color = gc.textPrimary) }
                    Button(
                        onClick = { viewModel.saveProfile(editName, editBio, editGenre) },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = gc.accent),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Save", color = Color.White)
                    }
                }
            } else {
                // Read-only display
                if (!p.bio.isNullOrBlank()) {
                    ProfileInfoCard("Bio", p.bio!!, gc)
                    Spacer(Modifier.height(12.dp))
                }
                if (!p.favGenre.isNullOrBlank()) {
                    ProfileInfoCard("Favorite Genre", p.favGenre!!, gc)
                    Spacer(Modifier.height(12.dp))
                }

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatChip("Friends", "${p.friendCount}", gc)
                    StatChip("Joined", formatJoinDate(p.joinedAt), gc)
                }
            }

            Spacer(Modifier.height(32.dp))

            // ─── Sign Out ────────────────────────────────────────────
            if (!isEditing) {
                TextButton(
                    onClick = {
                        viewModel.signOut()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Logout,
                        null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out", color = Color(0xFFEF4444), fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProfileInfoCard(label: String, value: String, gc: GhibliColors) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = gc.surfaceCard,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                label.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = gc.textPrimary.copy(alpha = 0.50f),
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 14.sp, color = gc.textPrimary)
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, gc: GhibliColors) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(gc.surfaceCard)
            .border(1.dp, gc.accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = gc.accent)
        Text(label, fontSize = 11.sp, color = gc.textPrimary.copy(alpha = 0.55f))
    }
}

private fun formatJoinDate(timestamp: Long): String {
    if (timestamp <= 0) return "New"
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val month = cal.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.SHORT, java.util.Locale.US) ?: ""
    val year = cal.get(java.util.Calendar.YEAR)
    return "$month $year"
}
