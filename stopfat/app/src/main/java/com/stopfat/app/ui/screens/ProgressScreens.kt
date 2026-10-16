package com.stopfat.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stopfat.app.domain.AchievementInput
import com.stopfat.app.domain.Achievements
import com.stopfat.app.domain.Nutrition
import com.stopfat.app.domain.PlanSafety
import com.stopfat.app.domain.WeightLossPlanner
import com.stopfat.app.ui.MainViewModel
import com.stopfat.app.ui.Nav
import com.stopfat.app.ui.Routes
import com.stopfat.app.ui.components.BarChart
import com.stopfat.app.ui.components.BarValue
import com.stopfat.app.ui.components.ChartPoint
import com.stopfat.app.ui.components.GlassButton
import com.stopfat.app.ui.components.GlassCard
import com.stopfat.app.ui.components.GlassPill
import com.stopfat.app.ui.components.GlassTextField
import com.stopfat.app.ui.components.Legend
import com.stopfat.app.ui.components.LineChart
import com.stopfat.app.ui.components.MarkdownText
import com.stopfat.app.ui.components.ProgressRing
import com.stopfat.app.ui.components.ScreenHeader
import com.stopfat.app.ui.components.SectionTitle
import com.stopfat.app.ui.components.StatChip
import com.stopfat.app.ui.theme.Glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(vm: MainViewModel, nav: Nav) {
    val profile by vm.profile.collectAsState()
    val weights by vm.weights.collectAsState()
    val kcal7 by vm.kcalLast7.collectAsState()
    val stats by vm.allStats.collectAsState()
    val loggedDays by vm.loggedDays.collectAsState()
    val workouts by vm.workoutLogs.collectAsState()
    val fasts by vm.fastingLogs.collectAsState()
    val scans by vm.aiScanCount.collectAsState()
    var showWeight by remember { mutableStateOf(false) }

    val today = LocalDate.now().toEpochDay()
    val goal = Nutrition.calorieGoal(profile)
    val plan = WeightLossPlanner.plan(profile)

    Page(tabPage = true) {
        ScreenHeader("Postęp", "Waga, kalorie, kroki i osiągnięcia")

        // Weight
        GlassCard(Modifier.fillMaxWidth(), strong = true) {
            val start = weights.firstOrNull()?.kg ?: profile.weightKg
            val change = profile.weightKg - start
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Aktualna waga", color = Glass.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    Text("${profile.weightKg.oneDecimal()} kg", color = Glass.Text, style = MaterialTheme.typography.headlineLarge)
                    Text(
                        (if (change <= 0) "▼ ${(-change).oneDecimal()} kg" else "▲ ${change.oneDecimal()} kg") + " od startu",
                        color = if (change <= 0) Glass.Teal else Glass.Pink, style = MaterialTheme.typography.labelLarge,
                    )
                }
                GlassButton("Waga", icon = Icons.Rounded.Add) { showWeight = true }
            }
            Spacer(Modifier.height(12.dp))
            if (weights.size >= 2) {
                LineChart(weights.map { ChartPoint(it.epochDay.toFloat(), it.kg.toFloat()) }, target = profile.targetWeightKg.toFloat())
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(Glass.Teal, "Twoja waga")
                    Legend(Glass.Amber, "Cel ${profile.targetWeightKg.oneDecimal()} kg")
                }
            } else {
                Text("Dodawaj pomiary wagi 2–3 razy w tygodniu, aby zobaczyć wykres trendu.", color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }

        // Plan
        SectionTitle("Plan odchudzania", action = "Szczegóły", onAction = { nav.go(Routes.ANALYSIS) })
        GlassCard(Modifier.fillMaxWidth(), onClick = { nav.go(Routes.ANALYSIS) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(plan.safety.label, color = safetyColor(plan.safety), style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (plan.kgToLose > 0) "−${plan.kgToLose.oneDecimal()} kg w ${plan.daysLeft} dni · ${"%.2f".format(PL, plan.weeklyLossKg)} kg/tydz."
                        else "Utrzymanie wagi",
                        color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Prognoza na ${WeightLossPlanner.formatDate(profile.targetDate)}: ${plan.expectedWeightAtTarget.oneDecimal()} kg", color = Glass.Text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                Icon(Icons.Rounded.AutoAwesome, null, tint = Glass.Teal)
            }
        }

        // Calories chart
        SectionTitle("Kalorie – 7 dni")
        GlassCard(Modifier.fillMaxWidth()) {
            val map = kcal7.associate { it.epochDay to it.kcal }
            val values = (6 downTo 0).map { d -> val day = today - d; BarValue(shortDay(day), (map[day] ?: 0.0).toFloat(), day == today) }
            val logged = values.filter { it.value > 0 }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatChip(if (logged.isEmpty()) "—" else logged.map { it.value }.average().roundToInt().thousands(), "średnio kcal", Glass.Text)
                StatChip(goal.thousands(), "cel kcal", Glass.Amber)
                StatChip("${logged.count { it.value <= goal * 1.05f }}/${logged.size}", "dni w limicie", Glass.Teal)
            }
            Spacer(Modifier.height(14.dp))
            BarChart(values, goal = goal.toFloat())
        }

        // Steps chart
        SectionTitle("Kroki – 7 dni")
        GlassCard(Modifier.fillMaxWidth()) {
            val map = stats.associate { it.epochDay to it.steps }
            val values = (6 downTo 0).map { d -> val day = today - d; BarValue(shortDay(day), (map[day] ?: 0).toFloat(), day == today) }
            val total = values.sumOf { it.value.toDouble() }.toInt()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatChip((total / 7).thousands(), "średnio/dzień", Glass.Lime)
                StatChip(total.thousands(), "w tygodniu", Glass.Text)
                StatChip("${Nutrition.stepsToKcal(total, profile.weightKg).roundToInt().thousands()}", "kcal spalone", Glass.Amber)
            }
            Spacer(Modifier.height(14.dp))
            BarChart(values, goal = profile.stepGoal.toFloat(), color = Glass.Lime, overColor = Glass.Teal)
        }

        // Body
        SectionTitle("Ciało")
        GlassCard(Modifier.fillMaxWidth()) {
            val bmi = Nutrition.bmi(profile.weightKg, profile.heightCm)
            val bf = Nutrition.bodyFatPercent(profile)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatChip(bmi.oneDecimal(), "BMI", Glass.Sky)
                StatChip("${bf.roundToInt()}%", "tkanka tłuszcz. (szac.)", Glass.Pink)
                StatChip("${plan.tdee.thousands()}", "TDEE kcal", Glass.Amber)
            }
            Spacer(Modifier.height(8.dp))
            Text(Nutrition.bmiCategory(bmi), color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }

        // Achievements
        val achievements = Achievements.compute(
            AchievementInput(
                loggedDays, today, stats.associate { it.epochDay to it.steps }, profile.stepGoal,
                stats.associate { it.epochDay to it.waterMl }, profile.waterGoalMl, workouts.size,
                weights.firstOrNull()?.kg, weights.lastOrNull()?.kg, fasts.count { it.endMillis - it.startMillis >= it.targetHours * 3_600_000L }, scans,
            ),
        )
        SectionTitle("Osiągnięcia", action = "Wszystkie", onAction = { nav.go(Routes.ACHIEVEMENTS) })
        GlassCard(Modifier.fillMaxWidth(), onClick = { nav.go(Routes.ACHIEVEMENTS) }) {
            val unlocked = achievements.filter { it.unlocked }
            Text("${unlocked.size} z ${achievements.size} odblokowanych", color = Glass.Text, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (unlocked.takeLast(7).ifEmpty { achievements.take(5) }).forEach { a ->
                    Text(a.emoji, fontSize = 28.sp, modifier = Modifier.padding(2.dp), color = if (a.unlocked) Glass.Text else Glass.TextTertiary)
                }
            }
            Text("🔥 Seria: ${Achievements.streak(loggedDays, today)} dni · rekord: ${Achievements.bestStreak(loggedDays)} dni", color = Glass.Amber, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        }

        // Weight log list
        if (weights.isNotEmpty()) {
            SectionTitle("Pomiary")
            GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                weights.reversed().take(10).forEach { w ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(dayLabel(w.epochDay), color = Glass.TextSecondary, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${w.kg.oneDecimal()} kg", color = Glass.Text, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { vm.deleteWeight(w.epochDay) }) { Icon(Icons.Rounded.Delete, "Usuń", tint = Glass.TextTertiary, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
    }

    if (showWeight) WeightDialog(profile.weightKg, onDismiss = { showWeight = false }) { kg, day ->
        vm.logWeight(kg, day)
        showWeight = false
    }
}

@Composable
fun WeightDialog(current: Double, onDismiss: () -> Unit, onSave: (Double, Long) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(current.oneDecimal()) }
    var day by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val kg = parseNumber(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Glass.Dialog,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Nowy pomiar wagi", color = Glass.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassTextField(text, { text = it.take(6) }, "Waga", keyboardType = KeyboardType.Decimal, suffix = "kg")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-0.5, -0.1, 0.1, 0.5).forEach { d ->
                        GlassPill((if (d > 0) "+" else "") + d.toString().replace('.', ','), false, Modifier.weight(1f)) {
                            text = ((kg ?: current) + d).coerceIn(30.0, 350.0).oneDecimal()
                        }
                    }
                }
                GlassPill(dayLabel(day), false, icon = Icons.Rounded.CalendarMonth) {
                    val d = LocalDate.ofEpochDay(day)
                    DatePickerDialog(context, { _, y, m, dd -> day = LocalDate.of(y, m + 1, dd).toEpochDay() }, d.year, d.monthValue - 1, d.dayOfMonth).apply {
                        datePicker.maxDate = System.currentTimeMillis()
                    }.show()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (kg != null && kg in 30.0..350.0) onSave(kg, day) }, enabled = kg != null && kg in 30.0..350.0) {
                Text("Zapisz", color = Glass.Teal, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj", color = Glass.TextSecondary) } },
    )
}

// ---------------------------------------------------------------- analysis

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisScreen(vm: MainViewModel, nav: Nav) {
    val context = LocalContext.current
    val profile by vm.profile.collectAsState()
    val weights by vm.weights.collectAsState()
    val kcal7 by vm.kcalLast7.collectAsState()
    val stats by vm.allStats.collectAsState()
    val scope = rememberCoroutineScope()
    var aiText by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf(false) }
    val plan = WeightLossPlanner.plan(profile)
    val today = LocalDate.now()

    Page {
        ScreenHeader("Analiza celu", "Jak schudnąć ${plan.kgToLose.coerceAtLeast(0.0).oneDecimal()} kg do ${WeightLossPlanner.formatDate(profile.targetDate)}", onBack = nav::back)

        GlassCard(Modifier.fillMaxWidth(), strong = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val progress = if (plan.kgToLose > 0) (plan.weeklyLossPercent / 1.5).toFloat() else 0f
                ProgressRing(progress, Modifier.size(96.dp), stroke = 10.dp, colors = listOf(Glass.Teal, Glass.Amber, Glass.Red, Glass.Teal)) {
                    Text("${"%.1f".format(PL, plan.weeklyLossPercent)}%", color = Glass.Text, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(plan.safety.label, color = safetyColor(plan.safety), style = MaterialTheme.typography.titleLarge)
                    Text(plan.safety.description, color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            GlassPill("Zmień cel lub datę", false, icon = Icons.Rounded.CalendarMonth) { editTarget = true }
        }

        SectionTitle("Liczby")
        GlassCard(Modifier.fillMaxWidth()) {
            NumberRow("Do zrzucenia", "${plan.kgToLose.coerceAtLeast(0.0).oneDecimal()} kg")
            NumberRow("Czas", "${plan.daysLeft} dni (${(plan.weeksLeft).oneDecimal()} tyg.)")
            NumberRow("Tempo", "${"%.2f".format(PL, plan.weeklyLossKg)} kg/tydz.")
            NumberRow("Przemiana materii (BMR)", "${plan.bmr.thousands()} kcal")
            NumberRow("Wydatek dzienny (TDEE)", "${plan.tdee.thousands()} kcal")
            NumberRow("Wymagany deficyt", "${plan.requiredDailyDeficit.thousands()} kcal/dzień")
            NumberRow("Kalorie wg terminu", "${plan.requiredIntake.thousands()} kcal", if (plan.requiredIntake < plan.minimumIntake) Glass.Red else Glass.Text)
            NumberRow("Zalecane kalorie", "${plan.recommendedIntake.thousands()} kcal", Glass.Teal)
            NumberRow("Bezpieczne minimum", "${plan.minimumIntake.thousands()} kcal")
            if (plan.extraStepsNeeded > 0) NumberRow("Dodatkowy ruch", "+${plan.extraStepsNeeded.thousands()} kroków/dzień", Glass.Amber)
            NumberRow("Bezpieczna data celu", WeightLossPlanner.formatDate(plan.safeTargetDate))
            NumberRow("Prognoza w dniu celu", "${plan.expectedWeightAtTarget.oneDecimal()} kg", Glass.Violet)
            val macros = Nutrition.macroTargets(profile, plan.recommendedIntake)
            NumberRow("Makro (B/W/T)", "${macros.proteinG} / ${macros.carbsG} / ${macros.fatG} g")
        }

        SectionTitle("Prognoza wagi")
        GlassCard(Modifier.fillMaxWidth()) {
            val proj = plan.projection.map { ChartPoint(it.day.toFloat(), it.weightKg.toFloat()) }
            val actual = weights.filter { it.epochDay >= today.toEpochDay() - 60 }
                .map { ChartPoint((it.epochDay - today.toEpochDay()).toFloat(), it.kg.toFloat()) }
            LineChart(actual, secondary = proj, target = profile.targetWeightKg.toFloat())
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legend(Glass.Violet, "Prognoza")
                Legend(Glass.Teal, "Pomiary")
                Legend(Glass.Amber, "Cel")
            }
            Text("Prognoza uwzględnia spowolnienie metabolizmu wraz ze spadkiem masy ciała.", color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
        }

        if (plan.milestones.isNotEmpty()) {
            SectionTitle("Kamienie milowe")
            GlassCard(Modifier.fillMaxWidth(), corner = 22.dp) {
                plan.milestones.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.label, color = Glass.Text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${m.weightKg.oneDecimal()} kg", color = Glass.Teal, fontWeight = FontWeight.Bold)
                            Text(WeightLossPlanner.formatDate(m.date), color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        SectionTitle("Rekomendacje")
        GlassCard(Modifier.fillMaxWidth(), corner = 22.dp) {
            plan.tips.forEach { Text("✅ $it", color = Glass.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp)) }
        }

        SectionTitle("Szczegółowa analiza AI")
        GlassCard(Modifier.fillMaxWidth()) {
            val text = aiText
            if (text != null) {
                MarkdownText(text)
            } else {
                Text(
                    "AI przeanalizuje Twoje dane, dziennik z ostatnich 7 dni i trend wagi, a następnie przygotuje plan krok po kroku: kalorie, ruch, przykładowy jadłospis i co robić przy zastoju.",
                    color = Glass.TextSecondary, style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (busy) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)), color = Glass.Teal, trackColor = Glass.Fill)
                Text("AI analizuje… to może potrwać do minuty.", color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
            }
            ErrorBanner(error)
            Spacer(Modifier.height(12.dp))
            GlassButton(
                if (text == null) "Generuj analizę AI" else "Odśwież analizę", Modifier.fillMaxWidth(),
                icon = Icons.Rounded.AutoAwesome, loading = busy, enabled = profile.hasAi,
            ) {
                busy = true
                error = null
                scope.launch {
                    try {
                        val service = vm.ai() ?: throw IllegalStateException("Brak klucza API")
                        val logged = kcal7.filter { it.kcal > 0 }
                        val avgKcal = if (logged.isEmpty()) null else logged.map { it.kcal }.average().roundToInt()
                        val week = stats.filter { it.epochDay > today.toEpochDay() - 7 }
                        val avgSteps = if (week.isEmpty()) null else week.map { it.steps }.average().roundToInt()
                        val trend = if (weights.size >= 2) {
                            val first = weights.first(); val last = weights.last()
                            "z ${first.kg.oneDecimal()} kg (${dayLabel(first.epochDay)}) na ${last.kg.oneDecimal()} kg (${dayLabel(last.epochDay)})"
                        } else "brak historii"
                        aiText = service.weightLossAnalysis(profile, avgKcal, avgSteps, trend)
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        busy = false
                    }
                }
            }
            if (!profile.hasAi) Text("Dodaj klucz API w Ustawieniach, aby uzyskać analizę AI.", color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
        }
        Text(
            "StopFat nie zastępuje porady lekarza ani dietetyka. Przy chorobach przewlekłych, w ciąży lub przy zaburzeniach odżywiania skonsultuj plan ze specjalistą.",
            color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 14.dp),
        )
    }

    if (editTarget) {
        var target by remember { mutableStateOf(profile.targetWeightKg.oneDecimal()) }
        var date by remember { mutableLongStateOf(profile.targetDateEpochDay) }
        AlertDialog(
            onDismissRequest = { editTarget = false },
            containerColor = Glass.Dialog,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Twój cel", color = Glass.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassTextField(target, { target = it.take(6) }, "Waga docelowa", keyboardType = KeyboardType.Decimal, suffix = "kg")
                    GlassPill(WeightLossPlanner.formatDate(LocalDate.ofEpochDay(date)), false, icon = Icons.Rounded.CalendarMonth) {
                        val d = LocalDate.ofEpochDay(date)
                        DatePickerDialog(context, { _, y, m, dd -> date = LocalDate.of(y, m + 1, dd).toEpochDay() }, d.year, d.monthValue - 1, d.dayOfMonth).apply {
                            datePicker.minDate = System.currentTimeMillis() + 86_400_000L
                        }.show()
                    }
                    if (plan.safety == PlanSafety.TOO_FAST || plan.safety == PlanSafety.AMBITIOUS) {
                        GlassPill("Ustaw bezpieczną datę: ${WeightLossPlanner.formatDate(plan.safeTargetDate)}", false) { date = plan.safeTargetDate.toEpochDay() }
                    }
                }
            },
            confirmButton = {
                val t = parseNumber(target)
                TextButton(onClick = {
                    if (t != null && t in 30.0..350.0) {
                        vm.saveProfile(profile.copy(targetWeightKg = t, targetDateEpochDay = date))
                        aiText = null
                        editTarget = false
                    }
                }) { Text("Zapisz", color = Glass.Teal, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { editTarget = false }) { Text("Anuluj", color = Glass.TextSecondary) } },
        )
    }
}

@Composable
private fun NumberRow(label: String, value: String, color: androidx.compose.ui.graphics.Color = Glass.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Glass.TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

// ---------------------------------------------------------------- achievements

@Composable
fun AchievementsScreen(vm: MainViewModel, nav: Nav) {
    val profile by vm.profile.collectAsState()
    val weights by vm.weights.collectAsState()
    val stats by vm.allStats.collectAsState()
    val loggedDays by vm.loggedDays.collectAsState()
    val workouts by vm.workoutLogs.collectAsState()
    val fasts by vm.fastingLogs.collectAsState()
    val scans by vm.aiScanCount.collectAsState()
    val today = LocalDate.now().toEpochDay()
    val list = Achievements.compute(
        AchievementInput(
            loggedDays, today, stats.associate { it.epochDay to it.steps }, profile.stepGoal,
            stats.associate { it.epochDay to it.waterMl }, profile.waterGoalMl, workouts.size,
            weights.firstOrNull()?.kg, weights.lastOrNull()?.kg, fasts.count { it.endMillis - it.startMillis >= it.targetHours * 3_600_000L }, scans,
        ),
    )
    Page {
        ScreenHeader("Osiągnięcia", "${list.count { it.unlocked }} / ${list.size} odblokowanych", onBack = nav::back)
        list.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                row.forEach { a ->
                    GlassCard(Modifier.weight(1f), corner = 22.dp, strong = a.unlocked) {
                        Text(a.emoji, fontSize = 34.sp, color = if (a.unlocked) Glass.Text else Glass.TextTertiary)
                        Text(a.title, color = if (a.unlocked) Glass.Text else Glass.TextSecondary, style = MaterialTheme.typography.titleSmall)
                        Text(a.description, color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall, minLines = 2)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { a.progress },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                            color = if (a.unlocked) Glass.Teal else Glass.Violet,
                            trackColor = Glass.Fill,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------- fasting

@Composable
fun FastingScreen(vm: MainViewModel, nav: Nav) {
    val profile by vm.profile.collectAsState()
    val logs by vm.fastingLogs.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var hours by remember(profile.fastingHours) { mutableStateOf(profile.fastingHours) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val active = profile.fastingStartMillis > 0
    val elapsed = if (active) (now - profile.fastingStartMillis).coerceAtLeast(0) else 0L
    val target = profile.fastingHours * 3_600_000L

    Page {
        ScreenHeader("Post przerywany", "Okno żywieniowe pod kontrolą", onBack = nav::back)
        GlassCard(Modifier.fillMaxWidth(), strong = true) {
            ProgressRing(
                if (active) elapsed / target.toFloat() else 0f,
                Modifier.size(220.dp).align(Alignment.CenterHorizontally), stroke = 18.dp,
                colors = listOf(Glass.Amber, Glass.Pink, Glass.Violet, Glass.Amber),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (active) {
                        val s = elapsed / 1000
                        Text("%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60), color = Glass.Text, style = MaterialTheme.typography.headlineMedium)
                        val left = (target - elapsed).coerceAtLeast(0) / 60_000
                        Text(if (left > 0) "zostało ${left / 60} h ${left % 60} min" else "Cel osiągnięty! 🎉", color = Glass.Amber, style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text("${hours}:${24 - hours}", color = Glass.Text, style = MaterialTheme.typography.headlineLarge)
                        Text("gotowy do startu", color = Glass.TextSecondary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (!active) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(14, 16, 18, 20, 23).forEach { h -> GlassPill(if (h == 23) "OMAD" else "$h:${24 - h}", hours == h, Modifier.weight(1f)) { hours = h } }
                }
                Spacer(Modifier.height(12.dp))
                GlassButton("Rozpocznij post", Modifier.fillMaxWidth()) { vm.startFast(hours) }
            } else {
                Text("Start: ${java.text.SimpleDateFormat("HH:mm, d MMM", PL).format(java.util.Date(profile.fastingStartMillis))}", color = Glass.TextSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                GlassButton("Zakończ post", Modifier.fillMaxWidth(), secondary = elapsed < target) { vm.stopFast() }
            }
        }
        SectionTitle("Etapy postu")
        GlassCard(Modifier.fillMaxWidth(), corner = 22.dp) {
            listOf(
                4 to "Spada poziom cukru i insuliny we krwi",
                8 to "Organizm zaczyna sięgać po zapasy glikogenu",
                12 to "Rośnie spalanie tłuszczu, pojawiają się ketony",
                16 to "Wyraźnie zwiększona lipoliza – klasyczne 16:8",
                18 to "Nasilona ketoza i autofagia (wg badań na zwierzętach)",
            ).forEach { (h, t) ->
                val reached = active && elapsed >= h * 3_600_000L
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (reached) "✅" else "⏳", fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("$h h", color = if (reached) Glass.Teal else Glass.TextSecondary, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                    Text(t, color = Glass.Text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (logs.isNotEmpty()) {
            SectionTitle("Historia")
            GlassCard(Modifier.fillMaxWidth(), corner = 22.dp) {
                logs.take(10).forEach { l ->
                    val h = (l.endMillis - l.startMillis) / 3_600_000.0
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(java.text.SimpleDateFormat("d MMM, HH:mm", PL).format(java.util.Date(l.startMillis)), color = Glass.TextSecondary, modifier = Modifier.weight(1f))
                        Text("${h.oneDecimal()} h / ${l.targetHours} h", color = if (h >= l.targetHours) Glass.Teal else Glass.Amber, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text("Post przerywany nie jest zalecany w ciąży, przy cukrzycy leczonej insuliną i zaburzeniach odżywiania.", color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 14.dp))
    }
}
