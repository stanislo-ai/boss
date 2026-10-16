package com.stopfat.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Glass {
    val BgTop = Color(0xFF070B1F)
    val BgMid = Color(0xFF15103D)
    val BgBottom = Color(0xFF06202E)

    val Teal = Color(0xFF3EF0C8)
    val Violet = Color(0xFF8C6CFF)
    val Pink = Color(0xFFFF5E9A)
    val Amber = Color(0xFFFFB547)
    val Sky = Color(0xFF4FC3FF)
    val Lime = Color(0xFFB6F36A)
    val Red = Color(0xFFFF5D6C)

    val Text = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.68f)
    val TextTertiary = Color.White.copy(alpha = 0.45f)

    val Fill = Color.White.copy(alpha = 0.09f)
    val FillStrong = Color.White.copy(alpha = 0.16f)
    val Stroke = Color.White.copy(alpha = 0.22f)
    val Dialog = Color(0xFF1A1745)

    val accent = Brush.linearGradient(listOf(Teal, Sky, Violet))
    val warm = Brush.linearGradient(listOf(Amber, Pink))
    val protein = Sky
    val carbs = Amber
    val fat = Pink
}

private val colors = darkColorScheme(
    primary = Glass.Teal,
    onPrimary = Color(0xFF00241C),
    secondary = Glass.Violet,
    onSecondary = Color.White,
    tertiary = Glass.Pink,
    background = Glass.BgTop,
    onBackground = Color.White,
    surface = Glass.Dialog,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF26235A),
    onSurfaceVariant = Glass.TextSecondary,
    surfaceContainerHigh = Glass.Dialog,
    surfaceContainer = Glass.Dialog,
    surfaceContainerHighest = Color(0xFF2A2660),
    outline = Glass.Stroke,
    error = Glass.Red,
)

private val base = Typography()
private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 23.sp),
)

@Composable
fun StopFatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
