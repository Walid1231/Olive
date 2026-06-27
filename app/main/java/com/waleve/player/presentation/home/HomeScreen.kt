package com.waleve.player.presentation.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.waleve.player.domain.model.Song
import com.waleve.player.presentation.components.GhibliSceneCanvas
import com.waleve.player.presentation.theme.GhibliForestDeep
import com.waleve.player.presentation.theme.GhibliForestLight
import com.waleve.player.presentation.theme.GhibliLeaf
import com.waleve.player.presentation.theme.GhibliMist
import com.waleve.player.presentation.theme.GhibliNightCard
import com.waleve.player.presentation.theme.GhibliNightMid
import com.waleve.player.presentation.theme.LocalGhibliColors
import java.util.Calendar

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    isNightMode: Boolean,
    onThemeToggle: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val mostPlayed     by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val isScanning     by viewModel.isScanning.collectAsStateWithLifecycle()
    val gc = LocalGhibliColors.current

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = if (isNightMode) {
        when (hour) {
            in 21..23, in 0..4 -> "Good Night 🌙"
            else               -> "Night Mode 🌙"
        }
    } else {
        when (hour) {
            in 5..11  -> "Good Morning ☀️"
            in 12..17 -> "Good Afternoon 🌿"
            in 18..21 -> "Good Evening 🌅"
            else      -> "Late Night 🌙"
        }
    }

    val bgColor by animateColorAsState(targetValue = gc.surface, animationSpec = tween(1200), label = "bg")
    val textPrimary by animateColorAsState(targetValue = gc.textPrimary, animationSpec = tween(800), label = "tp")
    val textMuted   by animateColorAsState(targetValue = gc.textMuted,   animationSpec = tween(800), label = "tm")
    val accentColor by animateColorAsState(targetValue = gc.accent,      animationSpec = tween(800), label = "ac")

    // Subtle breathing glow pulse for the "Now Playing" hint
    val inf = rememberInfiniteTransition(label = "homeGlow")
    val glowPulse by inf.animateFloat(
        0.35f, 0.65f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        "glow",
    )

    LazyColumn(
        modifier = modifier.fillMaxSize().background(bgColor),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {

        // ── Ghibli Scene Banner ───────────────────────────────────────────────
        item {
            Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                GhibliSceneCanvas(
                    isNight  = isNightMode,
                    onToggle = onThemeToggle,
                    modifier = Modifier.fillMaxSize(),
                )
                // Deep gradient fade into body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, bgColor)))
                )
            }
        }

        // ── Greeting + library stat ───────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(top = 2.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineSmall,
                        fontSize = 24.sp,
                        color = textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    if (isScanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 2.dp, color = accentColor)
                            Spacer(modifier = Modifier.width(7.dp))
                            Text("Scanning library…", style = MaterialTheme.typography.bodySmall, color = textMuted)
                        }
                    } else {
                        // Song count pill
                        val totalSongs = (recentlyPlayed + mostPlayed).distinctBy { it.id }.size
                        if (totalSongs > 0) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(accentColor.copy(alpha = 0.14f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.LibraryMusic, null, tint = accentColor, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("$totalSongs songs", style = MaterialTheme.typography.labelSmall, color = accentColor, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // ── Recently Played ───────────────────────────────────────────────────
        if (recentlyPlayed.isNotEmpty()) {
            item {
                HomeSectionHeader(
                    title    = if (isNightMode) "Recently Played" else "Recently Played",
                    emoji    = if (isNightMode) "🌙" else "🌸",
                    accent   = accentColor,
                    primary  = textPrimary,
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(recentlyPlayed, key = { it.id }) { song ->
                        GhibliSongCard(
                            song      = song,
                            isNight   = isNightMode,
                            accent    = accentColor,
                            onClick   = { onSongClick(recentlyPlayed, recentlyPlayed.indexOf(song)) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // ── Most Played ───────────────────────────────────────────────────────
        if (mostPlayed.isNotEmpty()) {
            item { Spacer(modifier = Modifier.height(18.dp)) }
            item {
                HomeSectionHeader(
                    title   = "Most Played",
                    emoji   = if (isNightMode) "✨" else "⭐",
                    accent  = accentColor,
                    primary = textPrimary,
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(mostPlayed, key = { it.id }) { song ->
                        GhibliSongCard(
                            song    = song,
                            isNight = isNightMode,
                            accent  = accentColor,
                            onClick = { onSongClick(mostPlayed, mostPlayed.indexOf(song)) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // ── Empty state ───────────────────────────────────────────────────────
        if (recentlyPlayed.isEmpty() && mostPlayed.isEmpty() && !isScanning) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Glowing icon orb
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(accentColor.copy(alpha = glowPulse * 0.45f), Color.Transparent)
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.30f), gc.surfaceCard))
                                )
                                .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (isNightMode) "🌙" else "🌿", fontSize = 28.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = if (isNightMode) "Music for the night…" else "Your music awaits",
                        style = MaterialTheme.typography.titleLarge,
                        color = textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Play some songs and they'll appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textMuted,
                    )
                }
            }
        }
    }
}

// ── Section header with coloured left accent bar ──────────────────────────────

@Composable
private fun HomeSectionHeader(title: String, emoji: String, accent: Color, primary: Color) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$emoji $title",
            style = MaterialTheme.typography.titleMedium,
            color = primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Song card — deeper glass, play overlay, shadow ───────────────────────────

@Composable
private fun GhibliSongCard(song: Song, isNight: Boolean, accent: Color, onClick: () -> Unit) {
    val cardBg by animateColorAsState(
        targetValue = if (isNight) GhibliNightCard else Color.White,
        animationSpec = tween(1000), label = "cardBg",
    )
    val textCol by animateColorAsState(
        targetValue = if (isNight) Color(0xFFE2EEE8) else GhibliForestDeep,
        animationSpec = tween(1000), label = "cardText",
    )
    val subCol by animateColorAsState(
        targetValue = GhibliMist,
        animationSpec = tween(1000), label = "cardSub",
    )

    Column(
        modifier = Modifier
            .width(136.dp)
            .shadow(
                elevation = if (isNight) 4.dp else 8.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = accent.copy(alpha = 0.12f),
                spotColor   = accent.copy(alpha = 0.08f),
            )
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(
                width = 1.dp,
                color = if (isNight) Color.White.copy(alpha = 0.08f) else accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(bottom = 10.dp),
    ) {
        // Album art + play overlay
        Box {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = song.title,
                modifier = Modifier
                    .size(136.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(if (isNight) GhibliNightMid else GhibliLeaf.copy(alpha = 0.18f)),
                contentScale = ContentScale.Crop,
            )
            // Play button overlay (bottom-right)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
        }
        // Track info
        Column(modifier = Modifier.padding(horizontal = 10.dp).padding(top = 9.dp)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.labelLarge,
                color = textCol,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.labelSmall,
                color = subCol,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
