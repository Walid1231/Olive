package com.waleve.player.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Olive's own hand-"painted" countryside (drawn in code, no copyrighted artwork):
 * warm sky → layered blue mountains → patchwork fields → winding dirt road → a little red-roofed
 * cottage and round trees. [nightAlpha] 0 = day, 1 = night (stars, moon, lit cottage window).
 */
@Composable
fun PaintedCountryside(nightAlpha: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val horizon = h * 0.42f

            // ── Static geometry (rebuilt only when the size changes) ──
            fun ridge(baseY: Float, amp: Float, f1: Float, f2: Float, phase: Float): Path = Path().apply {
                moveTo(0f, h)
                lineTo(0f, baseY)
                val steps = 48
                for (i in 0..steps) {
                    val x = w * i / steps
                    val t = i.toFloat() / steps
                    val y = baseY - amp * (0.6f * sin((t * f1 + phase) * 2 * PI).toFloat() +
                        0.4f * sin((t * f2 + phase * 1.7f) * 2 * PI).toFloat())
                    lineTo(x, y)
                }
                lineTo(w, h)
                close()
            }
            val farMountains = ridge(horizon - h * 0.02f, h * 0.06f, 1.3f, 3.1f, 0.15f)
            val midMountains = ridge(horizon + h * 0.03f, h * 0.035f, 2.2f, 4.7f, 0.55f)
            val fields = ridge(horizon + h * 0.08f, h * 0.025f, 1.1f, 2.6f, 0.8f)
            val nearLeft = Path().apply {
                moveTo(0f, h); lineTo(0f, h * 0.62f)
                cubicTo(w * 0.18f, h * 0.58f, w * 0.32f, h * 0.70f, w * 0.44f, h)
                close()
            }
            val nearRight = Path().apply {
                moveTo(w, h); lineTo(w, h * 0.66f)
                cubicTo(w * 0.86f, h * 0.64f, w * 0.70f, h * 0.76f, w * 0.60f, h)
                close()
            }
            // Winding road: wide at the bottom, a thin ribbon at the horizon
            val road = Path().apply {
                moveTo(w * 0.30f, h)
                cubicTo(w * 0.42f, h * 0.82f, w * 0.30f, h * 0.68f, w * 0.47f, horizon + h * 0.12f)
                lineTo(w * 0.495f, horizon + h * 0.12f)
                cubicTo(w * 0.38f, h * 0.68f, w * 0.62f, h * 0.82f, w * 0.68f, h)
                close()
            }
            val rnd = Random(7)
            val stars = List(70) { Triple(rnd.nextFloat(), rnd.nextFloat() * 0.40f, 0.6f + rnd.nextFloat() * 1.6f) }
            val fieldStripes = List(7) { i -> horizon + h * (0.10f + i * 0.022f) }

            onDrawBehind {
                val n = nightAlpha.coerceIn(0f, 1f)
                fun c(day: Long, night: Long) = lerp(Color(day), Color(night), n)

                // Sky
                drawRect(
                    Brush.verticalGradient(
                        0f to c(0xFF7CC6EE, 0xFF0B1A33),
                        0.55f to c(0xFFBFE6F5, 0xFF1D3157),
                        1f to c(0xFFFFF1D6, 0xFF3B4A6E),
                        startY = 0f, endY = horizon + h * 0.1f,
                    ),
                    size = Size(w, horizon + h * 0.1f),
                )

                // Stars + moon (night) / sun (day)
                if (n > 0.01f) {
                    stars.forEach { (x, y, r) ->
                        drawCircle(Color.White.copy(alpha = 0.85f * n), r, Offset(w * x, h * y))
                    }
                    val moon = Offset(w * 0.80f, h * 0.12f)
                    drawCircle(Color(0xFFFFF4C8).copy(alpha = 0.18f * n), h * 0.09f, moon)
                    drawCircle(Color(0xFFFFF4C8).copy(alpha = n), h * 0.045f, moon)
                    drawCircle(c(0xFF7CC6EE, 0xFF0F2040).copy(alpha = n), h * 0.040f, moon + Offset(h * 0.022f, -h * 0.010f))
                }
                if (n < 0.99f) {
                    val sun = Offset(w * 0.78f, h * 0.13f)
                    drawCircle(Color(0xFFFFE9A8).copy(alpha = 0.35f * (1 - n)), h * 0.11f, sun)
                    drawCircle(Color(0xFFFFF6D8).copy(alpha = 1 - n), h * 0.05f, sun)
                }

                // Mountains (atmospheric perspective: farther = bluer, lighter)
                drawPath(farMountains, c(0xFF9CC3D9, 0xFF22365C))
                drawPath(midMountains, c(0xFF6FA4A0, 0xFF1C3049))

                // Patchwork fields
                drawPath(fields, c(0xFF9CCB6A, 0xFF1E3A2E))
                fieldStripes.forEachIndexed { i, y ->
                    val col = if (i % 2 == 0) c(0xFFC9D97A, 0xFF24452F) else c(0xFF7DB85A, 0xFF1A3426)
                    drawRect(col.copy(alpha = 0.35f), Offset(0f, y), Size(w, h * 0.007f))
                }

                // Road
                drawPath(road, c(0xFFE3C48E, 0xFF4A4A55))
                drawPath(road, Brush.verticalGradient(listOf(Color.Transparent, c(0x22000000, 0x44000000)), startY = horizon, endY = h))

                // Cottage on the right
                drawCottage(Offset(w * 0.66f, horizon + h * 0.075f), h * 0.07f, n)

                // Trees
                drawRoundTree(Offset(w * 0.12f, h * 0.60f), h * 0.11f, n)
                drawRoundTree(Offset(w * 0.24f, h * 0.55f), h * 0.07f, n)
                drawRoundTree(Offset(w * 0.86f, h * 0.56f), h * 0.09f, n)
                drawCypress(Offset(w * 0.55f, horizon + h * 0.10f), h * 0.07f, n)
                drawCypress(Offset(w * 0.58f, horizon + h * 0.11f), h * 0.05f, n)

                // Foreground hills
                drawPath(nearLeft, c(0xFF4E9B4A, 0xFF10251A))
                drawPath(nearRight, c(0xFF5AA552, 0xFF132A1E))

                // Soft grass tufts in the foreground
                val tuft = c(0xFF3D8A3E, 0xFF0C1E14)
                for (i in 0 until 14) {
                    val x = w * (if (i < 7) 0.02f + i * 0.055f else 0.62f + (i - 7) * 0.055f)
                    val y = h * (0.92f + (i % 3) * 0.025f)
                    drawCircle(tuft, h * 0.028f, Offset(x, y))
                }

                // Gentle vignette so UI text on top stays readable
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f + 0.2f * n)), startY = h * 0.6f, endY = h))
            }
        }
    )
}

private fun DrawScope.drawRoundTree(base: Offset, r: Float, n: Float) {
    val trunk = lerp(Color(0xFF7A5634), Color(0xFF2A1E16), n)
    val dark = lerp(Color(0xFF2F7D45), Color(0xFF0E2418), n)
    val light = lerp(Color(0xFF5DAE5C), Color(0xFF173626), n)
    drawRect(trunk, Offset(base.x - r * 0.08f, base.y - r * 0.6f), Size(r * 0.16f, r * 0.75f))
    drawCircle(dark, r * 0.62f, Offset(base.x - r * 0.35f, base.y - r * 0.85f))
    drawCircle(dark, r * 0.62f, Offset(base.x + r * 0.35f, base.y - r * 0.85f))
    drawCircle(dark, r * 0.70f, Offset(base.x, base.y - r * 1.25f))
    drawCircle(light.copy(alpha = 0.7f), r * 0.38f, Offset(base.x - r * 0.2f, base.y - r * 1.45f))
}

private fun DrawScope.drawCypress(base: Offset, hgt: Float, n: Float) {
    val col = lerp(Color(0xFF2B6E40), Color(0xFF0D2217), n)
    val p = Path().apply {
        moveTo(base.x, base.y - hgt)
        cubicTo(base.x + hgt * 0.22f, base.y - hgt * 0.6f, base.x + hgt * 0.2f, base.y, base.x, base.y)
        cubicTo(base.x - hgt * 0.2f, base.y, base.x - hgt * 0.22f, base.y - hgt * 0.6f, base.x, base.y - hgt)
        close()
    }
    drawPath(p, col)
}

private fun DrawScope.drawCottage(base: Offset, s: Float, n: Float) {
    val wall = lerp(Color(0xFFF4E6CC), Color(0xFF5A5468), n)
    val roof = lerp(Color(0xFFC8553D), Color(0xFF4A2626), n)
    val bodyW = s * 1.3f
    val bodyH = s * 0.8f
    val left = base.x - bodyW / 2
    val top = base.y - bodyH
    drawRect(wall, Offset(left, top), Size(bodyW, bodyH))
    drawPath(Path().apply {
        moveTo(left - s * 0.12f, top)
        lineTo(base.x, top - s * 0.62f)
        lineTo(left + bodyW + s * 0.12f, top)
        close()
    }, roof)
    // chimney
    drawRect(roof, Offset(left + bodyW * 0.72f, top - s * 0.55f), Size(s * 0.14f, s * 0.3f))
    // window: lit warm at night
    val win = lerp(Color(0xFF7FA7C0), Color(0xFFFFD27A), n)
    val winPos = Offset(left + bodyW * 0.2f, top + bodyH * 0.28f)
    val winSize = Size(s * 0.28f, s * 0.26f)
    if (n > 0.05f) drawCircle(Color(0xFFFFC965).copy(alpha = 0.35f * n), s * 0.45f, winPos + Offset(winSize.width / 2, winSize.height / 2))
    drawRect(win, winPos, winSize)
    drawRect(lerp(Color(0xFF8A5A3B), Color(0xFF2E2220), n), Offset(left + bodyW * 0.62f, top + bodyH * 0.38f), Size(s * 0.24f, bodyH * 0.62f))
}
