package com.waleve.player.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Light glass Material scheme ───────────────────────
private val GlassColorScheme = lightColorScheme(
    primary = GlassAccent, onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F2E5), onPrimaryContainer = GlassInk,
    secondary = Color(0xFF2E8FA8), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EEFF), onSecondaryContainer = GlassInk,
    tertiary = Color(0xFF7B6CF0), onTertiary = Color.White,
    background = GlassSurface, onBackground = GlassInk,
    surface = GlassSurface, onSurface = GlassInk,
    surfaceVariant = Color(0xFFF0EEF6), onSurfaceVariant = GlassSub,
    surfaceContainer = GlassSurface, surfaceContainerHigh = Color(0xFFF1EFF7), surfaceContainerLow = GlassSurface,
    error = Color(0xFFE0607E), onError = Color.White,
    outline = Color(0xFFD9DCE6), outlineVariant = Color(0xFFE8EAF1),
)

// ── Day (Light) Color Scheme ──────────────────────────
private val GhibliDayColorScheme = lightColorScheme(
    primary             = GhibliForestMid,
    onPrimary           = GhibliCream,
    primaryContainer    = GhibliForestLight,
    onPrimaryContainer  = GhibliForestDeep,
    secondary           = GhibliSkyBlue,
    onSecondary         = GhibliCream,
    secondaryContainer  = GhibliWave,
    onSecondaryContainer = GhibliForestDeep,
    tertiary            = GhibliSunset,
    onTertiary          = GhibliCream,
    background          = GhibliDayBg,
    onBackground        = GhibliDayText,
    surface             = GhibliDaySurface,
    onSurface           = GhibliDayText,
    surfaceVariant      = GhibliDayCard,
    onSurfaceVariant    = GhibliDayTextSub,
    error               = ErrorRed,
    onError             = GhibliCream,
    outline             = GhibliDayMuted,
    outlineVariant      = GhibliWave,
)

// ── Night (Dark) Color Scheme ─────────────────────────
private val GhibliNightColorScheme = darkColorScheme(
    primary             = GhibliNightAccent,
    onPrimary           = GhibliNightDeep,
    primaryContainer    = GhibliNightCard,
    onPrimaryContainer  = GhibliNightText,
    secondary           = GhibliMoonGlow,
    onSecondary         = GhibliNightDeep,
    secondaryContainer  = GhibliNightMid,
    onSecondaryContainer = GhibliNightText,
    tertiary            = GhibliFirefly,
    onTertiary          = GhibliNightDeep,
    background          = GhibliNightDeep,
    onBackground        = GhibliNightText,
    surface             = GhibliNightMid,
    onSurface           = GhibliNightText,
    surfaceVariant      = GhibliNightCard,
    onSurfaceVariant    = GhibliNightSub,
    error               = ErrorRed,
    onError             = GhibliNightDeep,
    outline             = GhibliNightMuted,
    outlineVariant      = Color(0xFF1A3A5C),
)

@Immutable
data class GhibliColors(
    val isNight: Boolean = false,
    // Scene colors (legacy, kept so old code compiles)
    val skyTop: androidx.compose.ui.graphics.Color        = Color(0xFFEEF2FA),
    val skyBottom: androidx.compose.ui.graphics.Color     = Color(0xFFEEF6F2),
    val mountainFar: androidx.compose.ui.graphics.Color   = Color(0xFFC9B8FF),
    val mountainMid: androidx.compose.ui.graphics.Color   = Color(0xFF9EE6C6),
    val mountainNear: androidx.compose.ui.graphics.Color  = Color(0xFFA9D6FF),
    val ground: androidx.compose.ui.graphics.Color        = Color(0xFFFFCDB2),
    val road: androidx.compose.ui.graphics.Color          = Color(0xFFE6E1EE),
    val roadMark: androidx.compose.ui.graphics.Color      = Color(0xFFFFFFFF),
    val celestial: androidx.compose.ui.graphics.Color     = Color(0xFFFFF4C2),
    val starAlpha: Float                                  = 0f,
    val fireflyAlpha: Float                               = 0f,
    // ── Light glass UI ──
    val surface: androidx.compose.ui.graphics.Color       = GlassSurface,        // sheets, dialogs, detail screens
    val surfaceCard: androidx.compose.ui.graphics.Color   = GlassCard,           // translucent white "glass"
    val textPrimary: androidx.compose.ui.graphics.Color   = GlassInk,
    val textSecondary: androidx.compose.ui.graphics.Color = GlassSub,
    val textMuted: androidx.compose.ui.graphics.Color     = GlassMuted,
    val accent: androidx.compose.ui.graphics.Color        = GlassAccent,
    val accentLight: androidx.compose.ui.graphics.Color   = GlassAccentLight,
    val glassOverlay: androidx.compose.ui.graphics.Color  = GlassCard,
    val glassBorder: androidx.compose.ui.graphics.Color   = GlassStroke,
    val navBg: androidx.compose.ui.graphics.Color         = GlassCardStrong,
    val miniPlayerStart: androidx.compose.ui.graphics.Color = GlassCardStrong,
    val miniPlayerEnd: androidx.compose.ui.graphics.Color   = GlassCardStrong,
    val playerBgTop: androidx.compose.ui.graphics.Color   = Color(0xFFF7EEF2),
    val playerBgBottom: androidx.compose.ui.graphics.Color = Color(0xFFEEF6F2),
)

/** The app is light glass only now — night uses the same colours. */
val GhibliNightColors = GhibliColors()

val LocalGhibliColors = staticCompositionLocalOf { GhibliColors() }

@Composable
fun WaLeveTheme(
    isNightMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val ghibliColors = GhibliColors()
    val materialScheme = GlassColorScheme

    CompositionLocalProvider(LocalGhibliColors provides ghibliColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography  = WaLeveTypography,
            content     = content,
        )
    }
}

object WaLeveThemeAccess {
    val colors: GhibliColors
        @Composable get() = LocalGhibliColors.current
}

// Legacy compat
@Immutable
data class WaLeveColors(
    val navyDeep: androidx.compose.ui.graphics.Color    = NavyDeep,
    val navySurface: androidx.compose.ui.graphics.Color = NavySurface,
    val forestDark: androidx.compose.ui.graphics.Color  = ForestDark,
    val forestSurface: androidx.compose.ui.graphics.Color = ForestSurface,
    val olive: androidx.compose.ui.graphics.Color       = Olive,
    val oliveBright: androidx.compose.ui.graphics.Color = OliveBright,
    val cream: androidx.compose.ui.graphics.Color       = Cream,
    val sage: androidx.compose.ui.graphics.Color        = Sage,
    val sageMuted: androidx.compose.ui.graphics.Color   = SageMuted,
)
val LocalWaLeveColors = staticCompositionLocalOf { WaLeveColors() }
