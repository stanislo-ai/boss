package com.stopfat.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** White "liquid glass" palette: frosted white surfaces over soft, moving pastel gradients. */
object Glass {
    val BgTop = Color(0xFFF6F7FF)
    val BgMid = Color(0xFFFFFFFF)
    val BgBottom = Color(0xFFF1F8FF)

    // Pastel blobs behind the glass
    val BlobLavender = Color(0xFFC9B8FF)
    val BlobMint = Color(0xFFA8F0DC)
    val BlobPeach = Color(0xFFFFD2B5)
    val BlobSky = Color(0xFFB7DEFF)
    val BlobPink = Color(0xFFFFC4DC)

    // Vivid accents (tuned for contrast on white)
    val Teal = Color(0xFF0FBF9F)
    /** Darker teal for text on white (WCAG-friendly). */
    val TealText = Color(0xFF07866E)
    val AmberText = Color(0xFFB86200)
    val LimeText = Color(0xFF23853D)
    val Violet = Color(0xFF7457FF)
    val Pink = Color(0xFFFF4F8B)
    val Amber = Color(0xFFFF9500)
    val Sky = Color(0xFF2F8BFF)
    val Lime = Color(0xFF34B556)
    val Red = Color(0xFFEF3E56)

    val Text = Color(0xFF111633)
    val TextSecondary = Color(0xFF111633).copy(alpha = 0.62f)
    val TextTertiary = Color(0xFF111633).copy(alpha = 0.42f)
    val OnAccent = Color.White

    val Fill = Color(0xFF111633).copy(alpha = 0.06f)
    val FillStrong = Color(0xFF111633).copy(alpha = 0.10f)
    val Stroke = Color(0xFF111633).copy(alpha = 0.12f)
    val Track = Color(0xFF111633).copy(alpha = 0.07f)
    val Dialog = Color(0xFFFDFDFF)
    val Shadow = Color(0xFF5B4BDB)

    val accentColors = listOf(Color(0xFF14D3AE), Color(0xFF2F8BFF), Color(0xFF7457FF))
    val accent = Brush.linearGradient(accentColors)
    val warm = Brush.linearGradient(listOf(Color(0xFFFFB13B), Color(0xFFFF4F8B)))
    val protein = Sky
    val carbs = Amber
    val fat = Pink
}

private val colors = lightColorScheme(
    primary = Glass.Violet,
    onPrimary = Color.White,
    secondary = Glass.Teal,
    onSecondary = Color.White,
    tertiary = Glass.Pink,
    background = Glass.BgMid,
    onBackground = Glass.Text,
    surface = Glass.Dialog,
    onSurface = Glass.Text,
    surfaceVariant = Color(0xFFEFF1FB),
    onSurfaceVariant = Glass.TextSecondary,
    surfaceContainerHigh = Glass.Dialog,
    surfaceContainer = Glass.Dialog,
    surfaceContainerHighest = Color(0xFFF0F1FA),
    outline = Glass.Stroke,
    error = Glass.Red,
)

private val base = Typography()
private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 23.sp),
)

@Composable
fun StopFatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
