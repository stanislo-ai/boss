package com.stanislo.aura.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.stanislo.aura.ui.AssistantPhase
import com.stanislo.aura.ui.theme.AuraAccent
import com.stanislo.aura.ui.theme.AuraAccentSoft
import com.stanislo.aura.ui.theme.AuraBlush
import com.stanislo.aura.ui.theme.AuraMint
import kotlin.math.cos
import kotlin.math.sin

/**
 * Zywa "aura" - miekka kula z wolno obracajacymi sie warstwami koloru.
 * Reaguje na glosnosc mowy i zmienia charakter zaleznie od stanu asystenta.
 */
@Composable
fun VoiceOrb(
    phase: AssistantPhase,
    amplitude: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "orb")

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (phase == AssistantPhase.THINKING) 3200 else 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    val breathe by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    val wobble by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wobble",
    )

    // Glosnosc wygladzona sprezyna, zeby kula nie "skakala".
    val level by animateFloatAsState(
        targetValue = if (phase == AssistantPhase.LISTENING) amplitude else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f),
        label = "level",
    )

    val intensity by animateFloatAsState(
        targetValue = when (phase) {
            AssistantPhase.IDLE -> 0.42f
            AssistantPhase.LISTENING -> 0.95f
            AssistantPhase.THINKING -> 0.8f
            AssistantPhase.SPEAKING -> 0.7f
        },
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "intensity",
    )

    val palette = when (phase) {
        AssistantPhase.IDLE -> listOf(AuraAccentSoft, AuraMint, AuraBlush)
        AssistantPhase.LISTENING -> listOf(AuraAccent, AuraAccentSoft, AuraMint)
        AssistantPhase.THINKING -> listOf(AuraAccent, AuraBlush, AuraAccentSoft)
        AssistantPhase.SPEAKING -> listOf(AuraMint, AuraAccentSoft, AuraAccent)
    }

    Canvas(modifier = modifier) {
        val maxRadius = size.minDimension / 2f
        val core = maxRadius * (0.52f + level * 0.16f) * breathe

        drawHalo(core, intensity, palette)
        drawRotatingLayers(rotation, wobble, core, intensity, palette)
        drawCore(core, intensity, palette)
    }
}

private fun DrawScope.drawHalo(core: Float, intensity: Float, palette: List<Color>) {
    // Trzy rozmyte pierscienie tworza wrazenie swiatla wokol kuli.
    listOf(1.9f to 0.10f, 1.5f to 0.16f, 1.2f to 0.22f).forEach { (scale, alpha) ->
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette[0].copy(alpha = alpha * intensity),
                    Color.Transparent,
                ),
                center = center,
                radius = core * scale,
            ),
            radius = core * scale,
            center = center,
        )
    }
}

private fun DrawScope.drawRotatingLayers(
    rotation: Float,
    wobble: Float,
    core: Float,
    intensity: Float,
    palette: List<Color>,
) {
    palette.forEachIndexed { index, color ->
        val angle = wobble + index * 2.1f
        val drift = core * 0.16f
        val offset = Offset(
            x = center.x + cos(angle) * drift,
            y = center.y + sin(angle * 1.3f) * drift,
        )
        rotate(degrees = rotation + index * 120f, pivot = center) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.55f * intensity),
                        color.copy(alpha = 0.12f * intensity),
                        Color.Transparent,
                    ),
                    center = offset,
                    radius = core * 1.05f,
                ),
                radius = core * 1.05f,
                center = offset,
            )
        }
    }
}

private fun DrawScope.drawCore(core: Float, intensity: Float, palette: List<Color>) {
    // Jasne, prawie biale wnetrze - utrzymuje biel jako kolor przewodni.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.96f),
                Color.White.copy(alpha = 0.72f),
                palette[0].copy(alpha = 0.20f * intensity),
                Color.Transparent,
            ),
            center = center,
            radius = core,
        ),
        radius = core,
        center = center,
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = core * 0.42f,
        center = center,
    )
}
