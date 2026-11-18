package com.waleve.player.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waleve.player.domain.model.Song
import com.waleve.player.presentation.components.Artwork
import com.waleve.player.presentation.components.Avatar
import com.waleve.player.presentation.components.GlassChip
import com.waleve.player.presentation.components.GlassGradientH
import com.waleve.player.presentation.components.GlassIconButton
import com.waleve.player.presentation.components.GlassProgress
import com.waleve.player.presentation.components.GradientButton
import com.waleve.player.presentation.components.Overline
import com.waleve.player.presentation.components.SectionHeader
import com.waleve.player.presentation.components.SongListItem
import com.waleve.player.presentation.components.glass
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassMuted
import com.waleve.player.presentation.theme.GlassSub
import java.util.Calendar

/** Home (Figma "01 Home"): greeting, search, chips, continue listening, recently played, quick picks. */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    isNightMode: Boolean,
    onThemeToggle: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchOnline: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenLibrary: (Int) -> Unit = {},
    onOpenFriends: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    notificationCount: Int = 0,
    currentPlayingSongId: Long? = null,
) {
    val continueListening by viewModel.continueListening.collectAsStateWithLifecycle()
    val allAudiobooks by viewModel.audiobooks.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val songCount by viewModel.songCount.collectAsStateWithLifecycle()

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
    }
    val firstName = profile?.displayName?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() && it != "Unknown" } ?: "there"
    val quickPicks = (mostPlayed.ifEmpty { recentlyPlayed }).take(6)

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // ── Header ──
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, color = GlassSub, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("$firstName ✦", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    GlassIconButton(Icons.Rounded.Notifications, onOpenFriends, contentDescription = "Inbox")
                    if (notificationCount > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(Color(0xFFE0607E)),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (notificationCount > 9) "9+" else "$notificationCount", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Avatar(profile?.avatarUrl, profile?.displayName ?: "Olive", 44.dp, Modifier.clickable(onClick = onOpenProfile))
            }
        }
        // ── Search pill ──
        item {
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), alpha = 0.7f).clickable(onClick = onSearchOnline).padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = GlassSub, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Search songs, audiobooks, artists…", color = GlassMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        // ── Chips ──
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("For you", true, {})
                GlassChip("Songs", false, { onOpenLibrary(0) }, count = songCount.takeIf { it > 0 })
                GlassChip("Audiobooks", false, { onOpenLibrary(1) }, count = allAudiobooks.size.takeIf { it > 0 })
                GlassChip("Downloads", false, onOpenDownloads)
            }
        }
        // ── Continue listening ──
        continueListening.firstOrNull()?.let { ch ->
            item {
                val book = allAudiobooks.filter { it.album == ch.album && it.artist == ch.artist }
                val idx = book.indexOfFirst { it.id == ch.id }.coerceAtLeast(0)
                val frac = if (book.isEmpty()) 0f else (book.count { it.playCount > 0 }.toFloat() / book.size)
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), alpha = 0.7f).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(ch.albumArtUri, Modifier.size(104.dp), corner = 18.dp, seed = ch.album.hashCode())
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Overline("Continue listening")
                        Text(ch.album.ifBlank { ch.title }, color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Audiobook  ·  Chapter ${idx + 1} of ${book.size.coerceAtLeast(1)}", color = GlassSub, fontSize = 12.5.sp, maxLines = 1)
                        GlassProgress(frac)
                        GradientButton("Resume", { onSongClick(book.ifEmpty { listOf(ch) }, if (book.isEmpty()) 0 else idx) }, icon = Icons.Rounded.PlayArrow, height = 34.dp)
                    }
                }
            }
        }
        // ── Recently played ──
        if (recentlyPlayed.isNotEmpty()) {
            item { SectionHeader("Recently played", action = "See all", onAction = { onOpenLibrary(0) }) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recentlyPlayed.take(12), key = { "r_${it.id}" }) { song ->
                        Column(
                            Modifier.width(118.dp).glass(RoundedCornerShape(20.dp), alpha = 0.6f)
                                .clickable { onSongClick(recentlyPlayed, recentlyPlayed.indexOf(song)) }
                                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
                        ) {
                            Artwork(song.albumArtUri, Modifier.fillMaxWidth().aspectRatio(1f), seed = song.title.hashCode())
                            Spacer(Modifier.height(8.dp))
                            Text(song.title, color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, color = GlassSub, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        // ── Quick picks ──
        if (quickPicks.isNotEmpty()) {
            item { SectionHeader("Quick picks", action = "Shuffle", onAction = { val s = quickPicks.shuffled(); onSongClick(s, 0) }) }
            item {
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), alpha = 0.55f).padding(6.dp)) {
                    quickPicks.forEachIndexed { i, song ->
                        SongListItem(
                            song = song,
                            onClick = { onSongClick(quickPicks, i) },
                            onFavoriteClick = {},
                            onAddToPlaylist = {},
                            isPlaying = song.id == currentPlayingSongId,
                        )
                    }
                }
            }
        }
        // ── Empty state ──
        if (recentlyPlayed.isEmpty() && mostPlayed.isEmpty() && continueListening.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), alpha = 0.55f).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(GlassGradientH), contentAlignment = Alignment.Center) {
                        Text("♪", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(if (songCount > 0) "Your music is ready" else "Let's find your music", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (songCount > 0) "$songCount songs in your library. Play something and it'll show up here."
                        else "Olive scans your phone for music and audiobooks, or search online to stream and download.",
                        color = GlassSub, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (isScanning) CircularProgressIndicator(Modifier.size(24.dp), color = GlassAccent, strokeWidth = 2.dp)
                        else com.waleve.player.presentation.components.GlassButton("Rescan", viewModel::scanMusic, icon = Icons.Rounded.Refresh, height = 40.dp)
                        GradientButton(if (songCount > 0) "Open library" else "Search online", { if (songCount > 0) onOpenLibrary(0) else onSearchOnline() }, height = 40.dp)
                    }
                }
            }
        }
    }
}
