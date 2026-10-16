package com.stopfat.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stopfat.app.ui.theme.Glass
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ------------------------------------------------------------------ animation helpers

/** Hands out increasing indices so cards on a screen appear one after another. */
class StaggerCounter {
    private var next = 0
    fun next(): Int = next++
}

val LocalStagger = compositionLocalOf<StaggerCounter?> { null }

/** Entrance animation: fade + rise + slight scale, delayed by its stagger index. */
fun Modifier.enterAnimation(index: Int = -1): Modifier = composed {
    val stagger = LocalStagger.current
    val order = remember { if (index >= 0) index else stagger?.next() ?: 0 }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((order * 55L).coerceAtMost(550L))
        progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 46.dp.toPx()
        val s = 0.94f + 0.06f * p
        scaleX = s
        scaleY = s
    }
}

/** Squishy "liquid" press feedback. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.95f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

fun Modifier.liquidClickable(pressed: Float = 0.95f, enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    this
        .pressScale(interaction, pressed)
        .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}

// ------------------------------------------------------------------ background

/** White liquid background: soft pastel gradient blobs drifting slowly + rising glass bubbles. */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(22_000, easing = LinearEasing)), label = "phase")
    val rise by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "rise")
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
                    Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.4f), Color.Transparent), Offset(cx, cy), r),
                    r, Offset(cx, cy),
                )
            }
            blob(Glass.BlobLavender, w * (0.12f + 0.14f * cos(phase)), h * (0.10f + 0.06f * sin(phase)), w * 0.95f, 0.85f)
            blob(Glass.BlobMint, w * (0.98f + 0.10f * sin(phase * 2)), h * (0.30f + 0.08f * cos(phase)), w * 0.80f, 0.75f)
            blob(Glass.BlobPeach, w * (0.05f + 0.12f * sin(phase)), h * (0.62f + 0.07f * cos(phase * 2)), w * 0.78f, 0.65f)
            blob(Glass.BlobSky, w * (0.85f + 0.12f * cos(phase)), h * (0.92f + 0.05f * sin(phase)), w * 0.90f, 0.75f)
            blob(Glass.BlobPink, w * (0.55f + 0.20f * sin(phase * 1.5f)), h * (0.48f + 0.10f * cos(phase * 1.5f)), w * 0.55f, 0.45f)

            // floating glass bubbles
            val bubbles = listOf(0.12f to 22f, 0.33f to 14f, 0.58f to 26f, 0.76f to 12f, 0.9f to 18f, 0.46f to 10f)
            bubbles.forEachIndexed { i, (x, r) ->
                val p = (rise + i * 0.17f) % 1f
                val cy = h * (1.08f - p * 1.2f)
                val cx = w * x + sin(phase * 2 + i) * 18f
                val rad = r * density
                drawCircle(Color.White.copy(alpha = 0.55f * (1f - p)), rad, Offset(cx, cy))
                drawCircle(Color.White.copy(alpha = 0.9f * (1f - p)), rad, Offset(cx, cy), style = Stroke(1.2f * density))
                drawCircle(Color.White.copy(alpha = 0.9f * (1f - p)), rad * 0.28f, Offset(cx - rad * 0.35f, cy - rad * 0.35f))
            }
        }
        content()
    }
}

// ------------------------------------------------------------------ glass surface

/** Frosted white glass with a bright rim, inner specular highlight and a light sweep running across it. */
fun Modifier.glassSurface(shape: Shape, strong: Boolean = false, shadow: Boolean = true, sheen: Boolean = true, opaque: Boolean = false): Modifier = composed {
    val sweep = if (sheen) {
        val t = rememberInfiniteTransition(label = "sheen")
        val v by t.animateFloat(-1f, 2.2f, infiniteRepeatable(tween(5200, delayMillis = 1800, easing = FastOutSlowInEasing)), label = "sweep")
        v
    } else -2f
    this
        .then(if (shadow) Modifier.shadow(if (strong) 18.dp else 10.dp, shape, ambientColor = Glass.Shadow.copy(alpha = 0.18f), spotColor = Glass.Shadow.copy(alpha = 0.22f)) else Modifier)
        .clip(shape)
        .background(
            Brush.verticalGradient(
                if (opaque) listOf(Color.White.copy(alpha = 0.985f), Color(0xFFF7F6FF).copy(alpha = 0.97f))
                else if (strong) listOf(Color.White.copy(alpha = 0.92f), Color.White.copy(alpha = 0.72f))
                else listOf(Color.White.copy(alpha = 0.80f), Color.White.copy(alpha = 0.55f)),
            ),
        )
        .border(
            1.2.dp,
            Brush.linearGradient(
                listOf(Color.White, Color.White.copy(alpha = 0.55f), Glass.Violet.copy(alpha = 0.18f), Color.White.copy(alpha = 0.9f)),
            ),
            shape,
        )
        .drawWithContent {
            // soft inner top highlight
            drawRect(
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.85f), 0.25f to Color.Transparent, endY = size.height * 0.5f),
                size = Size(size.width, size.height * 0.5f),
            )
            drawContent()
            // moving diagonal light sweep
            if (sweep > -1f && sweep < 2f) {
                val x = size.width * sweep
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                        start = Offset(x - size.width * 0.35f, 0f),
                        end = Offset(x, size.height),
                        tileMode = TileMode.Clamp,
                    ),
                )
            }
        }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    padding: PaddingValues = PaddingValues(18.dp),
    strong: Boolean = false,
    animate: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Column(
        modifier
            .then(if (animate) Modifier.enterAnimation() else Modifier)
            .then(if (onClick != null) Modifier.liquidClickable(0.97f, onClick = onClick) else Modifier)
            .glassSurface(shape, strong)
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
    val sel by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.6f), label = "pill")
    val fg = if (selected) Glass.OnAccent else Glass.Text
    Box(
        modifier
            .liquidClickable(0.92f, onClick = onClick)
            .glassSurface(shape, shadow = false, sheen = false),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = sel; scaleX = 0.8f + 0.2f * sel; scaleY = 0.8f + 0.2f * sel }
                .clip(shape)
                .background(Glass.accent),
        )
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
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
    val t = rememberInfiniteTransition(label = "btn")
    val flow by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse), label = "flow")
    val glow by t.animateFloat(0.25f, 0.55f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier
            .heightIn(min = 54.dp)
            .graphicsLayer { this.alpha = alpha }
            .liquidClickable(0.94f, enabled = enabled && !loading, onClick = onClick)
            .then(
                if (!secondary && enabled) Modifier.shadow(16.dp, shape, ambientColor = Glass.Violet.copy(alpha = glow), spotColor = Glass.Violet.copy(alpha = glow))
                else Modifier,
            )
            .then(
                if (secondary) Modifier.glassSurface(shape, strong = true, shadow = true, sheen = false)
                else Modifier
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            Glass.accentColors + Glass.accentColors.first(),
                            start = Offset(-400f + 800f * flow, 0f),
                            end = Offset(400f + 800f * flow, 300f),
                            tileMode = TileMode.Mirror,
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.6f), shape),
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (secondary) Glass.Text else Glass.OnAccent
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
            .liquidClickable(0.85f, onClick = onClick)
            .glassSurface(CircleShape, sheen = false),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

// ------------------------------------------------------------------ data visuals

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 14.dp,
    colors: List<Color> = listOf(Glass.Teal, Glass.Sky, Glass.Violet, Glass.Teal),
    trackAlpha: Float = 1f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        animated.animateTo(progress.coerceIn(0f, 1.5f), spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessVeryLow))
    }
    val t = rememberInfiniteTransition(label = "ringglow")
    val pulse by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(size.width - sw, size.height - sw)
            drawArc(Glass.Track.copy(alpha = Glass.Track.alpha * trackAlpha), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
            val value = animated.value
            val sweep = 360f * value.coerceIn(0f, 1f)
            if (sweep > 0.5f) {
                drawArc(
                    Brush.sweepGradient(colors.map { it.copy(alpha = 0.22f * pulse) }), -90f, sweep, false,
                    Offset(inset, inset), arcSize, style = Stroke(sw * 2.1f, cap = StrokeCap.Round),
                )
                drawArc(Brush.sweepGradient(colors), -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                // glowing head
                val angle = Math.toRadians((sweep - 90f).toDouble())
                val r = (size.width - sw) / 2
                val c = Offset(size.width / 2 + (r * cos(angle)).toFloat(), size.height / 2 + (r * sin(angle)).toFloat())
                drawCircle(Color.White, sw * 0.42f, c)
                drawCircle(colors.last().copy(alpha = 0.35f * pulse), sw * 0.9f, c)
            }
            if (value > 1f) {
                drawArc(Glass.Red, -90f, 360f * (value - 1f).coerceAtMost(1f), false, Offset(inset, inset), arcSize, style = Stroke(sw * 0.5f, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Number that counts up/down smoothly when it changes. */
@Composable
fun AnimatedNumber(value: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier, prefix: String = "", suffix: String = "") {
    val v by animateIntAsState(value, tween(900, easing = FastOutSlowInEasing), label = "num")
    val text = "%,d".format(java.util.Locale.forLanguageTag("pl-PL"), v).replace(' ', ' ')
    Text("$prefix$text$suffix", style = style, color = color, modifier = modifier, maxLines = 1)
}

@Composable
fun MacroBar(label: String, value: Double, goal: Int, color: Color, modifier: Modifier = Modifier, unit: String = "g") {
    val p by animateFloatAsState(if (goal > 0) (value / goal).toFloat().coerceIn(0f, 1f) else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow), label = "macro")
    val t = rememberInfiniteTransition(label = "bar")
    val shine by t.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "shine")
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Glass.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Text(if (unit == "%") "${value.toInt()}%" else "${value.toInt()}/${goal} $unit", color = Glass.Text, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(50))
                .background(Glass.Track),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(p)
                    .height(9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color)))
                    .drawWithContent {
                        drawContent()
                        val x = size.width * shine
                        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.Transparent), startX = x - 40f, endX = x + 40f))
                    },
            )
        }
    }
}

/** Glass circle filled with animated water waves – used for the hydration card. */
@Composable
fun WaterWave(level: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val target = level.coerceIn(0f, 1f)
    val anim by animateFloatAsState(target, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow), label = "water")
    val t = rememberInfiniteTransition(label = "wave")
    val shift by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "shift")
    Box(modifier.clip(CircleShape).glassSurface(CircleShape, shadow = false, sheen = false), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val baseY = h * (1f - anim)
            fun wave(phase: Float, amp: Float, color: Color) {
                val path = androidx.compose.ui.graphics.Path()
                path.moveTo(0f, h)
                var x = 0f
                while (x <= w) {
                    val y = baseY + amp * sin((x / w) * 2f * PI.toFloat() * 1.3f + phase)
                    path.lineTo(x, y)
                    x += 4f
                }
                path.lineTo(w, h)
                path.close()
                drawPath(path, color)
            }
            if (anim > 0.001f) {
                wave(shift + 1.6f, 7.dp.toPx(), Glass.Sky.copy(alpha = 0.28f))
                wave(shift, 5.dp.toPx(), Glass.Sky.copy(alpha = 0.45f))
            }
        }
        content()
    }
}

// ------------------------------------------------------------------ text & layout pieces

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .enterAnimation(0)
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
    Row(modifier.fillMaxWidth().enterAnimation().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Glass.Text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action, color = Glass.Violet, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).liquidClickable(0.9f, onClick = onAction).padding(6.dp),
            )
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
        colors = glassFieldColors(),
    )
}

@Composable
fun glassFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Glass.Violet,
    unfocusedBorderColor = Glass.Violet.copy(alpha = 0.22f),
    focusedContainerColor = Color.White.copy(alpha = 0.85f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.6f),
    focusedLabelColor = Glass.Violet,
    unfocusedLabelColor = Glass.TextSecondary,
    cursorColor = Glass.Violet,
    focusedTextColor = Glass.Text,
    unfocusedTextColor = Glass.Text,
)

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
        FloatingEmoji(emoji, 40)
        Spacer(Modifier.height(8.dp))
        Text(title, color = Glass.Text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(text, color = Glass.TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

/** Emoji that gently floats and breathes. */
@Composable
fun FloatingEmoji(emoji: String, sizeSp: Int, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "float")
    val y by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    val s by t.animateFloat(0.96f, 1.06f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    Text(
        emoji, fontSize = sizeSp.sp, textAlign = TextAlign.Center,
        modifier = modifier.graphicsLayer { translationY = y * density; scaleX = s; scaleY = s },
    )
}

@Composable
fun IconBadge(icon: ImageVector, brush: Brush, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val shape = RoundedCornerShape(size * 0.36f)
    Box(
        modifier
            .size(size)
            .shadow(8.dp, shape, ambientColor = Glass.Violet.copy(alpha = 0.3f), spotColor = Glass.Violet.copy(alpha = 0.3f))
            .clip(shape)
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.7f), shape)
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.45f), 0.5f to Color.Transparent))
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun BackRow(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Wstecz", tint = Glass.Text) }
}

/** Glass toast that drops in from the top ("Dodano!"). */
@Composable
fun GlassToast(message: String?, modifier: Modifier = Modifier) {
    val visible = message != null
    val last = remember { androidx.compose.runtime.mutableStateOf("") }
    if (message != null) last.value = message
    val p by animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "toast")
    if (p <= 0.01f && !visible) return
    Row(
        modifier
            .graphicsLayer {
                alpha = p.coerceIn(0f, 1f)
                translationY = (1f - p) * -80.dp.toPx()
                scaleX = 0.85f + 0.15f * p
                scaleY = 0.85f + 0.15f * p
            }
            .glassSurface(RoundedCornerShape(50), strong = true, sheen = false)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Glass.accent), contentAlignment = Alignment.Center) {
            Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(last.value, color = Glass.Text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
