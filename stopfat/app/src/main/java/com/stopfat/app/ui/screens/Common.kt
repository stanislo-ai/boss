package com.stopfat.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stopfat.app.data.MealType
import com.stopfat.app.ui.components.GlassPill
import com.stopfat.app.ui.components.GlassTextField
import com.stopfat.app.ui.theme.Glass
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

val PL: Locale = Locale.forLanguageTag("pl-PL")

/** Standard scrollable page with system-bar padding. [tabPage] adds space for the floating tab bar. */
@Composable
fun Page(tabPage: Boolean = false, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val stagger = androidx.compose.runtime.remember { com.stopfat.app.ui.components.StaggerCounter() }
    androidx.compose.runtime.CompositionLocalProvider(com.stopfat.app.ui.components.LocalStagger provides stagger) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .then(if (tabPage) Modifier else Modifier.navigationBarsPadding().imePadding())
            .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 18.dp),
    ) {
        content()
        Spacer(Modifier.height(if (tabPage) 120.dp else 28.dp))
    }
    }
}

fun Double.kcal(): String = "${roundToInt()} kcal"
fun Double.g(): String = if (this < 10 && this % 1.0 != 0.0) "%.1f g".format(PL, this) else "${roundToInt()} g"
fun Double.oneDecimal(): String = "%.1f".format(PL, this)
fun Int.thousands(): String = "%,d".format(PL, this).replace(' ', ' ')

fun parseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

fun dayLabel(epochDay: Long): String {
    val today = LocalDate.now().toEpochDay()
    val d = LocalDate.ofEpochDay(epochDay)
    return when (epochDay) {
        today -> "Dzisiaj"
        today - 1 -> "Wczoraj"
        else -> "${d.dayOfWeek.getDisplayName(TextStyle.FULL, PL).replaceFirstChar { it.uppercase() }}, ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, PL)}"
    }
}

fun shortDay(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).dayOfWeek.getDisplayName(TextStyle.SHORT, PL).take(2).replaceFirstChar { it.uppercase() }

@Composable
fun MealSelector(selected: MealType, onSelect: (MealType) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MealType.entries.forEach { m ->
            GlassPill("${m.emoji} ${m.label}", selected = m == selected) { onSelect(m) }
        }
    }
}

@Composable
fun MacroLine(kcal: Double, p: Double, c: Double, f: Double, modifier: Modifier = Modifier) {
    // One Text with coloured spans, so it wraps between items instead of inside them in narrow rows.
    val text = androidx.compose.ui.text.buildAnnotatedString {
        withStyle(androidx.compose.ui.text.SpanStyle(color = Glass.Text, fontWeight = FontWeight.Bold)) { append(kcal.kcal().replace(' ', '\u00A0')) }
        append("   ")
        withStyle(androidx.compose.ui.text.SpanStyle(color = Glass.protein)) { append("B\u00A0${p.g().replace(' ', '\u00A0')}") }
        append("  ")
        withStyle(androidx.compose.ui.text.SpanStyle(color = Glass.carbs)) { append("W\u00A0${c.g().replace(' ', '\u00A0')}") }
        append("  ")
        withStyle(androidx.compose.ui.text.SpanStyle(color = Glass.fat)) { append("T\u00A0${f.g().replace(' ', '\u00A0')}") }
    }
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium)
}

/** Dialog asking for an amount in grams (with quick unit buttons) and the meal. */
@Composable
fun AmountDialog(
    title: String,
    per100: com.stopfat.app.data.Nutrients,
    unitGrams: Double,
    unitName: String,
    initialMeal: MealType,
    gi: Int? = null,
    onDismiss: () -> Unit,
    onConfirm: (grams: Double, meal: MealType) -> Unit,
) {
    var gramsText by remember { mutableStateOf(unitGrams.roundToInt().toString()) }
    var meal by remember { mutableStateOf(initialMeal) }
    val grams = parseNumber(gramsText) ?: 0.0
    val n = per100.times(grams / 100.0)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Glass.Dialog,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, color = Glass.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassTextField(gramsText, { gramsText = it }, "Ilość", keyboardType = KeyboardType.Decimal, suffix = "g")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    if (unitGrams != 100.0) GlassPill("1 $unitName", false) { gramsText = unitGrams.roundToInt().toString() }
                    if (unitGrams != 100.0) GlassPill("2 × $unitName", false) { gramsText = (unitGrams * 2).roundToInt().toString() }
                    GlassPill("100 g", false) { gramsText = "100" }
                    GlassPill("50 g", false) { gramsText = "50" }
                    GlassPill("200 g", false) { gramsText = "200" }
                }
                MacroLine(n.kcal, n.protein, n.carbs, n.fat)
                if (gi != null) {
                    val level = com.stopfat.app.domain.Glycemic.level(gi)
                    Text(
                        "${level.label} · ${com.stopfat.app.domain.Glycemic.describe(gi, n.carbs)}" +
                            if (gi > 0) " (ładunek ${com.stopfat.app.domain.Glycemic.loadLabel(com.stopfat.app.domain.Glycemic.load(gi, n.carbs))})" else "",
                        color = giColor(level), style = MaterialTheme.typography.labelLarge,
                    )
                }
                MealSelector(meal) { meal = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (grams > 0) onConfirm(grams, meal) }, enabled = grams > 0) { Text("Dodaj", color = Glass.TealText, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj", color = Glass.TextSecondary) } },
    )
}

@Composable
fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Glass.Dialog,
        title = { Text(title, color = Glass.Text) },
        text = { Text(text, color = Glass.TextSecondary) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = Glass.TealText) } },
    )
}

@Composable
fun ErrorBanner(message: String?, modifier: Modifier = Modifier) {
    if (message.isNullOrBlank()) return
    com.stopfat.app.ui.components.GlassCard(modifier.fillMaxWidth().padding(vertical = 6.dp), corner = 18.dp, padding = androidx.compose.foundation.layout.PaddingValues(14.dp), animate = false) {
        Text("⚠️ $message", color = Glass.AmberText, style = MaterialTheme.typography.bodyMedium)
    }
}
