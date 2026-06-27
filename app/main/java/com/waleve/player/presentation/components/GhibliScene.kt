package com.waleve.player.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.waleve.player.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// ── Night weather states ──────────────────────────────────────────────────────
private enum class NightWeather { RAIN, WIND_THUNDER }

// ── Stable per-frame seeds (computed once, reused every draw) ─────────────────
private data class RainSeed(val xFrac: Float, val yOff: Float, val spd: Float, val len: Float, val a: Float)
private data class WindSeed(val xFrac: Float, val yFrac: Float, val len: Float, val spd: Float, val a: Float)

/**
 * Ghibli countryside road scene with live weather effects.
 *
 * Day   → clouds drifting across the sky + rain begins after 2–3 minutes
 * Night → randomly alternates every 25–45 s between:
 *           • Rain state  — heavy falling rain
 *           • Wind+Thunder state — near-horizontal wind streaks + lightning bolt every 1–3 s
 */
@Composable
fun GhibliSceneCanvas(
    isNight: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (() -> Unit)? = null,
) {
    // ── Day/Night blend ───────────────────────────────────────────────────────
    val nightAlpha by animateFloatAsState(
        targetValue = if (isNight) 1f else 0f,
        animationSpec = tween(durationMillis = 1600, easing = LinearEasing),
        label = "nightAlpha",
    )
    val dayAlpha = 1f - nightAlpha

    val inf = rememberInfiniteTransition(label = "sceneAnim")

    // ── Firefly pulses (20 independent phases for dense swarm) ───────────────
    val ff1  by inf.animateFloat(0.20f, 1.00f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), "ff1")
    val ff2  by inf.animateFloat(1.00f, 0.30f, infiniteRepeatable(tween(2300), RepeatMode.Reverse), "ff2")
    val ff3  by inf.animateFloat(0.50f, 0.95f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), "ff3")
    val ff4  by inf.animateFloat(0.70f, 0.15f, infiniteRepeatable(tween(2700), RepeatMode.Reverse), "ff4")
    val ff5  by inf.animateFloat(0.30f, 0.90f, infiniteRepeatable(tween(2100), RepeatMode.Reverse), "ff5")
    val ff6  by inf.animateFloat(0.60f, 0.10f, infiniteRepeatable(tween(1650), RepeatMode.Reverse), "ff6")
    val ff7  by inf.animateFloat(0.15f, 0.85f, infiniteRepeatable(tween(2450), RepeatMode.Reverse), "ff7")
    val ff8  by inf.animateFloat(0.80f, 0.25f, infiniteRepeatable(tween(1900), RepeatMode.Reverse), "ff8")
    val ff9  by inf.animateFloat(0.40f, 0.95f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), "ff9")
    val ff10 by inf.animateFloat(0.55f, 0.05f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), "ff10")

    val ffDrift  by inf.animateFloat(-8f,  8f,  infiniteRepeatable(tween(3200), RepeatMode.Reverse), "ffDrift")
    val ffDrift2 by inf.animateFloat(-5f,  5f,  infiniteRepeatable(tween(4100), RepeatMode.Reverse), "ffDrift2")
    val ffDrift3 by inf.animateFloat(-10f, 10f, infiniteRepeatable(tween(2800), RepeatMode.Reverse), "ffDrift3")

    // ── Birds ─────────────────────────────────────────────────────────────────
    val bird1 by inf.animateFloat(-0.15f, 1.15f, infiniteRepeatable(tween(7000,  easing = LinearEasing, delayMillis = 0),    RepeatMode.Restart), "b1")
    val bird2 by inf.animateFloat(-0.15f, 1.15f, infiniteRepeatable(tween(9500,  easing = LinearEasing, delayMillis = 2500), RepeatMode.Restart), "b2")
    val bird3 by inf.animateFloat(-0.15f, 1.15f, infiniteRepeatable(tween(6500,  easing = LinearEasing, delayMillis = 4800), RepeatMode.Restart), "b3")
    val bird4 by inf.animateFloat(-0.15f, 1.15f, infiniteRepeatable(tween(11000, easing = LinearEasing, delayMillis = 1200), RepeatMode.Restart), "b4")
    val bird5 by inf.animateFloat(-0.15f, 1.15f, infiniteRepeatable(tween(8200,  easing = LinearEasing, delayMillis = 6500), RepeatMode.Restart), "b5")
    val bird6 by inf.animateFloat(-0.15f, 1.15f, infiniteRepeatable(tween(7600,  easing = LinearEasing, delayMillis = 3300), RepeatMode.Restart), "b6")
    val bird7 by inf.animateFloat(-0.15f, 1.15f, infiniteRepeatable(tween(5800,  easing = LinearEasing, delayMillis = 800),  RepeatMode.Restart), "b7")
    val bird8 by inf.animateFloat(-0.15f, 1.15f, infiniteRepeatable(tween(6200,  easing = LinearEasing, delayMillis = 2000), RepeatMode.Restart), "b8")
    val wingFlapFast by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(260), RepeatMode.Reverse), "wf")
    val wingFlapSlow by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), "ws")

    // ── Bats ──────────────────────────────────────────────────────────────────
    val bat1X by inf.animateFloat(1.15f, -0.15f, infiniteRepeatable(tween(4200, easing = LinearEasing, delayMillis = 0),    RepeatMode.Restart), "bt1x")
    val bat2X by inf.animateFloat(1.15f, -0.15f, infiniteRepeatable(tween(5600, easing = LinearEasing, delayMillis = 1800), RepeatMode.Restart), "bt2x")
    val bat3X by inf.animateFloat(-0.15f, 1.15f, infiniteRepeatable(tween(3800, easing = LinearEasing, delayMillis = 2600), RepeatMode.Restart), "bt3x")
    val bat4X by inf.animateFloat(1.15f, -0.15f, infiniteRepeatable(tween(6800, easing = LinearEasing, delayMillis = 900),  RepeatMode.Restart), "bt4x")
    val bat5X by inf.animateFloat(-0.15f, 1.15f, infiniteRepeatable(tween(4900, easing = LinearEasing, delayMillis = 3400), RepeatMode.Restart), "bt5x")
    val batFlapFast by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(180), RepeatMode.Reverse), "bf")

    // ── DAY CLOUDS — 6 fluffy clouds at different speeds / altitudes ──────────
    val cloud1 by inf.animateFloat(-0.30f, 1.30f, infiniteRepeatable(tween(25000, easing = LinearEasing, delayMillis = 0),     RepeatMode.Restart), "cl1")
    val cloud2 by inf.animateFloat(-0.30f, 1.30f, infiniteRepeatable(tween(32000, easing = LinearEasing, delayMillis = 4000),  RepeatMode.Restart), "cl2")
    val cloud3 by inf.animateFloat(-0.30f, 1.30f, infiniteRepeatable(tween(19000, easing = LinearEasing, delayMillis = 9500),  RepeatMode.Restart), "cl3")
    val cloud4 by inf.animateFloat(-0.30f, 1.30f, infiniteRepeatable(tween(38000, easing = LinearEasing, delayMillis = 2000),  RepeatMode.Restart), "cl4")
    val cloud5 by inf.animateFloat(-0.30f, 1.30f, infiniteRepeatable(tween(22000, easing = LinearEasing, delayMillis = 7000),  RepeatMode.Restart), "cl5")
    val cloud6 by inf.animateFloat(-0.30f, 1.30f, infiniteRepeatable(tween(28000, easing = LinearEasing, delayMillis = 14000), RepeatMode.Restart), "cl6")

    // ── DAY RAIN — master Y phase; drops appear after 2–3 min ────────────────
    val rainPhase by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), "rainPhase")

    var dayRainEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(isNight) {
        if (!isNight) {
            dayRainEnabled = false
            // Delay 2–3 minutes before rain starts in day mode
            delay(Random.nextLong(120_000L, 180_000L))
            if (!isNight) dayRainEnabled = true
        } else {
            dayRainEnabled = false
        }
    }
    val dayRainAlpha by animateFloatAsState(
        targetValue = if (dayRainEnabled && !isNight) 1f else 0f,
        animationSpec = tween(4000),
        label = "dayRainA",
    )

    // Stable per-drop seeds for day rain (90 drops)
    val dayRainSeeds = remember {
        List(90) {
            RainSeed(
                xFrac = Random.nextFloat(),
                yOff  = Random.nextFloat(),
                spd   = 0.6f + Random.nextFloat() * 0.8f,
                len   = 12f  + Random.nextFloat() * 8f,
                a     = 0.30f + Random.nextFloat() * 0.30f,
            )
        }
    }

    // ── NIGHT WEATHER STATE MACHINE ───────────────────────────────────────────
    var nightWeather by remember { mutableStateOf(NightWeather.RAIN) }
    LaunchedEffect(isNight) {
        if (isNight) {
            while (true) {
                delay(Random.nextLong(25_000L, 45_000L))
                nightWeather = if (nightWeather == NightWeather.RAIN)
                    NightWeather.WIND_THUNDER else NightWeather.RAIN
            }
        }
    }

    val nightRainAlpha by animateFloatAsState(
        targetValue = if (isNight && nightWeather == NightWeather.RAIN) 1f else 0f,
        animationSpec = tween(2000),
        label = "nightRainA",
    )
    val nightWindAlpha by animateFloatAsState(
        targetValue = if (isNight && nightWeather == NightWeather.WIND_THUNDER) 1f else 0f,
        animationSpec = tween(2000),
        label = "nightWindA",
    )

    // Stable seeds for night rain (110 drops — heavier than day)
    val nightRainSeeds = remember {
        List(110) {
            RainSeed(
                xFrac = Random.nextFloat(),
                yOff  = Random.nextFloat(),
                spd   = 0.7f + Random.nextFloat() * 0.6f,
                len   = 15f  + Random.nextFloat() * 10f,
                a     = 0.35f + Random.nextFloat() * 0.35f,
            )
        }
    }

    // ── WIND phase (fast sweep) ────────────────────────────────────────────────
    val windPhase by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart), "windPhase")

    // Stable seeds for wind streaks (65 streaks)
    val windSeeds = remember {
        List(65) {
            WindSeed(
                xFrac = Random.nextFloat(),
                yFrac = 0.03f + Random.nextFloat() * 0.55f,
                len   = 40f   + Random.nextFloat() * 90f,
                spd   = 0.4f  + Random.nextFloat() * 0.6f,
                a     = 0.12f + Random.nextFloat() * 0.22f,
            )
        }
    }

    // ── THUNDER & LIGHTNING ───────────────────────────────────────────────────
    var thunderFlashAlpha by remember { mutableFloatStateOf(0f) }
    var boltXFrac         by remember { mutableFloatStateOf(0.5f) }
    var boltVisible       by remember { mutableStateOf(false) }

    LaunchedEffect(isNight, nightWeather) {
        thunderFlashAlpha = 0f
        boltVisible       = false
        if (isNight && nightWeather == NightWeather.WIND_THUNDER) {
            while (true) {
                // 1–3 second gap between lightning strikes
                delay(Random.nextLong(1_000L, 3_000L))
                if (!isNight || nightWeather != NightWeather.WIND_THUNDER) break

                // Choose bolt X position
                boltXFrac = 0.15f + Random.nextFloat() * 0.70f

                // First flash (bright)
                thunderFlashAlpha = 0.38f
                boltVisible       = true
                delay(85L)
                thunderFlashAlpha = 0f
                boltVisible       = false
                delay(130L)

                // Double-flash (dimmer)
                thunderFlashAlpha = 0.20f
                boltVisible       = true
                delay(55L)
                thunderFlashAlpha = 0f
                boltVisible       = false
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(modifier = modifier) {
        // ── Background images ────────────────────────────────────────────────
        if (dayAlpha > 0.01f) {
            androidx.compose.foundation.Image(
                painter            = painterResource(R.drawable.ghibli_road_day),
                contentDescription = "Day countryside scene",
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop,
                alpha              = dayAlpha,
            )
        }
        if (nightAlpha > 0.01f) {
            androidx.compose.foundation.Image(
                painter            = painterResource(R.drawable.ghibli_road_night),
                contentDescription = "Night countryside scene",
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop,
                alpha              = nightAlpha,
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // ════════════════════════════════════════════════════════════════
            // DAY EFFECTS
            // ════════════════════════════════════════════════════════════════
            if (dayAlpha > 0.01f) {

                // ── Clouds ───────────────────────────────────────────────────
                drawCloud(cx = w * cloud1, cy = h * 0.07f, scale = 1.4f, alpha = dayAlpha * 0.82f)
                drawCloud(cx = w * cloud2, cy = h * 0.13f, scale = 1.1f, alpha = dayAlpha * 0.78f)
                drawCloud(cx = w * cloud3, cy = h * 0.05f, scale = 0.9f, alpha = dayAlpha * 0.70f)
                drawCloud(cx = w * cloud4, cy = h * 0.18f, scale = 1.3f, alpha = dayAlpha * 0.75f)
                drawCloud(cx = w * cloud5, cy = h * 0.22f, scale = 0.8f, alpha = dayAlpha * 0.65f)
                drawCloud(cx = w * cloud6, cy = h * 0.10f, scale = 1.0f, alpha = dayAlpha * 0.72f)

                // ── Birds ────────────────────────────────────────────────────
                drawBird(cx = w * bird1, cy = h * 0.13f + sin(bird1 * 2f * PI.toFloat()) * h * 0.025f, scale = 2.2f, wingPhase = wingFlapSlow,         alpha = dayAlpha * 0.92f)
                drawBird(cx = w * bird2, cy = h * 0.08f + sin(bird2 * 2f * PI.toFloat()) * h * 0.018f, scale = 1.8f, wingPhase = -wingFlapSlow,        alpha = dayAlpha * 0.88f)
                drawBird(cx = w * bird3, cy = h * 0.18f + sin(bird3 * 3f * PI.toFloat()) * h * 0.020f, scale = 1.6f, wingPhase = wingFlapSlow * 0.9f,  alpha = dayAlpha * 0.85f)
                drawBird(cx = w * bird4, cy = h * 0.10f + sin(bird4 * 2f * PI.toFloat()) * h * 0.022f, scale = 1.3f, wingPhase = wingFlapFast,         alpha = dayAlpha * 0.80f)
                drawBird(cx = w * bird5, cy = h * 0.15f + sin(bird5 * 2f * PI.toFloat()) * h * 0.015f, scale = 1.1f, wingPhase = -wingFlapFast,        alpha = dayAlpha * 0.78f)
                drawBird(cx = w * bird6, cy = h * 0.21f + sin(bird6 * 2f * PI.toFloat()) * h * 0.018f, scale = 1.0f, wingPhase = wingFlapFast * 0.8f,  alpha = dayAlpha * 0.75f)
                drawBird(cx = w * bird7,               cy = h * 0.12f + sin(bird7 * 4f * PI.toFloat()) * h * 0.012f, scale = 0.75f, wingPhase = wingFlapFast,       alpha = dayAlpha * 0.65f)
                drawBird(cx = w * bird7 - w * 0.05f,   cy = h * 0.14f,                                               scale = 0.65f, wingPhase = -wingFlapFast,      alpha = dayAlpha * 0.55f)
                drawBird(cx = w * bird8,               cy = h * 0.20f + sin(bird8 * 3f * PI.toFloat()) * h * 0.010f, scale = 0.60f, wingPhase = wingFlapFast * 0.7f, alpha = dayAlpha * 0.60f)
                drawBird(cx = w * bird8 + w * 0.04f,   cy = h * 0.22f,                                               scale = 0.50f, wingPhase = -wingFlapFast,      alpha = dayAlpha * 0.50f)
                drawBird(cx = w * bird1 - w * 0.06f,   cy = h * 0.11f,                                               scale = 0.70f, wingPhase = wingFlapFast,       alpha = dayAlpha * 0.55f)

                // ── Day Rain (starts after 2–3 min) ──────────────────────────
                if (dayRainAlpha > 0.01f) {
                    drawRain(
                        seeds  = dayRainSeeds,
                        phase  = rainPhase,
                        alpha  = dayAlpha * dayRainAlpha,
                        // Soft blue-grey day rain
                        color  = Color(0xFF9BBBD4),
                    )
                }
            }

            // ════════════════════════════════════════════════════════════════
            // NIGHT EFFECTS
            // ════════════════════════════════════════════════════════════════
            if (nightAlpha > 0.01f) {

                // ── Fireflies ────────────────────────────────────────────────
                data class FF(val xF: Float, val yF: Float, val dm: Float, val dm2: Float, val dm3: Float, val a: Float, val size: Float = 3.5f)
                listOf(
                    FF(0.07f, 0.70f,  0.6f, -0.4f,  0.3f, ff1),
                    FF(0.12f, 0.78f, -0.8f,  0.5f, -0.4f, ff2),
                    FF(0.18f, 0.65f,  1.0f, -0.3f,  0.7f, ff3),
                    FF(0.25f, 0.73f, -0.5f,  0.7f, -0.5f, ff4),
                    FF(0.30f, 0.68f,  0.7f, -0.6f,  0.3f, ff5),
                    FF(0.37f, 0.76f, -0.9f,  0.4f, -0.6f, ff6),
                    FF(0.42f, 0.62f,  0.4f, -0.8f,  0.5f, ff7),
                    FF(0.48f, 0.71f, -0.6f,  0.3f, -0.3f, ff8),
                    FF(0.54f, 0.66f,  0.8f, -0.5f,  0.6f, ff9),
                    FF(0.60f, 0.75f, -0.7f,  0.6f, -0.7f, ff10),
                    FF(0.66f, 0.68f,  0.5f, -0.4f,  0.4f, ff1),
                    FF(0.72f, 0.73f, -0.8f,  0.7f, -0.5f, ff3),
                    FF(0.78f, 0.64f,  0.6f, -0.3f,  0.8f, ff5),
                    FF(0.84f, 0.72f, -0.4f,  0.5f, -0.6f, ff7),
                    FF(0.91f, 0.67f,  0.9f, -0.7f,  0.3f, ff9),
                    FF(0.05f, 0.58f,  0.3f, -0.5f,  0.4f, ff2,  2.8f),
                    FF(0.22f, 0.55f, -0.6f,  0.3f, -0.3f, ff4,  3.0f),
                    FF(0.39f, 0.60f,  0.5f, -0.4f,  0.6f, ff6,  2.5f),
                    FF(0.55f, 0.56f, -0.8f,  0.6f, -0.5f, ff8,  2.8f),
                    FF(0.74f, 0.59f,  0.4f, -0.3f,  0.7f, ff10, 3.2f),
                    FF(0.88f, 0.54f, -0.5f,  0.7f, -0.4f, ff2,  2.6f),
                    FF(0.14f, 0.45f,  0.7f, -0.3f,  0.5f, ff1,  2.0f),
                    FF(0.33f, 0.42f, -0.4f,  0.5f, -0.6f, ff5,  1.8f),
                    FF(0.62f, 0.48f,  0.6f, -0.6f,  0.4f, ff3,  2.2f),
                    FF(0.80f, 0.44f, -0.5f,  0.4f, -0.3f, ff7,  1.9f),
                ).forEach { ff ->
                    val px = w * ff.xF + ffDrift * ff.dm + ffDrift2 * ff.dm2 + ffDrift3 * ff.dm3
                    val py = h * ff.yF + ffDrift * ff.dm * 0.35f + ffDrift2 * ff.dm2 * 0.2f
                    val a  = (nightAlpha * ff.a * 0.95f).coerceIn(0f, 1f)
                    val r  = ff.size
                    drawCircle(Color(0xFF90FF90).copy(alpha = a),         radius = r,        center = Offset(px, py))
                    drawCircle(Color(0xFF78FF78).copy(alpha = a * 0.28f), radius = r * 3.2f, center = Offset(px, py))
                    drawCircle(Color.White.copy(alpha = a * 0.70f),       radius = r * 0.4f, center = Offset(px, py))
                }

                // ── Bats ─────────────────────────────────────────────────────
                val batYBase  = h * 0.25f
                val swoopAmt  = h * 0.06f
                drawBat(cx = w * bat1X, cy = batYBase          + sin(bat1X * 4f * PI.toFloat()) * swoopAmt, scale = 1.2f, wingPhase = batFlapFast,         alpha = nightAlpha * 0.85f)
                drawBat(cx = w * bat2X, cy = batYBase * 0.80f  + sin(bat2X * 5f * PI.toFloat()) * swoopAmt, scale = 0.9f, wingPhase = -batFlapFast,        alpha = nightAlpha * 0.75f)
                drawBat(cx = w * bat3X, cy = batYBase * 1.10f  + sin(bat3X * 3f * PI.toFloat()) * swoopAmt, scale = 1.0f, wingPhase = batFlapFast * 0.8f,  alpha = nightAlpha * 0.80f)
                drawBat(cx = w * bat4X, cy = batYBase * 0.65f  + sin(bat4X * 6f * PI.toFloat()) * swoopAmt, scale = 0.7f, wingPhase = -batFlapFast,        alpha = nightAlpha * 0.70f)
                drawBat(cx = w * bat5X, cy = batYBase * 0.90f  + sin(bat5X * 4f * PI.toFloat()) * swoopAmt, scale = 1.1f, wingPhase = batFlapFast * 0.9f,  alpha = nightAlpha * 0.78f)

                // ── Night Rain ────────────────────────────────────────────────
                if (nightRainAlpha > 0.01f) {
                    drawRain(
                        seeds = nightRainSeeds,
                        phase = rainPhase,
                        alpha = nightAlpha * nightRainAlpha,
                        color = Color(0xFF607898),
                    )
                }

                // ── Wind streaks ──────────────────────────────────────────────
                if (nightWindAlpha > 0.01f) {
                    drawWindStreaks(
                        seeds = windSeeds,
                        phase = windPhase,
                        alpha = nightAlpha * nightWindAlpha,
                    )
                }

                // ── Lightning bolt + flash overlay ────────────────────────────
                if (thunderFlashAlpha > 0.001f) {
                    // Full-scene white flash
                    drawRect(
                        color  = Color.White.copy(alpha = thunderFlashAlpha * nightAlpha),
                        size   = size,
                    )
                    // Jagged lightning bolt (mandatory)
                    if (boltVisible) {
                        drawLightningBolt(
                            cx     = w * boltXFrac,
                            yTop   = h * 0.01f,
                            yBot   = h * 0.62f,
                            alpha  = nightAlpha * (thunderFlashAlpha / 0.38f),
                        )
                    }
                }
            }
        }

        // ── Tap sky to toggle day/night ───────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .clickable(interactionSource = interactionSource, indication = null) { onToggle?.invoke() }
        )
    }
}

// ── Fluffy cloud (3 overlapping ovals + soft shadow) ─────────────────────────
private fun DrawScope.drawCloud(cx: Float, cy: Float, scale: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    val base  = Color.White.copy(alpha = alpha * 0.88f)
    val shade = Color(0xFF8899BB).copy(alpha = alpha * 0.10f)
    val s     = scale

    // Bottom shadow strip
    drawOval(shade, topLeft = Offset(cx - s * 48f, cy + s * 10f), size = Size(s * 96f, s * 20f))
    // Three cloud puffs
    drawOval(base,  topLeft = Offset(cx - s * 50f, cy - s * 18f), size = Size(s * 80f, s * 44f))
    drawOval(base,  topLeft = Offset(cx - s * 22f, cy - s * 32f), size = Size(s * 68f, s * 52f))
    drawOval(base,  topLeft = Offset(cx + s * 12f, cy - s * 16f), size = Size(s * 55f, s * 38f))
}

// ── Rain drops ────────────────────────────────────────────────────────────────
private fun DrawScope.drawRain(seeds: List<RainSeed>, phase: Float, alpha: Float, color: Color) {
    if (alpha <= 0.01f) return
    val w = size.width
    val h = size.height + 30f
    seeds.forEach { seed ->
        val yNorm = (phase * seed.spd + seed.yOff) % 1f
        val y     = yNorm * h - 10f
        val x     = seed.xFrac * size.width + y * 0.16f  // slight right-lean
        drawLine(
            color       = color.copy(alpha = alpha * seed.a),
            start       = Offset(x, y),
            end         = Offset(x + seed.len * 0.16f, y + seed.len),
            strokeWidth = 1.3f,
            cap         = StrokeCap.Round,
        )
    }
}

// ── Wind streaks (near-horizontal, fast sweep) ────────────────────────────────
private fun DrawScope.drawWindStreaks(seeds: List<WindSeed>, phase: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    val w = size.width
    val h = size.height
    seeds.forEach { seed ->
        val xNorm = (phase * seed.spd + seed.xFrac) % 1f
        val x     = xNorm * (w + seed.len) - seed.len
        val y     = seed.yFrac * h
        drawLine(
            color       = Color(0xFF99AABB).copy(alpha = alpha * seed.a),
            start       = Offset(x, y),
            end         = Offset(x + seed.len, y + seed.len * 0.06f),
            strokeWidth = 1.0f,
            cap         = StrokeCap.Round,
        )
    }
}

// ── Lightning bolt — jagged zigzag from sky top to 62 % height ───────────────
private fun DrawScope.drawLightningBolt(cx: Float, yTop: Float, yBot: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    val totalH = yBot - yTop

    // Pre-defined deterministic zigzag offsets (scaled by cx so bolt stays on screen)
    val pts = listOf(
        Offset(cx,          yTop),
        Offset(cx + 22f,    yTop + totalH * 0.13f),
        Offset(cx - 14f,    yTop + totalH * 0.26f),
        Offset(cx + 30f,    yTop + totalH * 0.42f),
        Offset(cx - 8f,     yTop + totalH * 0.57f),
        Offset(cx + 18f,    yTop + totalH * 0.72f),
        Offset(cx + 4f,     yBot),
    )

    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
    }
    val strokeStyle   = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val glowStyle     = Stroke(width = 14f,  cap = StrokeCap.Round, join = StrokeJoin.Round)
    val midGlowStyle  = Stroke(width = 6f,   cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Outer glow (wide, very dim)
    drawPath(path, Color(0xFFFFFF88).copy(alpha = alpha * 0.18f), style = glowStyle)
    // Mid glow
    drawPath(path, Color(0xFFFFFFCC).copy(alpha = alpha * 0.35f), style = midGlowStyle)
    // Core bolt (bright white)
    drawPath(path, Color.White.copy(alpha = (alpha * 0.95f).coerceIn(0f, 1f)), style = strokeStyle)
}

// ── Bird silhouette ───────────────────────────────────────────────────────────
private fun DrawScope.drawBird(cx: Float, cy: Float, scale: Float, wingPhase: Float, alpha: Float) {
    val span      = scale * 14f
    val drop      = wingPhase * span * 0.50f
    val birdColor = Color(0xFF0D1810).copy(alpha = (alpha * 0.90f).coerceIn(0f, 1f))
    val stroke    = Stroke(width = scale * 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    val left = Path().apply {
        moveTo(cx, cy)
        cubicTo(cx - span * 0.40f, cy - drop * 0.5f, cx - span * 0.80f, cy - drop, cx - span * 1.30f, cy - drop + span * 0.20f)
    }
    val right = Path().apply {
        moveTo(cx, cy)
        cubicTo(cx + span * 0.40f, cy - drop * 0.5f, cx + span * 0.80f, cy - drop, cx + span * 1.30f, cy - drop + span * 0.20f)
    }
    drawPath(left,  birdColor, style = stroke)
    drawPath(right, birdColor, style = stroke)
}

// ── Bat silhouette ────────────────────────────────────────────────────────────
private fun DrawScope.drawBat(cx: Float, cy: Float, scale: Float, wingPhase: Float, alpha: Float) {
    val span     = scale * 12f
    val lift     = (wingPhase + 1f) * 0.5f
    val tipDrop  = lift * span * 0.6f - span * 0.2f
    val midLift  = lift * span * 0.3f - span * 0.1f
    val batColor = Color(0xFF1A0A2E).copy(alpha = (alpha * 0.88f).coerceIn(0f, 1f))
    val stroke   = Stroke(width = scale * 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    val left = Path().apply {
        moveTo(cx, cy)
        lineTo(cx - span * 0.35f, cy - midLift)
        lineTo(cx - span * 1.15f, cy + tipDrop)
        cubicTo(cx - span * 0.9f, cy + tipDrop * 0.6f, cx - span * 0.55f, cy + tipDrop * 0.2f, cx, cy + span * 0.15f)
    }
    val right = Path().apply {
        moveTo(cx, cy)
        lineTo(cx + span * 0.35f, cy - midLift)
        lineTo(cx + span * 1.15f, cy + tipDrop)
        cubicTo(cx + span * 0.9f, cy + tipDrop * 0.6f, cx + span * 0.55f, cy + tipDrop * 0.2f, cx, cy + span * 0.15f)
    }
    drawPath(left,  batColor, style = stroke)
    drawPath(right, batColor, style = stroke)
    drawCircle(batColor, radius = scale * 2.2f, center = Offset(cx, cy))
}
