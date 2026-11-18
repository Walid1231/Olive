package com.waleve.player.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.waleve.player.presentation.theme.LocalGhibliColors

/**
 * Two-way pill: save audio into the Songs library or the Audiobooks library.
 */
@Composable
fun SaveAsToggle(
    asAudiobook: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gc = LocalGhibliColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(gc.surfaceCard)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SaveAsOption(
            label = "Save as Song",
            icon = Icons.Rounded.MusicNote,
            selected = !asAudiobook,
            onClick = { onChange(false) },
            modifier = Modifier.weight(1f),
        )
        SaveAsOption(
            label = "Save as Audiobook",
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            selected = asAudiobook,
            onClick = { onChange(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SaveAsOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gc = LocalGhibliColors.current
    val bg by animateColorAsState(if (selected) gc.accent else gc.surfaceCard, tween(250), label = "saveAsBg")
    val fg by animateColorAsState(if (selected) gc.surface else gc.textSecondary, tween(250), label = "saveAsFg")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
