package com.waleve.player.presentation.components

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The main colour of a cover, softened so it works as a gentle glow behind the light glass UI
 * (Now Playing background, mini player tint). Results are cached per cover.
 */
object CoverColors {
    private const val NO_COLOR = 0x00000001          // "looked, found nothing usable" (never a real result)
    private val cache = LruCache<String, Int>(96)

    fun cached(model: Any?): Color? {
        val key = model?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val v = cache.get(key) ?: return null
        return if (v == NO_COLOR) null else Color(v)
    }

    suspend fun of(context: Context, model: Any?): Color? {
        val key = model?.toString()?.takeIf { it.isNotBlank() } ?: return null
        cache.get(key)?.let { return if (it == NO_COLOR) null else Color(it) }
        val argb: Int? = withContext(Dispatchers.Default) {
            runCatching {
                val request = ImageRequest.Builder(context).data(model).size(48).allowHardware(false).build()
                val result = context.imageLoader.execute(request) as? SuccessResult
                    ?: return@runCatching null                     // failed load: don't cache, try again later
                val color = dominant(result.drawable.toBitmap(48, 48, Bitmap.Config.ARGB_8888))
                cache.put(key, color ?: NO_COLOR)
                color
            }.getOrNull()
        }
        return argb?.let { Color(it) }
    }

    /** Most "characteristic" colour: coarse histogram, favouring saturated mid-bright pixels. */
    private fun dominant(bmp: Bitmap): Int? {
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return null
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val buckets = HashMap<Int, FloatArray>()
        val hsv = FloatArray(3)
        var colourful = 0
        for (c in px) {
            if ((c ushr 24) < 200) continue
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val s = hsv[1]
            val v = hsv[2]
            if (v < 0.15f) continue                     // near black
            if (v > 0.95f && s < 0.10f) continue        // near white
            if (s > 0.25f) colourful++
            val weight = 0.1f + s * s * 2f + (1f - abs(v - 0.65f)) * 0.4f
            val key = ((r shr 5) shl 6) or ((g shr 5) shl 3) or (b shr 5)
            val acc = buckets.getOrPut(key) { FloatArray(4) }
            acc[0] += weight; acc[1] += r * weight; acc[2] += g * weight; acc[3] += b * weight
        }
        val best = buckets.values.maxByOrNull { it[0] } ?: return null
        val r = (best[1] / best[0]).toInt().coerceIn(0, 255)
        val g = (best[2] / best[0]).toInt().coerceIn(0, 255)
        val b = (best[3] / best[0]).toInt().coerceIn(0, 255)
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        // Soften: never muddy-dark or neon, grey covers stay grey-ish
        hsv[1] = if (colourful < px.size / 20) hsv[1].coerceAtMost(0.18f) else hsv[1].coerceIn(0.35f, 0.85f)
        hsv[2] = hsv[2].coerceIn(0.55f, 0.92f)
        return android.graphics.Color.HSVToColor(hsv)
    }
}

/** Cover colour for [model]; keeps the previous colour until the new one is ready (smooth song changes). */
@Composable
fun rememberCoverColor(model: Any?, fallback: Color): Color {
    val context = LocalContext.current
    val key = model?.toString()
    val state = remember { mutableStateOf(CoverColors.cached(model) ?: fallback) }
    LaunchedEffect(key) {
        state.value = CoverColors.of(context, model) ?: fallback
    }
    return state.value
}
