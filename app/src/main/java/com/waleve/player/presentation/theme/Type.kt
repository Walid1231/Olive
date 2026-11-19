package com.waleve.player.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.waleve.player.R

val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val fraunces = GoogleFont("Fraunces")
val manrope = GoogleFont("Manrope")

val FrauncesFamily = FontFamily(
    Font(googleFont = fraunces, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = fraunces, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = fraunces, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = fraunces, fontProvider = fontProvider, weight = FontWeight.Bold),
)

/** Outfit — the app's font (downloaded once through Google Play services, then cached). */
val outfit = GoogleFont("Outfit")

val OutfitFamily = FontFamily(
    Font(googleFont = outfit, fontProvider = fontProvider, weight = FontWeight.Light),
    Font(googleFont = outfit, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = outfit, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = outfit, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = outfit, fontProvider = fontProvider, weight = FontWeight.Bold),
    Font(googleFont = outfit, fontProvider = fontProvider, weight = FontWeight.ExtraBold),
    Font(googleFont = outfit, fontProvider = fontProvider, weight = FontWeight.Black),
)

/** Kept for old references — now Outfit everywhere. */
val ManropeFamily = OutfitFamily

val WaLeveTypography = Typography(
    // Display — Fraunces serif for big hero text
    displayLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        color = Cream,
    ),
    displayMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        color = Cream,
    ),
    displaySmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = Cream,
    ),
    // Headlines — Fraunces for song titles / section headers
    headlineLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = Cream,
    ),
    headlineMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = Cream,
    ),
    headlineSmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = Cream,
    ),
    // Titles — Manrope for screen titles
    titleLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        color = Cream,
    ),
    titleMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = Cream,
    ),
    titleSmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Cream,
    ),
    // Body — Manrope for all body/UI text
    bodyLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = Cream,
    ),
    bodyMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Sage,
    ),
    bodySmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = SageMuted,
    ),
    // Labels — Manrope
    labelLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Cream,
    ),
    labelMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = Sage,
    ),
    labelSmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = SageMuted,
    ),
)
