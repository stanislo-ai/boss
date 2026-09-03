package com.stanislo.aura.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanislo.aura.data.ChatMessage
import com.stanislo.aura.data.ChatRole
import com.stanislo.aura.ui.theme.AuraAccent
import com.stanislo.aura.ui.theme.AuraInkSoft
import com.stanislo.aura.ui.theme.AuraOutline
import com.stanislo.aura.ui.theme.AuraSurface

@Composable
fun MessageBubble(message: ChatMessage, modifier: Modifier = Modifier) {
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
                    .clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp))
                    .background(AuraSurface)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        } else {
            val errorColor = MaterialTheme.colorScheme.error
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (message.isError) errorColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 340.dp),
            )
            if (message.tools.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    message.tools.take(3).forEach { ToolChip(it) }
                }
            }
        }
    }
}

@Composable
fun ToolChip(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = AuraAccent,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, AuraOutline, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Trzy pulsujace kropki pokazywane, gdy model pracuje. */
@Composable
fun ThinkingDots(modifier: Modifier = Modifier, color: Color = AuraInkSoft) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700, delayMillis = index * 180, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}
