package com.stanislo.aura.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanislo.aura.data.ChatMessage
import com.stanislo.aura.data.ChatRole
import com.stanislo.aura.ui.theme.AuraAccent
import com.stanislo.aura.ui.theme.AuraIcons
import com.stanislo.aura.ui.theme.AuraInkSoft
import com.stanislo.aura.ui.theme.AuraOutline
import com.stanislo.aura.ui.theme.AuraSurface

@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    animateReveal: Boolean = false,
) {
    val isUser = message.chatRole == ChatRole.USER
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (isUser) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(24.dp, 24.dp, 8.dp, 24.dp))
                    .background(AuraSurface)
                    .padding(horizontal = 18.dp, vertical = 13.dp),
            )
        } else {
            val color = if (message.isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            RevealingText(
                text = message.text,
                color = color,
                animate = animateReveal && !message.isError,
                modifier = Modifier.widthIn(max = 340.dp),
            )
            if (message.tools.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    message.tools.take(3).forEach { ToolChip(it) }
                }
            }
        }
    }
}

/**
 * Odpowiedz pojawia sie slowo po slowie, w rytmie zblizonym do mowy.
 * Dluzsze teksty wchodza calosciowo, zeby nie zmuszac do czekania.
 */
@Composable
private fun RevealingText(
    text: String,
    color: Color,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val words = remember(text) { text.split(" ") }
    val shouldAnimate = animate && words.size in 1..70

    var started by remember(text) { mutableStateOf(!shouldAnimate) }
    LaunchedEffect(text) { started = true }

    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(
            durationMillis = (words.size * 34).coerceIn(180, 1500),
            easing = LinearEasing,
        ),
        label = "reveal",
    )

    val rendered: AnnotatedString = if (!shouldAnimate || progress >= 1f) {
        AnnotatedString(text)
    } else {
        buildAnnotatedString {
            words.forEachIndexed { index, word ->
                val start = index.toFloat() / words.size
                val alpha = ((progress - start) * words.size * 1.6f).coerceIn(0f, 1f)
                withStyle(SpanStyle(color = color.copy(alpha = alpha))) { append(word) }
                if (index < words.lastIndex) append(" ")
            }
        }
    }

    Text(
        text = rendered,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun ToolChip(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, AuraOutline, RoundedCornerShape(50))
            .padding(start = 9.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = AuraIcons.Sparkle,
            contentDescription = null,
            tint = AuraAccent,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AuraAccent,
        )
    }
}

/** Trzy pulsujace kropki pokazywane, gdy model pracuje. */
@Composable
fun ThinkingDots(modifier: Modifier = Modifier, color: Color = AuraInkSoft) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.18f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(680, delayMillis = index * 170, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}
