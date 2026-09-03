package com.stanislo.aura.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Biel jest kolorem przewodnim - reszta palety to subtelne szarosci
// plus kilka chlodnych akcentow uzywanych oszczednie.
val AuraWhite = Color(0xFFFFFFFF)
val AuraCanvas = Color(0xFFFBFBFC)
val AuraSurface = Color(0xFFF4F5F7)
val AuraSurfaceSoft = Color(0xFFFAFAFB)
val AuraInk = Color(0xFF0B0B0F)
val AuraInkSoft = Color(0xFF71757E)
val AuraInkFaint = Color(0xFFA2A6AE)
val AuraOutline = Color(0xFFEBECEF)
val AuraAccent = Color(0xFF4B5BD6)
val AuraAccentSoft = Color(0xFFA8B2F5)
val AuraMint = Color(0xFF6FD9C6)
val AuraBlush = Color(0xFFF5B5C8)
val AuraGold = Color(0xFFF7D69A)
val AuraDanger = Color(0xFFC4372B)

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

/** Aplikacja celowo trzyma sie jasnej, bialej identyfikacji niezaleznie od trybu systemu. */
@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuraColorScheme,
        typography = AuraTypography,
        content = content,
    )
}
