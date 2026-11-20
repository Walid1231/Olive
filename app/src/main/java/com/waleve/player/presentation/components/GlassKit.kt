package com.waleve.player.presentation.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.draw.blur
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
import com.waleve.player.presentation.theme.GlassMode
import com.waleve.player.presentation.theme.GlassFill
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
fun GlassBackground(modifier: Modifier = Modifier, artwork: String? = null, content: @Composable BoxScope.() -> Unit) {
    val dark = GlassMode.dark
    val mint = GlassBlobMint; val lilac = GlassBlobLilac; val sky = GlassBlobSky; val peach = GlassBlobPeach
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        if (dark) listOf(Color(0xFF0C0F18), Color(0xFF151226), Color(0xFF0B1717))
                        else listOf(Color(0xFFEEF2FA), Color(0xFFF7F1F6), Color(0xFFEEF6F2)),
                        start = Offset.Zero, end = Offset(size.width, size.height),
                    )
                )
                fun blob(c: Color, a: Float, cx: Float, cy: Float, r: Float) = drawCircle(
                    Brush.radialGradient(listOf(c.copy(alpha = a), c.copy(alpha = 0f)), center = Offset(cx, cy), radius = r),
                    radius = r, center = Offset(cx, cy),
                )
                val w = size.width; val h = size.height; val r = w * 0.75f
                val k = if (dark) 0.55f else 1f
                blob(mint, 0.75f * k, w * 0.05f, h * 0.06f, r)
                blob(lilac, 0.65f * k, w * 0.95f, h * 0.30f, r)
                blob(sky, 0.6f * k, w * 0.0f, h * 0.70f, r)
                blob(peach, 0.6f * k, w * 0.95f, h * 0.88f, r)
            },
    ) {
        // The playing song's cover, hugely blurred, glowing through every glass card.
        // Loaded tiny (48 px) and stretched → a soft blur on every Android version;
        // Android 12+ adds a real blur on top. Cross-fades when the song changes.
        androidx.compose.animation.Crossfade(
            targetState = artwork,
            animationSpec = androidx.compose.animation.core.tween(900),
            label = "bgArt",
        ) { art ->
            if (art != null) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Box(Modifier.fillMaxSize()) {
                    coil.compose.AsyncImage(
                        model = remember(art) {
                            coil.request.ImageRequest.Builder(ctx).data(art).size(48).allowHardware(false).crossfade(false).build()
                        },
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { scaleX = 1.35f; scaleY = 1.35f; alpha = if (dark) 0.85f else 0.9f }
                            .then(
                                if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(48.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
                                else Modifier
                            ),
                    )
                    // milky (light) / smoky (dark) wash so text stays readable on any cover
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                if (dark) listOf(Color(0xFF0C0F18).copy(alpha = 0.62f), Color(0xFF0C0F18).copy(alpha = 0.42f), Color(0xFF0C0F18).copy(alpha = 0.52f), Color(0xFF0C0F18).copy(alpha = 0.72f))
                                else listOf(Color.White.copy(alpha = 0.64f), Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.56f), Color.White.copy(alpha = 0.70f))
                            )
                        )
                    )
                    // a couple of soft light streaks (like the glassmorphism reference)
                    Box(
                        Modifier.fillMaxSize().drawBehind {
                            val w = size.width; val h = size.height
                            drawCircle(
                                Brush.radialGradient(listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.55f), Color.Transparent), center = Offset(w * 0.15f, h * 0.12f), radius = w * 0.6f),
                                radius = w * 0.6f, center = Offset(w * 0.15f, h * 0.12f),
                            )
                            drawCircle(
                                Brush.radialGradient(listOf(Color.White.copy(alpha = if (dark) 0.06f else 0.35f), Color.Transparent), center = Offset(w * 0.9f, h * 0.75f), radius = w * 0.55f),
                                radius = w * 0.55f, center = Offset(w * 0.9f, h * 0.75f),
                            )
                        }
                    )
                }
            }
        }
        content()
    }
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
val GlassEdge: Brush get() =
    if (GlassMode.dark) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.14f)))
    else Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.9f)))

/** Frosted-glass surface: translucent white + bright hairline + soft shadow. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(22.dp),
    alpha: Float = 0.62f,
    elevation: Dp = 0.dp,
    border: Boolean = true,
): Modifier = this
    .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, ambientColor = Color(0x1423305A), spotColor = Color(0x1423305A)) else Modifier)
    .clip(shape)
    .background(GlassFill.copy(alpha = if (GlassMode.dark) (alpha * 0.92f).coerceAtMost(0.9f) else alpha))
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
fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, count: Int? = null, icon: ImageVector? = null) {
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
        icon?.let { Icon(it, null, tint = if (selected) Color.White else GlassAccent, modifier = Modifier.size(16.dp)) }
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
 * One line of text that always shows in full: if it doesn't fit (big system font, small phone),
 * it shrinks a little at a time instead of being cut off.
 */
@Composable
fun FitText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    minFontSize: androidx.compose.ui.unit.TextUnit = 9.sp,
) {
    var size by remember(text, fontSize) { androidx.compose.runtime.mutableStateOf(fontSize) }
    var ready by remember(text, fontSize) { androidx.compose.runtime.mutableStateOf(false) }
    Text(
        text,
        color = color,
        fontSize = size,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        onTextLayout = { r ->
            if (r.didOverflowWidth && size.value > minFontSize.value) size = (size.value * 0.92f).sp
            else ready = true
        },
    )
}

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
                        }
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    opt.icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)) }
                    FitText(opt.label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f, fill = false))
                    opt.count?.takeIf { it > 0 }?.let { c ->
                        Spacer(Modifier.width(5.dp))
                        Box(
                            Modifier.heightIn(min = 18.dp).widthIn(min = 18.dp).clip(CircleShape)
                                .background(if (on) Color.White.copy(alpha = 0.28f) else GlassAccent.copy(alpha = 0.16f))
                                .padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (c > 99) "99+" else c.toString(),
                                color = if (on) Color.White else GlassAccent,
                                fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, maxLines = 1, softWrap = false,
                            )
                        }
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
            .background(if (enabled) GlassGradientH else Brush.horizontalGradient(if (GlassMode.dark) listOf(Color(0xFF3A4152), Color(0xFF2E3443)) else listOf(Color(0xFFC9CED8), Color(0xFFB8BECB))))
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
                style = androidx.compose.ui.text.TextStyle(fontFamily = com.waleve.player.presentation.theme.OutfitFamily, shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 4f)),
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
