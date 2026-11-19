package com.waleve.player.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// ── Ghibli Day Palette ─────────────────────────────────
val GhibliSkyBlue     = Color(0xFF5BA4CF)
val GhibliSkyLight    = Color(0xFFB8DCF0)
val GhibliForestDeep  = Color(0xFF1B4332)
val GhibliForestMid   = Color(0xFF3E7B4F)   // olive-forest accent
val GhibliForestLight = Color(0xFF7DB87A)
val GhibliLeaf        = Color(0xFF74C69D)
val GhibliCream       = Color(0xFFFDFBF7)
val GhibliSunGold     = Color(0xFFF9D71C)
val GhibliSunset      = Color(0xFFF4A261)
val GhibliPeach       = Color(0xFFFFCCD5)
val GhibliMist        = Color(0xFF8DA9C4)
val GhibliOcean       = Color(0xFF006994)
val GhibliWave        = Color(0xFFB0E0E6)
val GhibliDayBg       = Color(0xFFF4F1E6)
val GhibliDaySurface  = Color(0xFFF8F4EA)   // warm parchment
val GhibliDayCard     = Color(0xFFFFFDF7)
val GhibliDayText     = Color(0xFF233A2C)
val GhibliDayTextSub  = Color(0xFF5C7362)
val GhibliDayMuted    = Color(0xFF98A493)   // sage, matches the greens

// ── Ghibli Night Palette ───────────────────────────────
val GhibliNightSky    = Color(0xFF020611)
val GhibliNightDeep   = Color(0xFF0A1222)
val GhibliNightMid    = Color(0xFF0F1A2C)
val GhibliNightCard   = Color(0xFF18253D)
val GhibliMoonGlow    = Color(0xFFE8DFB3)
val GhibliStarWhite   = Color(0xFFEEF4FF)
val GhibliNightAccent = Color(0xFF8FD19E)   // firefly green
val GhibliNightText   = Color(0xFFEDE8DA)   // warm moonlit cream
val GhibliNightSub    = Color(0xFFA9C7AE)
val GhibliNightMuted  = Color(0xFF7084A6)
val GhibliFirefly     = Color(0xFF90FF90)

// ── Glassmorphism helpers ──────────────────────────────
val GlassWhiteDay     = Color(0x33FFFFFF)
val GlassWhiteNight   = Color(0x1AFFFFFF)
val GlassBorderDay    = Color(0x44FFFFFF)
val GlassBorderNight  = Color(0x1A74C69D)

// ── Legacy aliases kept for compilation ───────────────
val NavyDeep      = GhibliNightDeep
val NavySurface   = GhibliNightMid
val ForestDark    = GhibliForestDeep
val ForestSurface = GhibliNightCard
val Olive         = GhibliForestMid
val OliveBright   = GhibliLeaf
val Cream         = GhibliCream
val Sage          = GhibliForestLight
val SageMuted     = GhibliMist

// ── Utility ────────────────────────────────────────────
val SurfaceVariant = Color(0xFF162636)
val ErrorRed       = Color(0xFFCF6679)
val SuccessGreen   = Color(0xFF4CAF50)
val DividerColor   = Color(0xFF1E3345)

// ── Light glassmorphism palette (Olive 1.2) ────────────
/**
 * Light / dark glass. Every Glass* colour below follows [GlassMode.dark], so the whole app
 * switches together (Compose re-draws whatever reads them).
 */
object GlassMode {
    var dark by androidx.compose.runtime.mutableStateOf(false)
}
private inline fun pick(light: Long, dark: Long) = Color(if (GlassMode.dark) dark else light)

val GlassInk: Color         get() = pick(0xFF1C2333, 0xFFEEF1F8)
val GlassSub: Color         get() = pick(0xFF4C5468, 0xFFB8C0D2)
val GlassMuted: Color       get() = pick(0xFF6F788C, 0xFF8E97AC)
val GlassAccent: Color      get() = pick(0xFF2E9C73, 0xFF4CC795)
val GlassAccentLight: Color get() = pick(0xFF46C190, 0xFF5ED3A3)
val GlassAccentDeep: Color  get() = pick(0xFF2E8FA8, 0xFF3FA6C2)
val GlassSurface: Color     get() = pick(0xFFF6F5FA, 0xFF11141C)
val GlassCard: Color        get() = pick(0x9EFFFFFF, 0x8C1C2130)   // ~62 % white / smoked glass
val GlassCardStrong: Color  get() = pick(0xC7FFFFFF, 0xB31C2130)
val GlassStroke: Color      get() = pick(0xE6FFFFFF, 0x2EFFFFFF)
val GlassDanger: Color      get() = pick(0xFFE0607E, 0xFFFF7A96)
/** Tint of every glass surface (white in light mode, smoky blue-black in dark). */
val GlassFill: Color        get() = pick(0xFFFFFFFF, 0xFF1B2030)
/** Opaque panels: bottom sheets, dialogs. */
val GlassPanel: Color       get() = pick(0xFFFAF9FD, 0xFF161A24)
/** Popup menus, chat bubbles, text fields. */
val GlassMenu: Color        get() = pick(0xFFFFFFFF, 0xFF232838)
val GlassBlobMint: Color  get() = pick(0xFF9EE6C6, 0xFF1F8A66)
val GlassBlobLilac: Color get() = pick(0xFFC9B8FF, 0xFF5B47C9)
val GlassBlobPeach: Color get() = pick(0xFFFFCDB2, 0xFF9A4A5E)
val GlassBlobSky: Color   get() = pick(0xFFA9D6FF, 0xFF1F5FA8)
