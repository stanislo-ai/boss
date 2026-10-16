package com.stopfat.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stopfat.app.ui.theme.Glass
import kotlin.math.cos
import kotlin.math.sin

/** Animated "liquid" background: deep gradient with slowly drifting, glowing colour blobs. */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Glass.BgTop, Glass.BgMid, Glass.BgBottom))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun blob(color: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(cx, cy),
                )
            }
            blob(Glass.Violet, w * (0.15f + 0.10f * cos(phase)), h * (0.12f + 0.05f * sin(phase)), w * 0.85f, 0.55f)
            blob(Glass.Teal, w * (0.95f + 0.08f * sin(phase * 2)), h * (0.36f + 0.06f * cos(phase)), w * 0.70f, 0.32f)
            blob(Glass.Pink, w * (0.10f + 0.08f * sin(phase)), h * (0.72f + 0.05f * cos(phase * 2)), w * 0.70f, 0.30f)
            blob(Glass.Sky, w * (0.80f + 0.10f * cos(phase)), h * (0.98f + 0.04f * sin(phase)), w * 0.80f, 0.30f)
        }
        content()
    }
}

private fun Modifier.glassSurface(shape: Shape, strong: Boolean = false): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            if (strong) listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.10f))
            else listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.05f)),
        ),
    )
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.28f)),
        ),
        shape,
    )
    .drawWithContent {
        drawContent()
        // specular highlight on the upper part – the "liquid glass" sheen
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.10f),
                0.45f to Color.Transparent,
                endY = size.height * 0.6f,
            ),
            size = Size(size.width, size.height * 0.6f),
        )
    }

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    padding: PaddingValues = PaddingValues(18.dp),
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Column(
        modifier
            .glassSurface(shape, strong)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun GlassPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .then(
                if (selected) Modifier.background(Glass.accent).border(1.dp, Color.White.copy(alpha = 0.5f), shape)
                else Modifier.glassSurface(shape),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Color(0xFF07122A) else Glass.Text, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            color = if (selected) Color(0xFF07122A) else Glass.Text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    secondary: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier
            .heightIn(min = 54.dp)
            .then(if (!secondary && enabled) Modifier.shadow(18.dp, shape, ambientColor = Glass.Teal, spotColor = Glass.Violet) else Modifier)
            .clip(shape)
            .then(
                if (secondary) Modifier.glassSurface(shape, strong = true)
                else Modifier.background(Glass.accent).border(1.dp, Color.White.copy(alpha = 0.45f), shape),
            )
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = (if (secondary) Glass.Text else Color(0xFF07122A)).copy(alpha = alpha)
        if (loading) {
            CircularProgressIndicator(color = fg, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun GlassIconButton(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = Glass.Text, onClick: () -> Unit) {
    Box(
        modifier
            .size(size)
            .glassSurface(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 14.dp,
    colors: List<Color> = listOf(Glass.Teal, Glass.Sky, Glass.Violet, Glass.Teal),
    trackAlpha: Float = 0.10f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1.5f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(size.width - sw, size.height - sw)
            drawArc(Color.White.copy(alpha = trackAlpha), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
            val sweep = 360f * animated.coerceAtMost(1f)
            if (sweep > 0.5f) {
                // glow
                drawArc(
                    Brush.sweepGradient(colors.map { it.copy(alpha = 0.25f) }), -90f, sweep, false,
                    Offset(inset, inset), arcSize, style = Stroke(sw * 1.9f, cap = StrokeCap.Round),
                )
                drawArc(
                    Brush.sweepGradient(colors), -90f, sweep, false,
                    Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
            if (animated > 1f) {
                drawArc(Glass.Red, -90f, 360f * (animated - 1f).coerceAtMost(1f), false, Offset(inset, inset), arcSize, style = Stroke(sw * 0.5f, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

@Composable
fun MacroBar(label: String, value: Double, goal: Int, color: Color, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(if (goal > 0) (value / goal).toFloat().coerceIn(0f, 1f) else 0f, tween(800), label = "macro")
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Glass.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Text("${value.toInt()}/${goal} g", color = Glass.Text, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.10f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(p)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))),
            )
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Wstecz", onClick = onBack)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Glass.Text, maxLines = 2)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Glass.TextSecondary)
        }
        actions()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Glass.Text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, color = Glass.Teal, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp))
        }
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    suffix: String? = null,
    leading: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        placeholder = placeholder?.let { { Text(it, color = Glass.TextTertiary) } },
        suffix = suffix?.let { { Text(it, color = Glass.TextSecondary) } },
        leadingIcon = leading?.let { { Icon(it, null, tint = Glass.TextSecondary) } },
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Glass.Teal,
            unfocusedBorderColor = Glass.Stroke,
            focusedContainerColor = Color.White.copy(alpha = 0.07f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
            focusedLabelColor = Glass.Teal,
            unfocusedLabelColor = Glass.TextSecondary,
            cursorColor = Glass.Teal,
            focusedTextColor = Glass.Text,
            unfocusedTextColor = Glass.Text,
        ),
    )
}

@Composable
fun StatChip(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = Glass.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
fun EmptyHint(emoji: String, title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, color = Glass.Text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(text, color = Glass.TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun IconBadge(icon: ImageVector, brush: Brush, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.36f))
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(size * 0.36f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun BackRow(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Wstecz", tint = Glass.Text) }
}
