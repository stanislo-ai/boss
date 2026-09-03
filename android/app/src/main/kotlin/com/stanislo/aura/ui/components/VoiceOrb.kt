package com.stanislo.aura.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.stanislo.aura.ui.AssistantPhase
import com.stanislo.aura.ui.theme.AuraAccent
import com.stanislo.aura.ui.theme.AuraAccentSoft
import com.stanislo.aura.ui.theme.AuraBlush
import com.stanislo.aura.ui.theme.AuraGold
import com.stanislo.aura.ui.theme.AuraMint
import kotlin.math.cos
import kotlin.math.sin

/**
 * Zywa "aura" - miekka kula ze swiatla, oplywana przez chmure drobin.
 * Reaguje na glosnosc mowy i zmienia charakter zaleznie od stanu asystenta.
 */
@Composable
fun VoiceOrb(
    phase: AssistantPhase,
    amplitude: Float,
    modifier: Modifier = Modifier,
    particleCount: Int = 44,
) {
    val time by rememberAnimationTime()
    val particles = remember(particleCount) { buildOrbitParticles(particleCount) }

    // Glosnosc wygladzona sprezyna, zeby kula nie "skakala".
    val level by animateFloatAsState(
        targetValue = if (phase == AssistantPhase.LISTENING) amplitude else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f),
        label = "level",
    )

    val intensity by animateFloatAsState(
        targetValue = when (phase) {
            AssistantPhase.IDLE -> 0.45f
            AssistantPhase.LISTENING -> 1f
            AssistantPhase.THINKING -> 0.85f
            AssistantPhase.SPEAKING -> 0.75f
        },
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "intensity",
    )

    val ring by animateFloatAsState(
        targetValue = if (phase == AssistantPhase.THINKING) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "ring",
    )

    val palette = when (phase) {
        AssistantPhase.IDLE -> listOf(AuraAccentSoft, AuraMint, AuraBlush)
        AssistantPhase.LISTENING -> listOf(AuraAccent, AuraAccentSoft, AuraMint)
        AssistantPhase.THINKING -> listOf(AuraAccent, AuraBlush, AuraGold)
        AssistantPhase.SPEAKING -> listOf(AuraMint, AuraAccentSoft, AuraAccent)
    }

    // Szybsze krazenie warstw podczas myslenia; oddech niezalezny od stanu.
    val spin = time * if (phase == AssistantPhase.THINKING) 95f else 22f
    val breathe = 1f + 0.05f * sin(time * 0.9f)

    Canvas(modifier = modifier) {
        val maxRadius = size.minDimension / 2f
        val core = maxRadius * (0.46f + level * 0.15f) * breathe

        drawHalo(core, intensity, palette)
        drawOrbitParticles(particles, time, core, level, intensity)
        drawRotatingLayers(spin, time, core, intensity, palette)
        if (ring > 0.01f) drawThinkingRing(spin, core, ring, palette)
        drawCore(core, intensity, palette)
        if (level > 0.02f) drawVoiceRipples(core, level, palette)
    }
}

private fun DrawScope.drawHalo(core: Float, intensity: Float, palette: List<Color>) {
    // Trzy rozmyte pierscienie tworza wrazenie swiatla wokol kuli.
    listOf(2.1f to 0.09f, 1.6f to 0.14f, 1.25f to 0.20f).forEach { (scale, alpha) ->
        val radius = core * scale
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette[0].copy(alpha = alpha * intensity), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

private fun DrawScope.drawRotatingLayers(
    spin: Float,
    time: Float,
    core: Float,
    intensity: Float,
    palette: List<Color>,
) {
    palette.forEachIndexed { index, color ->
        val angle = time * 0.55f + index * 2.1f
        val drift = core * 0.18f
        val offset = Offset(
            x = center.x + cos(angle) * drift,
            y = center.y + sin(angle * 1.3f) * drift,
        )
        rotate(degrees = spin + index * 120f, pivot = center) {
            val radius = core * 1.08f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.58f * intensity),
                        color.copy(alpha = 0.13f * intensity),
                        Color.Transparent,
                    ),
                    center = offset,
                    radius = radius,
                ),
                radius = radius,
                center = offset,
            )
        }
    }
}

/** Cienki, obracajacy sie luk pokazywany, gdy model pracuje. */
private fun DrawScope.drawThinkingRing(
    spin: Float,
    core: Float,
    visibility: Float,
    palette: List<Color>,
) {
    val radius = core * 1.42f
    rotate(degrees = spin * 1.6f, pivot = center) {
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.Transparent,
                    palette[0].copy(alpha = 0.85f * visibility),
                    Color.Transparent,
                ),
                center = center,
            ),
            startAngle = 0f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 2.2f),
        )
    }
}

private fun DrawScope.drawCore(core: Float, intensity: Float, palette: List<Color>) {
    // Jasne, prawie biale wnetrze - utrzymuje biel jako kolor przewodni.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.97f),
                Color.White.copy(alpha = 0.74f),
                palette[0].copy(alpha = 0.22f * intensity),
                Color.Transparent,
            ),
            center = center,
            radius = core,
        ),
        radius = core,
        center = center,
    )
    drawCircle(color = Color.White.copy(alpha = 0.38f), radius = core * 0.44f, center = center)
}

/** Dwa rozchodzace sie kregi, ktorych zasieg zalezy od glosnosci mowy. */
private fun DrawScope.drawVoiceRipples(core: Float, level: Float, palette: List<Color>) {
    listOf(1.15f to 0.55f, 1.34f to 0.3f).forEachIndexed { index, (scale, strength) ->
        val radius = core * scale * (1f + level * 0.28f)
        drawCircle(
            color = palette[(index + 1) % palette.size].copy(alpha = level * strength * 0.5f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.4f),
        )
    }
}
