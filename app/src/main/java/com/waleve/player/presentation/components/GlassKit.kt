package com.waleve.player.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassAccentDeep
import com.waleve.player.presentation.theme.GlassAccentLight
import com.waleve.player.presentation.theme.GlassBlobLilac
import com.waleve.player.presentation.theme.GlassBlobMint
import com.waleve.player.presentation.theme.GlassBlobPeach
import com.waleve.player.presentation.theme.GlassBlobSky
import com.waleve.player.presentation.theme.GlassCard
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassMuted
import com.waleve.player.presentation.theme.GlassStroke
import com.waleve.player.presentation.theme.GlassSub

/*
 * Olive light-glass kit.
 *
 * Performance note: real backdrop blur (RenderEffect) is expensive on Android and only exists on API 31+.
 * The background here is a soft gradient with radial "blobs", so a translucent white card with a bright
 * hairline border reads as frosted glass on every device — at almost zero cost.
 */

val GlassGradient: Brush = Brush.linearGradient(listOf(GlassAccentLight, GlassAccentDeep))
val GlassGradientH: Brush = Brush.horizontalGradient(listOf(GlassAccentLight, GlassAccentDeep))

/** App-wide background: light gradient + four soft colour blobs (drawn once per frame, no blur pass). */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        listOf(Color(0xFFEEF2FA), Color(0xFFF7F1F6), Color(0xFFEEF6F2)),
                        start = Offset.Zero, end = Offset(size.width, size.height),
                    )
                )
                fun blob(c: Color, a: Float, cx: Float, cy: Float, r: Float) = drawCircle(
                    Brush.radialGradient(listOf(c.copy(alpha = a), c.copy(alpha = 0f)), center = Offset(cx, cy), radius = r),
                    radius = r, center = Offset(cx, cy),
                )
                val w = size.width; val h = size.height; val r = w * 0.75f
                blob(GlassBlobMint, 0.75f, w * 0.05f, h * 0.06f, r)
                blob(GlassBlobLilac, 0.65f, w * 0.95f, h * 0.30f, r)
                blob(GlassBlobSky, 0.6f, w * 0.0f, h * 0.70f, r)
                blob(GlassBlobPeach, 0.6f, w * 0.95f, h * 0.88f, r)
            },
        content = content,
    )
}

/**
 * The navigation-dock feel for every tappable thing: a springy press (shrinks a little and
 * bounces back) plus a light haptic tick. No grey ripple, so glass stays clean.
 */
fun Modifier.springClick(
    enabled: Boolean = true,
    haptic: Boolean = true,
    pressedScale: Float? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // Small things squeeze more, big cards / rows only a touch (same ~6 dp travel for all)
    var sizePx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val target = pressedScale ?: run {
        if (sizePx <= 0) 0.95f else (1f - with(density) { 6.dp.toPx() } / sizePx).coerceIn(0.92f, 0.985f)
    }
    val scale by animateFloatAsState(
        if (pressed && enabled) target else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    val hf = LocalHapticFeedback.current
    this
        .onSizeChanged { sizePx = maxOf(it.width, it.height) }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled) {
            if (haptic) hf.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Bright top-left → soft bottom-right hairline: the "lit edge" of real glass. */
val GlassEdge: Brush = Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.9f)))

/** Frosted-glass surface: translucent white + bright hairline + soft shadow. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(22.dp),
    alpha: Float = 0.62f,
    elevation: Dp = 0.dp,
    border: Boolean = true,
): Modifier = this
    .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, ambientColor = Color(0x1423305A), spotColor = Color(0x1423305A)) else Modifier)
    .clip(shape)
    .background(Color.White.copy(alpha = alpha))
    .then(if (border) Modifier.border(1.dp, GlassEdge, shape) else Modifier)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    alpha: Float = 0.62f,
    padding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.springClick(pressedScale = 0.98f, haptic = false, onClick = onClick) else Modifier)
            .glass(shape, alpha)
            .padding(padding),
        content = content,
    )
}

@Composable
fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, count: Int? = null) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .springClick(onClick = onClick)
            .then(
                if (selected) Modifier.shadow(6.dp, shape, ambientColor = GlassAccent, spotColor = GlassAccent).clip(shape).background(GlassGradientH)
                else Modifier.glass(shape, alpha = 0.6f, elevation = 0.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = if (selected) Color.White else GlassSub, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        if (count != null) {
            Text(
                "$count",
                color = if (selected) Color.White else GlassMuted,
                fontWeight = FontWeight.Bold, fontSize = 11.sp,
            )
        }
    }
}

data class SegmentOption(val label: String, val icon: ImageVector? = null, val count: Int? = null)

/**
 * Glass segmented control (Songs | Audiobooks, Inbox | Friends | Requests …).
 * One gradient pill slides under the chosen option. Only its position animates (no fading
 * between two pills), so nothing flickers when switching.
 */
@Composable
fun GlassSegmented(options: List<SegmentOption>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = 46.dp) {
    val hf = LocalHapticFeedback.current
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(height / 2 + 4.dp), alpha = 0.6f, elevation = 0.dp)
            .padding(4.dp),
    ) {
        val n = options.size.coerceAtLeast(1)
        val itemW = maxWidth / n
        val x by androidx.compose.animation.core.animateDpAsState(
            itemW * selected.coerceIn(0, n - 1),
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
            label = "segX",
        )
        // the sliding pill
        Box(
            Modifier
                .offset(x = x)
                .width(itemW).height(height)
                .shadow(8.dp, RoundedCornerShape(height / 2), ambientColor = GlassAccent, spotColor = GlassAccent)
                .clip(RoundedCornerShape(height / 2))
                .background(GlassGradientH)
        )
        Row(Modifier.fillMaxWidth().height(height)) {
            options.forEachIndexed { i, opt ->
                val on = i == selected
                val fg by animateColorAsState(if (on) Color.White else GlassSub, tween(220), label = "segFg")
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(height / 2))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !on) {
                            hf.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    opt.icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
                    Text(opt.label, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    opt.count?.let { c ->
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (on) Color.White.copy(alpha = 0.25f) else GlassInk.copy(alpha = 0.06f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) { Text("$c", color = fg, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}

/** Primary pill button with the mint→teal gradient. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 44.dp,
) {
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier = modifier
            .springClick(enabled = enabled && !loading, onClick = onClick)
            .height(height)
            .then(if (enabled) Modifier.shadow(8.dp, shape, ambientColor = GlassAccent, spotColor = GlassAccent) else Modifier)
            .clip(shape)
            .background(if (enabled) GlassGradientH else Brush.horizontalGradient(listOf(Color(0xFFC9CED8), Color(0xFFB8BECB))))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
    }
}

/** Secondary glass pill button. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = GlassInk,
    enabled: Boolean = true,
    height: Dp = 44.dp,
) {
    Row(
        modifier = modifier
            .springClick(enabled = enabled, onClick = onClick)
            .height(height)
            .glass(RoundedCornerShape(height / 2), alpha = if (enabled) 0.8f else 0.45f, elevation = 0.dp)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val t = if (enabled) tint else tint.copy(alpha = 0.4f)
        if (icon != null) { Icon(icon, null, tint = t, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = t, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1)
    }
}

@Composable
fun GlassIconButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = GlassInk, contentDescription: String? = null) {
    Box(
        modifier = modifier
            .springClick(pressedScale = 0.88f, onClick = onClick)
            .size(size)
            .glass(CircleShape, alpha = 0.72f, elevation = 0.dp),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.48f)) }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, color = GlassAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.springClick(onClick = onAction))
        }
    }
}

/** Round avatar: photo if there is one, otherwise the first letter on a gradient. */
@Composable
fun Avatar(url: String?, name: String, size: Dp = 44.dp, modifier: Modifier = Modifier, borderWidth: Dp = 2.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(GlassAccentLight, Color(0xFF7B6CF0))))
            .border(borderWidth, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.4f).sp,
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
        }
    }
}

/** Thin rounded progress bar with the brand gradient. */
@Composable
fun GlassProgress(fraction: Float, modifier: Modifier = Modifier, height: Dp = 5.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(GlassInk.copy(alpha = 0.08f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(height)).background(GlassGradientH))
    }
}

/** Artwork with a soft gradient + note fallback when there's no cover. */
@Composable
fun Artwork(model: Any?, modifier: Modifier = Modifier, corner: Dp = 14.dp, seed: Int = 0) {
    val palettes = listOf(
        listOf(Color(0xFF7FD1AE), Color(0xFF2E8C7A)), listOf(Color(0xFFFFB38A), Color(0xFFE0607E)),
        listOf(Color(0xFFB9A6FF), Color(0xFF6A5ACD)), listOf(Color(0xFF7CC8F8), Color(0xFF3466C2)),
        listOf(Color(0xFFC6DB7C), Color(0xFF5E8C3A)),
    )
    val p = palettes[(seed and 0x7fffffff) % palettes.size]
    Box(
        modifier = modifier.clip(RoundedCornerShape(corner)).background(Brush.linearGradient(p)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.MusicNote, null,
            tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.fillMaxSize(0.42f),
        )
        if (model != null && model.toString().isNotBlank()) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Small uppercase overline label. */
@Composable
fun Overline(text: String, color: Color = GlassAccent) {
    Text(text.uppercase(), color = color, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}


/**
 * Photo card: artwork fills the card, a dark gradient rises from the bottom and the text sits on it
 * in WHITE — readable on any cover (bright, dark or busy).
 */
@Composable
fun PhotoCard(
    model: Any?,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    seed: Int = title.hashCode(),
    corner: Dp = 20.dp,
    progress: Float? = null,
    onClick: (() -> Unit)? = null,
    topEnd: (@Composable BoxScope.() -> Unit)? = null,
    topStart: (@Composable BoxScope.() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.springClick(pressedScale = 0.96f, onClick = onClick) else Modifier)
            .shadow(10.dp, RoundedCornerShape(corner), ambientColor = Color(0x3323305A), spotColor = Color(0x3323305A))
            .clip(RoundedCornerShape(corner))
            .border(1.dp, GlassEdge, RoundedCornerShape(corner)),
    ) {
        Artwork(model, Modifier.fillMaxSize(), corner = 0.dp, seed = seed)
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.62f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.82f))))
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 11.dp)) {
            Text(
                title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, lineHeight = 17.sp,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 4f)),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (progress != null) {
                Spacer(Modifier.height(7.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
                }
            }
        }
        topEnd?.let { Box(Modifier.align(Alignment.TopEnd)) { it() } }
        topStart?.let { Box(Modifier.align(Alignment.TopStart)) { it() } }
    }
}
