package com.waleve.player.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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
    // Scene colors for Canvas/SVG drawing
    val skyTop: androidx.compose.ui.graphics.Color        = GhibliSkyBlue,
    val skyBottom: androidx.compose.ui.graphics.Color     = GhibliSkyLight,
    val mountainFar: androidx.compose.ui.graphics.Color   = Color(0xFF7BAFC0),
    val mountainMid: androidx.compose.ui.graphics.Color   = Color(0xFF4A8073),
    val mountainNear: androidx.compose.ui.graphics.Color  = GhibliForestMid,
    val ground: androidx.compose.ui.graphics.Color        = Color(0xFF3A7D55),
    val road: androidx.compose.ui.graphics.Color          = Color(0xFF5C5048),
    val roadMark: androidx.compose.ui.graphics.Color      = Color(0xFFE8D5A3),
    val celestial: androidx.compose.ui.graphics.Color     = GhibliSunGold,
    val starAlpha: Float                                  = 0f,
    val fireflyAlpha: Float                               = 0f,
    // UI surface colors
    val surface: androidx.compose.ui.graphics.Color       = GhibliDaySurface,
    val surfaceCard: androidx.compose.ui.graphics.Color   = GhibliDayCard,
    val textPrimary: androidx.compose.ui.graphics.Color   = GhibliDayText,
    val textSecondary: androidx.compose.ui.graphics.Color = GhibliDayTextSub,
    val textMuted: androidx.compose.ui.graphics.Color     = GhibliDayMuted,
    val accent: androidx.compose.ui.graphics.Color        = GhibliForestMid,
    val accentLight: androidx.compose.ui.graphics.Color   = GhibliForestLight,
    val glassOverlay: androidx.compose.ui.graphics.Color  = GlassWhiteDay,
    val glassBorder: androidx.compose.ui.graphics.Color   = GlassBorderDay,
    val navBg: androidx.compose.ui.graphics.Color         = GhibliDaySurface,
    val miniPlayerStart: androidx.compose.ui.graphics.Color = GhibliForestMid,
    val miniPlayerEnd: androidx.compose.ui.graphics.Color   = GhibliSkyBlue,
    val playerBgTop: androidx.compose.ui.graphics.Color   = GhibliForestDeep,
    val playerBgBottom: androidx.compose.ui.graphics.Color = Color(0xFF0D2B1D),
)

val GhibliNightColors = GhibliColors(
    isNight        = true,
    skyTop         = GhibliNightSky,
    skyBottom      = GhibliNightDeep,
    mountainFar    = Color(0xFF0F1E35),
    mountainMid    = Color(0xFF112240),
    mountainNear   = Color(0xFF0A1628),
    ground         = Color(0xFF071020),
    road           = Color(0xFF1A1A2E),
    roadMark       = Color(0xFFF0E6C8),
    celestial      = GhibliMoonGlow,
    starAlpha      = 1f,
    fireflyAlpha   = 1f,
    surface        = GhibliNightMid,
    surfaceCard    = GhibliNightCard,
    textPrimary    = GhibliNightText,
    textSecondary  = GhibliNightSub,
    textMuted      = GhibliNightMuted,
    accent         = GhibliNightAccent,
    accentLight    = GhibliStarWhite,
    glassOverlay   = GlassWhiteNight,
    glassBorder    = GlassBorderNight,
    navBg          = GhibliNightMid,
    miniPlayerStart= Color(0xFF0D1F3C),
    miniPlayerEnd  = Color(0xFF1A2F50),
    playerBgTop    = GhibliNightSky,
    playerBgBottom = GhibliNightDeep,
)

val LocalGhibliColors = staticCompositionLocalOf { GhibliColors() }

@Composable
fun WaLeveTheme(
    isNightMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val ghibliColors = if (isNightMode) GhibliNightColors else GhibliColors()
    val materialScheme = if (isNightMode) GhibliNightColorScheme else GhibliDayColorScheme

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
