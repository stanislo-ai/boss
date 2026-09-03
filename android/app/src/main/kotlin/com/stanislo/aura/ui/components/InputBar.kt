package com.stanislo.aura.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.stanislo.aura.ui.AssistantPhase
import com.stanislo.aura.ui.theme.AuraAccent
import com.stanislo.aura.ui.theme.AuraAccentSoft
import com.stanislo.aura.ui.theme.AuraIcons
import com.stanislo.aura.ui.theme.AuraInk
import com.stanislo.aura.ui.theme.AuraInkFaint
import com.stanislo.aura.ui.theme.AuraMint
import com.stanislo.aura.ui.theme.AuraOutline
import kotlin.math.sin

@Composable
fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    phase: AssistantPhase,
    amplitude: Float,
    modifier: Modifier = Modifier,
) {
    val focused = value.isNotEmpty()
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (focused) AuraOutline.copy(alpha = 1f) else AuraOutline,
        animationSpec = tween(240),
        label = "border",
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(Color.White)
                .border(1.dp, borderColor, RoundedCornerShape(27.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Placeholder(visible = value.isEmpty(), text = "Napisz albo powiedz...")
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge)
                    .copy(color = AuraInk),
                cursorBrush = SolidColor(AuraAccent),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AnimatedVisibility(
            visible = value.isNotBlank(),
            enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 420f)) + fadeIn(tween(160)),
            exit = scaleOut(tween(140)) + fadeOut(tween(140)),
        ) {
            CircleButton(
                onClick = onSend,
                background = SolidColor(AuraInk),
                contentDescription = "Wyslij",
                icon = AuraIcons.ArrowUp,
            )
        }

        MicButton(phase = phase, amplitude = amplitude, onClick = onMicClick)
    }
}

/** Wydzielone, by [AnimatedVisibility] nie trafilo w zasieg RowScope z paska wejscia. */
@Composable
private fun Placeholder(visible: Boolean, text: String) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(120)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = AuraInkFaint,
        )
    }
}

@Composable
private fun MicButton(
    phase: AssistantPhase,
    amplitude: Float,
    onClick: () -> Unit,
) {
    val listening = phase == AssistantPhase.LISTENING
    val busy = phase == AssistantPhase.THINKING || phase == AssistantPhase.SPEAKING
    val time by rememberAnimationTime()

    val voiceScale by animateFloatAsState(
        targetValue = if (listening) 1f + amplitude * 0.16f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "voiceScale",
    )
    val glow by animateFloatAsState(
        targetValue = if (listening) 1f else if (busy) 0.55f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "glow",
    )

    val background = when {
        listening -> Brush.linearGradient(listOf(AuraAccent, AuraAccentSoft))
        busy -> Brush.linearGradient(listOf(AuraMint, AuraAccentSoft))
        else -> SolidColor(AuraInk)
    }

    Box(contentAlignment = Alignment.Center) {
        // Pulsujaca poswiata pod przyciskiem podczas nasluchu.
        if (glow > 0.01f) {
            Canvas(modifier = Modifier.size(92.dp)) {
                val pulse = 0.82f + 0.18f * sin(time * 3.4f)
                val radius = size.minDimension / 2f * pulse * (0.7f + amplitude * 0.3f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AuraAccentSoft.copy(alpha = 0.34f * glow),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        }

        CircleButton(
            onClick = onClick,
            background = background,
            contentDescription = if (listening) "Zatrzymaj" else "Mow",
            icon = when {
                listening -> AuraIcons.Stop
                busy -> AuraIcons.Waveform
                else -> AuraIcons.Mic
            },
            modifier = Modifier.scale(voiceScale),
        )
    }
}

@Composable
private fun CircleButton(
    onClick: () -> Unit,
    background: Brush,
    contentDescription: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // Delikatne "wciskanie" - najbardziej odczuwalny detal w calym interfejsie.
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 900f),
        label = "press",
    )
    val iconSize by animateDpAsState(
        targetValue = if (pressed) 20.dp else 22.dp,
        animationSpec = tween(120),
        label = "iconSize",
    )

    Box(
        modifier = modifier
            .scale(press)
            .size(54.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickableNoRipple(interactionSource) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}
