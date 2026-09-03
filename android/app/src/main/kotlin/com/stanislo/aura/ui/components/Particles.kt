package com.stanislo.aura.ui.components

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.stanislo.aura.ui.theme.AuraAccentSoft
import com.stanislo.aura.ui.theme.AuraBlush
import com.stanislo.aura.ui.theme.AuraGold
import com.stanislo.aura.ui.theme.AuraMint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Zegar animacji oparty o klatki kompozycji - daje plynny, ciagly czas
 * (w przeciwienstwie do petli 0..1, ktora skacze przy kazdym powtorzeniu).
 * Zatrzymuje sie sam, gdy ekran jest niewidoczny albo animacje sa wylaczone.
 */
@Composable
fun rememberAnimationTime(): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var origin = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { frame ->
                if (origin == 0L) origin = frame
                time.floatValue = (frame - origin) / 1000f
            }
        }
    }
    return time
}

/** Pojedyncza drobina krazaca wokol kuli. */
internal data class OrbitParticle(
    val orbit: Float,
    val speed: Float,
    val phase: Float,
    val radius: Float,
    val drift: Float,
    val driftSpeed: Float,
    val color: Color,
    val alpha: Float,
)

internal fun buildOrbitParticles(count: Int, seed: Int = 7): List<OrbitParticle> {
    val random = Random(seed)
    val palette = listOf(AuraAccentSoft, AuraMint, AuraBlush, AuraGold)
    return List(count) {
        OrbitParticle(
            orbit = 0.75f + random.nextFloat() * 0.95f,
            speed = (0.06f + random.nextFloat() * 0.22f) * if (random.nextBoolean()) 1f else -1f,
            phase = random.nextFloat() * 6.2832f,
            radius = 0.9f + random.nextFloat() * 2.6f,
            drift = 0.04f + random.nextFloat() * 0.16f,
            driftSpeed = 0.25f + random.nextFloat() * 0.9f,
            color = palette[random.nextInt(palette.size)],
            alpha = 0.25f + random.nextFloat() * 0.55f,
        )
    }
}

/**
 * Delikatny pyl unoszacy sie w tle calego ekranu. Ma byc ledwo zauwazalny -
 * nadaje bieli glebi, ale nie odwraca uwagi od tresci.
 */
@Composable
fun AmbientParticles(
    modifier: Modifier = Modifier,
    count: Int = 26,
    intensity: Float = 1f,
) {
    val time by rememberAnimationTime()
    val specs = remember(count) {
        val random = Random(21)
        val palette = listOf(AuraAccentSoft, AuraMint, AuraBlush, AuraGold)
        List(count) {
            AmbientSpec(
                x = random.nextFloat(),
                y = random.nextFloat(),
                speed = 0.006f + random.nextFloat() * 0.022f,
                sway = 0.01f + random.nextFloat() * 0.05f,
                swaySpeed = 0.15f + random.nextFloat() * 0.5f,
                radius = 1f + random.nextFloat() * 2.4f,
                color = palette[random.nextInt(palette.size)],
                alpha = 0.08f + random.nextFloat() * 0.16f,
            )
        }
    }

    Canvas(modifier = modifier) {
        specs.forEach { spec ->
            // Ruch w gore z zapetleniem; delikatne bujanie na boki.
            val progress = (spec.y - time * spec.speed).mod(1f)
            val x = (spec.x + sin(time * spec.swaySpeed + spec.x * 10f) * spec.sway).mod(1f)
            val center = Offset(x * size.width, progress * size.height)
            // Wygaszanie przy krawedziach, zeby drobiny nie znikaly nagle.
            val edgeFade = (1f - kotlin.math.abs(progress - 0.5f) * 2f).coerceIn(0f, 1f)
            drawCircle(
                color = spec.color.copy(alpha = spec.alpha * edgeFade * intensity),
                radius = spec.radius,
                center = center,
            )
        }
    }
}

private data class AmbientSpec(
    val x: Float,
    val y: Float,
    val speed: Float,
    val sway: Float,
    val swaySpeed: Float,
    val radius: Float,
    val color: Color,
    val alpha: Float,
)

/** Rysuje drobiny krazace wokol kuli. Wywolywane z wnetrza [Canvas] kuli. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOrbitParticles(
    particles: List<OrbitParticle>,
    time: Float,
    core: Float,
    level: Float,
    intensity: Float,
) {
    val center = this.center
    particles.forEach { particle ->
        val angle = particle.phase + time * particle.speed * (1f + level * 1.6f)
        val breath = sin(time * particle.driftSpeed + particle.phase)
        val distance = core * (particle.orbit + particle.drift * breath) * (1f + level * 0.42f)
        val position = Offset(
            x = center.x + cos(angle) * distance,
            y = center.y + sin(angle) * distance * 0.93f,
        )
        val alpha = (particle.alpha * intensity * (0.55f + 0.45f * breath)).coerceIn(0f, 1f)
        val radius = particle.radius * (1f + level * 0.8f)

        // Poswiata wokol drobiny plus jej jasny srodek.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(particle.color.copy(alpha = alpha * 0.5f), Color.Transparent),
                center = position,
                radius = radius * 3.2f,
            ),
            radius = radius * 3.2f,
            center = position,
        )
        drawCircle(color = particle.color.copy(alpha = alpha), radius = radius, center = position)
    }
}
