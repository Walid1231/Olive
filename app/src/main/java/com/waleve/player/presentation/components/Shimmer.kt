package com.waleve.player.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.waleve.player.presentation.theme.GlassInk

/** True while audio is actually playing (not paused). Provided once at the app root. */
val LocalPlaybackActive = compositionLocalOf { false }

/** A soft light sweeping across placeholder blocks (only where they're drawn). */
fun Modifier.shimmer(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -0.6f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shimmerX",
    )
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val w = size.width
            val center = x * w
            drawRect(
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0f)),
                    start = Offset(center - w * 0.35f, 0f),
                    end = Offset(center + w * 0.35f, size.height),
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }
}

/** Grey placeholder block. */
@Composable
fun SkeletonBlock(modifier: Modifier, corner: Int = 8) {
    Box(modifier.clip(RoundedCornerShape(corner.dp)).background(GlassInk.copy(alpha = 0.08f)))
}

/** Placeholder for a song row: cover + two lines. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBlock(Modifier.size(52.dp), corner = 12)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.72f).height(13.dp), corner = 5)
            SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(11.dp), corner = 5)
        }
    }
}

/** A short list of row placeholders with one shared shimmer. */
@Composable
fun SkeletonList(rows: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().shimmer()) {
        repeat(rows) { SkeletonRow() }
    }
}
