package com.stanislo.aura.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Klikniecie bez efektu fali - spojne z minimalistycznym charakterem interfejsu. */
@Composable
fun Modifier.clickableNoRipple(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = interactionSource,
    indication = null,
    onClick = onClick,
)

@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    clickableNoRipple(remember { MutableInteractionSource() }, onClick)

/**
 * Element lekko sie kurczy pod palcem i wraca sprezyscie - zamiast fali Materiala
 * daje wrazenie fizycznego przycisku, blizsze temu, co robi iOS.
 */
@Composable
fun Modifier.pressable(
    scaleDown: Float = 0.94f,
    haptic: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 800f),
        label = "pressScale",
    )
    return this
        .scale(scale)
        .clickable(interactionSource = interactionSource, indication = null) {
            if (haptic) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}
