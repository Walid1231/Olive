package com.waleve.player.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import com.waleve.player.presentation.theme.ForestSurface
import com.waleve.player.presentation.theme.Olive
import com.waleve.player.presentation.theme.OliveBright
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun VuMeterVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 28,
) {
    val barHeights = remember { List(barCount) { Animatable(0.05f) } }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            barHeights.forEachIndexed { index, animatable ->
                launch {
                    // Stagger the start slightly per bar
                    kotlinx.coroutines.delay(index * 15L)
                    while (isActive) {
                        val target = Random.nextFloat() * 0.75f + 0.25f
                        animatable.animateTo(
                            targetValue = target,
                            animationSpec = tween(
                                durationMillis = Random.nextInt(120, 350),
                                easing = FastOutSlowInEasing,
                            )
                        )
                    }
                }
            }
        } else {
            barHeights.forEach { animatable ->
                launch {
                    animatable.animateTo(0.05f, tween(600))
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val barWidth = totalWidth / (barCount * 1.6f)
        val gap = barWidth * 0.6f

        barHeights.forEachIndexed { index, animatable ->
            val x = index * (barWidth + gap)
            val height = animatable.value * size.height
            val barColor = Brush.verticalGradient(
                colors = listOf(
                    OliveBright.copy(alpha = 0.9f),
                    Olive.copy(alpha = 0.7f),
                    ForestSurface.copy(alpha = 0.4f),
                ),
                startY = size.height - height,
                endY = size.height,
            )

            drawRoundRect(
                brush = barColor,
                topLeft = Offset(x, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}
