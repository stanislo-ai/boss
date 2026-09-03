package com.stanislo.aura.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.stanislo.aura.R

/**
 * Inter - krojem najblizszym systemowemu San Francisco, jaki wolno rozprowadzac
 * (licencja OFL). Wariant "Display" ma ciasniejsze swiatlo i sluzy duzym napisom,
 * dokladnie tak, jak Apple dzieli SF Pro Text i SF Pro Display.
 */
val InterText = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

val InterDisplay = FontFamily(
    Font(R.font.inter_display_light, FontWeight.Light),
    Font(R.font.inter_display_regular, FontWeight.Normal),
    Font(R.font.inter_display_medium, FontWeight.Medium),
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
)

/** Wyrownanie linii bez zbednego swiatla nad pierwszym i pod ostatnim wierszem. */
private val TrimmedLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun display(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Light,
    tracking: Float,
) = TextStyle(
    fontFamily = InterDisplay,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = TrimmedLineHeight,
)

private fun text(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = InterText,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = TrimmedLineHeight,
)

/**
 * Skala wzorowana na iOS: im wiekszy stopien pisma, tym ciasniejsze swiatlo miedzy
 * literami; male etykiety dostaja swiatlo dodatnie, zeby pozostac czytelne.
 */
val AuraTypography = Typography(
    displayLarge = display(52, 58, tracking = -1.6f),
    displayMedium = display(42, 48, tracking = -1.2f),
    displaySmall = display(34, 40, tracking = -0.9f),

    headlineLarge = display(30, 36, tracking = -0.7f),
    headlineMedium = display(26, 32, tracking = -0.6f),
    headlineSmall = display(23, 29, FontWeight.Normal, -0.5f),

    titleLarge = display(21, 26, FontWeight.Medium, -0.4f),
    titleMedium = text(17, 22, FontWeight.Medium, -0.2f),
    titleSmall = text(15, 20, FontWeight.Medium, -0.1f),

    bodyLarge = text(16, 23, tracking = -0.1f),
    bodyMedium = text(14, 20, tracking = 0f),
    bodySmall = text(13, 18, tracking = 0f),

    labelLarge = text(14, 18, FontWeight.Medium, 0.1f),
    labelMedium = text(12, 16, FontWeight.Medium, 0.2f),
    labelSmall = text(11, 14, FontWeight.Medium, 0.45f),
)
