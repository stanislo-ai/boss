package com.stopfat.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.stopfat.app.ui.theme.Glass
import kotlin.math.max
import kotlin.math.min

data class ChartPoint(val x: Float, val y: Float)

/**
 * Line chart with optional second (dashed) series and a horizontal target line.
 * X values are arbitrary (e.g. epoch days) – both series share the same axes.
 */
@Composable
fun LineChart(
    series: List<ChartPoint>,
    modifier: Modifier = Modifier,
    secondary: List<ChartPoint> = emptyList(),
    target: Float? = null,
    color: Color = Glass.Teal,
    secondaryColor: Color = Glass.Violet,
    yLabel: (Float) -> String = { "%.1f".format(it) },
) {
    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        val all = series + secondary
        if (all.isEmpty()) return@Canvas
        var minY = all.minOf { it.y }
        var maxY = all.maxOf { it.y }
        if (target != null) {
            minY = min(minY, target)
            maxY = max(maxY, target)
        }
        val pad = max(0.5f, (maxY - minY) * 0.12f)
        minY -= pad
        maxY += pad
        val minX = all.minOf { it.x }
        val maxX = max(all.maxOf { it.x }, minX + 1f)
        val left = 44.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val top = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        fun px(p: ChartPoint) = Offset(left + (p.x - minX) / (maxX - minX) * (right - left), bottom - (p.y - minY) / (maxY - minY) * (bottom - top))

        // grid + labels
        val paint = android.graphics.Paint().apply {
            this.color = android.graphics.Color.argb(150, 255, 255, 255)
            textSize = 10.dp.toPx()
            isAntiAlias = true
        }
        for (i in 0..3) {
            val v = minY + (maxY - minY) * i / 3f
            val y = bottom - (v - minY) / (maxY - minY) * (bottom - top)
            drawLine(Color.White.copy(alpha = 0.08f), Offset(left, y), Offset(right, y), 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(yLabel(v), 0f, y + 4.dp.toPx(), paint)
        }
        if (target != null) {
            val y = bottom - (target - minY) / (maxY - minY) * (bottom - top)
            drawLine(Glass.Amber, Offset(left, y), Offset(right, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
        }
        if (secondary.size >= 2) {
            val path = Path()
            secondary.forEachIndexed { i, p -> val o = px(p); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
            drawPath(path, secondaryColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
        }
        if (series.isNotEmpty()) {
            val path = Path()
            val fill = Path()
            series.forEachIndexed { i, p ->
                val o = px(p)
                if (i == 0) {
                    path.moveTo(o.x, o.y); fill.moveTo(o.x, bottom); fill.lineTo(o.x, o.y)
                } else {
                    path.lineTo(o.x, o.y); fill.lineTo(o.x, o.y)
                }
            }
            val last = px(series.last())
            fill.lineTo(last.x, bottom)
            fill.close()
            if (series.size >= 2) {
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), startY = top, endY = bottom))
                drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            series.forEach { p -> drawCircle(Color.White, 3.5.dp.toPx(), px(p)); drawCircle(color, 2.dp.toPx(), px(p)) }
        }
    }
}

data class BarValue(val label: String, val value: Float, val highlight: Boolean = false)

@Composable
fun BarChart(
    values: List<BarValue>,
    modifier: Modifier = Modifier,
    goal: Float? = null,
    color: Color = Glass.Teal,
    overColor: Color = Glass.Pink,
) {
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            if (values.isEmpty()) return@Canvas
            val maxV = max(values.maxOf { it.value }, goal ?: 0f).coerceAtLeast(1f) * 1.1f
            val n = values.size
            val slot = size.width / n
            val barW = slot * 0.55f
            values.forEachIndexed { i, v ->
                val h = v.value / maxV * size.height
                val x = i * slot + (slot - barW) / 2
                val over = goal != null && v.value > goal * 1.05f
                drawRoundRect(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2))
                if (h > 0) {
                    drawRoundRect(
                        Brush.verticalGradient(
                            if (over) listOf(overColor, overColor.copy(alpha = 0.5f)) else listOf(color, color.copy(alpha = 0.45f)),
                            startY = size.height - h, endY = size.height,
                        ),
                        Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 2),
                    )
                }
            }
            if (goal != null) {
                val y = size.height - goal / maxV * size.height
                drawLine(Glass.Amber, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            values.forEach {
                Text(it.label, color = if (it.highlight) Glass.Teal else Glass.TextTertiary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Glass.TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}
