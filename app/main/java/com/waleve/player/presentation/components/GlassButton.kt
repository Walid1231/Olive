package com.waleve.player.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waleve.player.presentation.theme.LocalGhibliColors

/**
 * A frosted-glass style icon button.
 * Uses translucent white/dark background + subtle border to simulate glass.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    cornerRadius: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalGhibliColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.glassOverlay)
            .border(1.dp, colors.glassBorder, RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * Circular glass button (for play/pause, etc.)
 */
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    filled: Boolean = false,
    fillColor: Color = Color.White,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalGhibliColors.current
    val bg = if (filled) fillColor else colors.glassOverlay
    val border = if (filled) Color.Transparent else colors.glassBorder
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
