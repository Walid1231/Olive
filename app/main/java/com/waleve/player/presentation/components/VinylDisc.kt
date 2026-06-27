package com.waleve.player.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.waleve.player.presentation.theme.Olive
import com.waleve.player.presentation.theme.OliveBright
import kotlinx.coroutines.isActive

@Composable
fun VinylDisc(
    albumArtUri: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
) {
    val rotationAngle = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                rotationAngle.animateTo(
                    targetValue = rotationAngle.value + 360f,
                    animationSpec = tween(4000, easing = LinearEasing)
                )
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotationAngle.value },
        contentAlignment = Alignment.Center,
    ) {
        // Vinyl disc background with grooved rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val maxRadius = this.size.minDimension / 2

            // Main disc
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A), Color(0xFF111111)),
                    center = center,
                    radius = maxRadius,
                ),
                radius = maxRadius,
            )

            // Grooved rings — concentric circles creating the vinyl texture
            for (i in 1..12) {
                val radius = maxRadius * (0.35f + i * 0.05f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.04f + (i % 3) * 0.01f),
                    radius = radius,
                    style = Stroke(width = 0.8f),
                )
            }

            // Outer rim highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.1f),
                radius = maxRadius - 2f,
                style = Stroke(width = 2f),
            )

            // Inner edge of grooves
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = maxRadius * 0.33f,
                style = Stroke(width = 1.5f),
            )
        }

        // Center label — olive circle
        Box(
            modifier = Modifier
                .size(size * 0.38f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(OliveBright, Olive, Olive.copy(alpha = 0.9f))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Album art inside the label or app icon as fallback
            if (!albumArtUri.isNullOrEmpty()) {
                AsyncImage(
                    model = albumArtUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.waleve.player.R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        // Center spindle dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF333333))
        )
    }
}
