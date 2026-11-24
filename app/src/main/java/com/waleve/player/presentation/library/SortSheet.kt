package com.waleve.player.presentation.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waleve.player.domain.model.BookSortOption
import com.waleve.player.domain.model.SortOption
import com.waleve.player.presentation.components.springClick
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassPanel
import com.waleve.player.presentation.theme.GlassSub

/** One way to sort, with what "up" and "down" mean for it. */
data class SortChoice(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val ascLabel: String,
    val descLabel: String,
    /** Names read best A→Z; dates / lengths read best newest / longest first. */
    val defaultAscending: Boolean,
)

val SongSortChoices = listOf(
    SortChoice(SortOption.DATE_ADDED.name, "Recently added", Icons.Rounded.Schedule, "Oldest first", "Newest first", false),
    SortChoice(SortOption.NAME.name, "Title", Icons.Rounded.SortByAlpha, "A to Z", "Z to A", true),
    SortChoice(SortOption.ARTIST.name, "Artist", Icons.Rounded.Mic, "A to Z", "Z to A", true),
    SortChoice(SortOption.ALBUM.name, "Album", Icons.Rounded.Album, "A to Z", "Z to A", true),
    SortChoice(SortOption.DURATION.name, "Length", Icons.Rounded.Timer, "Shortest first", "Longest first", false),
    SortChoice(SortOption.SIZE.name, "File size", Icons.Rounded.Storage, "Smallest first", "Largest first", false),
)

val BookSortChoices = listOf(
    SortChoice(BookSortOption.RECENT.name, "Recently added", Icons.Rounded.Schedule, "Oldest first", "Newest first", false),
    SortChoice(BookSortOption.TITLE.name, "Title", Icons.Rounded.SortByAlpha, "A to Z", "Z to A", true),
    SortChoice(BookSortOption.AUTHOR.name, "Author", Icons.Rounded.Person, "A to Z", "Z to A", true),
    SortChoice(BookSortOption.DURATION.name, "Length", Icons.Rounded.Timer, "Shortest first", "Longest first", false),
    SortChoice(BookSortOption.CHAPTERS.name, "Chapters", Icons.Rounded.AutoStories, "Fewest first", "Most first", false),
    SortChoice(BookSortOption.PROGRESS.name, "Progress", Icons.Rounded.DonutLarge, "Least listened", "Most listened", false),
)

fun List<SortChoice>.choiceFor(key: String): SortChoice = firstOrNull { it.key == key } ?: first()

/** Toolbar pill: tap the name to pick how to sort, tap the arrow to flip the direction. */
@Composable
fun SortPill(choice: SortChoice, ascending: Boolean, onOpen: () -> Unit, onFlip: () -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (ascending) 0f else 180f, label = "sortArrow")
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(GlassAccent.copy(alpha = 0.12f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.springClick(onClick = onOpen).padding(start = 12.dp, end = 9.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(choice.icon, null, tint = GlassAccent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(choice.label, color = GlassAccent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.width(1.dp).height(16.dp).background(GlassAccent.copy(alpha = 0.28f)))
        Box(Modifier.springClick(onClick = onFlip).padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.ArrowUpward,
                contentDescription = if (ascending) choice.ascLabel else choice.descLabel,
                tint = GlassAccent,
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotation },
            )
        }
    }
}

/** "Sort by" sheet. Picking a new option closes it; tapping the selected one flips the direction. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    choices: List<SortChoice>,
    activeKey: String,
    ascending: Boolean,
    onPick: (SortChoice) -> Unit,
    onFlip: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = GlassPanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
            Text("Sort by", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, modifier = Modifier.padding(start = 6.dp))
            Text("Tap the selected one again to reverse it", color = GlassSub, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
            Spacer(Modifier.height(12.dp))
            choices.forEach { c ->
                val active = c.key == activeKey
                Row(
                    Modifier.springClick { if (active) onFlip() else onPick(c) }
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) GlassAccent.copy(alpha = 0.10f) else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                            .background(if (active) GlassAccent else GlassInk.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(c.icon, null, tint = if (active) Color.White else GlassInk.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            c.label, color = if (active) GlassAccent else GlassInk, fontSize = 15.sp,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                        )
                        if (active) Text(if (ascending) c.ascLabel else c.descLabel, color = GlassSub, fontSize = 12.sp)
                    }
                    if (active) {
                        val rotation by animateFloatAsState(if (ascending) 0f else 180f, label = "sheetArrow")
                        Icon(
                            Icons.Rounded.ArrowUpward, null, tint = GlassAccent,
                            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation },
                        )
                    }
                }
            }
        }
    }
}
