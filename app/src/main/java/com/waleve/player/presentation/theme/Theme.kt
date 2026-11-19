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

// ── Dark glass Material scheme ───────────────────────
private val GlassDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4CC795), onPrimary = Color(0xFF0B1A14),
    primaryContainer = Color(0xFF1C3B30), onPrimaryContainer = Color(0xFFEEF1F8),
    secondary = Color(0xFF3FA6C2), onSecondary = Color(0xFF07141A),
    secondaryContainer = Color(0xFF1C2B3C), onSecondaryContainer = Color(0xFFEEF1F8),
    tertiary = Color(0xFF9D8FFF), onTertiary = Color(0xFF120D2B),
    background = Color(0xFF11141C), onBackground = Color(0xFFEEF1F8),
    surface = Color(0xFF161A24), onSurface = Color(0xFFEEF1F8),
    surfaceVariant = Color(0xFF232838), onSurfaceVariant = Color(0xFFB0B8CB),
    surfaceContainer = Color(0xFF161A24), surfaceContainerHigh = Color(0xFF1E2230), surfaceContainerLow = Color(0xFF131720),
    error = Color(0xFFFF7A96), onError = Color(0xFF2A0710),
    outline = Color(0xFF3A4152), outlineVariant = Color(0xFF2A3040),
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
    // Every Glass* colour follows this switch
    if (GlassMode.dark != isNightMode) GlassMode.dark = isNightMode
    val ghibliColors = GhibliColors(isNight = isNightMode)
    val materialScheme = if (isNightMode) GlassDarkColorScheme else GlassColorScheme

    // Status / navigation bar icons: dark icons on light glass, light icons on dark glass
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isNightMode
                isAppearanceLightNavigationBars = !isNightMode
            }
        }
    }

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
