package com.stanislo.aura.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Biel jest kolorem przewodnim - reszta palety to subtelne szarosci
// plus jeden chlodny akcent uzywany oszczednie.
val AuraWhite = Color(0xFFFFFFFF)
val AuraCanvas = Color(0xFFFCFCFD)
val AuraSurface = Color(0xFFF5F6F8)
val AuraInk = Color(0xFF101114)
val AuraInkSoft = Color(0xFF6B6F76)
val AuraOutline = Color(0xFFE7E9ED)
val AuraAccent = Color(0xFF4C5BD4)
val AuraAccentSoft = Color(0xFFAAB4F8)
val AuraMint = Color(0xFF7FD8C4)
val AuraBlush = Color(0xFFF6B8C8)
val AuraDanger = Color(0xFFC0392B)

private val AuraColorScheme = lightColorScheme(
    primary = AuraInk,
    onPrimary = AuraWhite,
    primaryContainer = AuraSurface,
    onPrimaryContainer = AuraInk,
    secondary = AuraAccent,
    onSecondary = AuraWhite,
    secondaryContainer = Color(0xFFEEF0FE),
    onSecondaryContainer = AuraAccent,
    background = AuraWhite,
    onBackground = AuraInk,
    surface = AuraWhite,
    onSurface = AuraInk,
    surfaceVariant = AuraSurface,
    onSurfaceVariant = AuraInkSoft,
    outline = AuraOutline,
    outlineVariant = AuraOutline,
    error = AuraDanger,
    onError = AuraWhite,
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = AuraDanger,
)

private val AuraTypography = Typography().let { base ->
    Typography(
        displaySmall = base.displaySmall.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.5).sp,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.4).sp,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = (-0.3).sp,
        ),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
        labelSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
        ),
    )
}

/** Aplikacja celowo trzyma sie jasnej, bialej identyfikacji niezaleznie od trybu systemu. */
@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuraColorScheme,
        typography = AuraTypography,
        content = content,
    )
}
