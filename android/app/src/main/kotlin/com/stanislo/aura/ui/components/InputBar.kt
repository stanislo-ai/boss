package com.stanislo.aura.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.stanislo.aura.ui.AssistantPhase
import com.stanislo.aura.ui.theme.AuraAccent
import com.stanislo.aura.ui.theme.AuraAccentSoft
import com.stanislo.aura.ui.theme.AuraInk
import com.stanislo.aura.ui.theme.AuraInkSoft
import com.stanislo.aura.ui.theme.AuraMint
import com.stanislo.aura.ui.theme.AuraOutline

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
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(26.dp))
                .border(1.dp, AuraOutline, RoundedCornerShape(26.dp))
                .background(Color.White)
                .padding(horizontal = 18.dp, vertical = 15.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = "Napisz albo powiedz...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AuraInkSoft.copy(alpha = 0.7f),
                )
            }
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
            enter = scaleIn(spring(stiffness = 420f)) + fadeIn(),
            exit = scaleOut(tween(140)) + fadeOut(tween(140)),
        ) {
            CircleButton(
                onClick = onSend,
                background = SolidColor(AuraInk),
            ) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Wyslij", tint = Color.White)
            }
        }

        MicButton(phase = phase, amplitude = amplitude, onClick = onMicClick)
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

    val transition = rememberInfiniteTransition(label = "mic")
    val idlePulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idlePulse",
    )
    val voiceScale by animateFloatAsState(
        targetValue = if (listening) 1f + amplitude * 0.18f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "voiceScale",
    )

    val background = when {
        listening -> Brush.linearGradient(listOf(AuraAccent, AuraAccentSoft))
        busy -> Brush.linearGradient(listOf(AuraMint, AuraAccentSoft))
        else -> SolidColor(AuraInk)
    }

    CircleButton(
        onClick = onClick,
        background = background,
        modifier = Modifier.scale(if (listening) voiceScale else idlePulse),
    ) {
        val icon = when {
            listening -> Icons.Rounded.Stop
            busy -> Icons.Rounded.GraphicEq
            else -> Icons.Rounded.Mic
        }
        Icon(
            imageVector = icon,
            contentDescription = if (listening) "Zatrzymaj" else "Mow",
            tint = Color.White,
        )
    }
}

@Composable
private fun CircleButton(
    onClick: () -> Unit,
    background: Brush,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Klikniecie bez efektu fali - spojne z minimalistycznym charakterem interfejsu. */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    )
}
