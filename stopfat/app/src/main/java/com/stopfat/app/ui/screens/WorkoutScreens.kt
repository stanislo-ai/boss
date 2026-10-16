package com.stopfat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stopfat.app.data.HomeEquipment
import com.stopfat.app.data.WorkoutGoal
import com.stopfat.app.data.WorkoutLevel
import com.stopfat.app.data.WorkoutLocation
import com.stopfat.app.data.WorkoutPreferences
import com.stopfat.app.ui.MainViewModel
import com.stopfat.app.ui.Nav
import com.stopfat.app.ui.Routes
import com.stopfat.app.ui.components.GlassButton
import com.stopfat.app.ui.components.GlassCard
import com.stopfat.app.ui.components.GlassPill
import com.stopfat.app.ui.components.GlassTextField
import com.stopfat.app.ui.components.IconBadge
import com.stopfat.app.ui.components.ScreenHeader
import com.stopfat.app.ui.components.SectionTitle
import com.stopfat.app.ui.components.StatChip
import com.stopfat.app.ui.theme.Glass
import kotlinx.coroutines.delay
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutScreen(vm: MainViewModel, nav: Nav) {
    val profile by vm.profile.collectAsState()
    val plan by vm.workoutPlan.collectAsState()
    val savedPrefs by vm.workoutPrefs.collectAsState()
    val busy by vm.planBusy.collectAsState()
    val message by vm.planMessage.collectAsState()
    val logs by vm.workoutLogs.collectAsState()
    val todayLogs by vm.todayWorkouts.collectAsState()
    var editing by rememberSaveable { mutableStateOf(false) }
    var showActivity by remember { mutableStateOf(false) }

    Page(tabPage = true) {
        ScreenHeader("Trening", "Plan AI na siłownię lub do domu")

        // today summary
        GlassCard(Modifier.fillMaxWidth(), strong = true) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatChip("${todayLogs.sumOf { it.kcal }}", "kcal spalone dziś", Glass.Amber)
                StatChip("${todayLogs.sumOf { it.minutes }}", "minut ruchu", Glass.Teal)
                val weekStart = LocalDate.now().toEpochDay() - 6
                StatChip("${logs.count { it.epochDay >= weekStart }}", "treningi (7 dni)", Glass.Violet)
            }
            Spacer(Modifier.height(12.dp))
            GlassButton("Dodaj aktywność (spacer, bieg, rower…)", Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.DirectionsRun, secondary = true) { showActivity = true }
        }

        val p = plan
        if (p == null || editing) {
            PlanSetup(
                initial = savedPrefs,
                hasAi = profile.hasAi,
                busy = busy,
                onCancel = if (p != null) ({ editing = false }) else null,
            ) { prefs, ai ->
                vm.generatePlan(prefs, ai)
                editing = false
            }
        } else {
            SectionTitle("Twój plan", action = "Nowy plan", onAction = { editing = true })
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(if (p.source.startsWith("AI")) Icons.Rounded.AutoAwesome else Icons.Rounded.FitnessCenter, Glass.accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.title, color = Glass.Text, style = MaterialTheme.typography.titleMedium)
                        Text(p.source, color = Glass.Teal, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (p.summary.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(p.summary, color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (busy) Text("Generuję nowy plan…", color = Glass.Teal, modifier = Modifier.padding(top = 8.dp))
            message?.let { ErrorBanner(it) }
            p.days.forEachIndexed { i, day ->
                Spacer(Modifier.height(10.dp))
                GlassCard(Modifier.fillMaxWidth(), corner = 24.dp, onClick = { nav.go(Routes.session(i)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(day.name, color = Glass.Text, style = MaterialTheme.typography.titleMedium)
                            Text("${day.exercises.size} ${exercisesWord(day.exercises.size)} · ${day.focus}", color = Glass.TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(Glass.accent),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.PlayArrow, "Start", tint = Color(0xFF07122A)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    day.exercises.take(4).forEach { e ->
                        Text("• ${e.name} — ${e.sets}×${e.reps}", color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (day.exercises.size > 4) Text("+ ${day.exercises.size - 4} więcej", color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (p.tips.isNotEmpty()) {
                SectionTitle("Wskazówki")
                GlassCard(Modifier.fillMaxWidth(), corner = 22.dp) {
                    p.tips.forEach { Text("💡 $it", color = Glass.Text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }
                }
            }
        }

        if (logs.isNotEmpty()) {
            SectionTitle("Historia")
            GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                logs.take(12).forEach { log ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(log.name, color = Glass.Text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text("${dayLabel(log.epochDay)} · ${log.minutes} min" + if (log.completedSets > 0) " · ${log.completedSets} ${setsWord(log.completedSets)}" else "", color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${log.kcal} kcal", color = Glass.Amber, style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { vm.deleteWorkout(log.id) }) { Icon(Icons.Rounded.Delete, "Usuń", tint = Glass.TextTertiary, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
    }

    if (showActivity) ActivityDialog(onDismiss = { showActivity = false }) { name, minutes, met ->
        vm.logCustomActivity(name, minutes, met)
        showActivity = false
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanSetup(
    initial: WorkoutPreferences,
    hasAi: Boolean,
    busy: Boolean,
    onCancel: (() -> Unit)?,
    onGenerate: (WorkoutPreferences, Boolean) -> Unit,
) {
    var location by remember(initial) { mutableStateOf(initial.location) }
    var level by remember(initial) { mutableStateOf(initial.level) }
    var goal by remember(initial) { mutableStateOf(initial.goal) }
    var days by remember(initial) { mutableIntStateOf(initial.daysPerWeek) }
    var minutes by remember(initial) { mutableIntStateOf(initial.minutes) }
    var equipment by remember(initial) { mutableStateOf(initial.equipment) }
    var notes by remember(initial) { mutableStateOf(initial.notes) }
    fun prefs() = WorkoutPreferences(location, level, goal, days, minutes, equipment, notes)

    SectionTitle("Stwórz plan treningowy")
    GlassCard(Modifier.fillMaxWidth()) {
        Label("Gdzie trenujesz?")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkoutLocation.entries.forEach { GlassPill(if (it == WorkoutLocation.GYM) "🏋️ ${it.label}" else "🏠 ${it.label}", location == it, Modifier.weight(1f)) { location = it } }
        }
        if (location == WorkoutLocation.HOME) {
            Label("Sprzęt")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeEquipment.entries.forEach { GlassPill(it.label, equipment == it) { equipment = it } }
            }
        }
        Label("Cel")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkoutGoal.entries.forEach { GlassPill(it.label, goal == it) { goal = it } }
        }
        Label("Poziom")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkoutLevel.entries.forEach { GlassPill(it.label, level == it) { level = it } }
        }
        Label("Dni w tygodniu")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (2..6).forEach { d -> GlassPill("$d", days == d, Modifier.weight(1f)) { days = d } }
        }
        Label("Czas treningu")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(30, 45, 60, 75).forEach { m -> GlassPill("$m min", minutes == m, Modifier.weight(1f)) { minutes = m } }
        }
        Spacer(Modifier.height(12.dp))
        GlassTextField(notes, { notes = it }, "Kontuzje / preferencje (opcjonalnie)", singleLine = false, placeholder = "np. boli mnie kolano, nie lubię biegać")
        Spacer(Modifier.height(14.dp))
        if (hasAi) {
            GlassButton("Generuj plan z AI", Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome, loading = busy) { onGenerate(prefs(), true) }
            Spacer(Modifier.height(8.dp))
        }
        GlassButton(if (hasAi) "Szybki plan offline" else "Generuj plan", Modifier.fillMaxWidth(), icon = Icons.Rounded.Bolt, secondary = hasAi, enabled = !busy) { onGenerate(prefs(), false) }
        if (!hasAi) Text("Dodaj klucz API w Ustawieniach, aby AI ułożyło w pełni spersonalizowany plan.", color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
        if (onCancel != null) {
            Spacer(Modifier.height(8.dp))
            GlassButton("Anuluj", Modifier.fillMaxWidth(), secondary = true, onClick = onCancel)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Glass.TextSecondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
}

@Composable
private fun ActivityDialog(onDismiss: () -> Unit, onSave: (String, Int, Double) -> Unit) {
    val activities = listOf(
        "🚶 Spacer" to 3.5, "🏃 Bieg" to 9.0, "🚴 Rower" to 7.0, "🏊 Pływanie" to 7.0,
        "💃 Taniec" to 5.5, "🧘 Joga" to 2.8, "⚽ Piłka nożna" to 7.0, "🏸 Tenis / squash" to 7.3,
        "🏋️ Siłownia" to 5.0, "🥊 Sztuki walki" to 9.0, "🧗 Wspinaczka" to 8.0, "🧹 Prace domowe" to 3.3,
    )
    var selected by remember { mutableStateOf(activities.first()) }
    var minutes by remember { mutableStateOf("30") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Glass.Dialog,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Dodaj aktywność", color = Glass.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                activities.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { a -> GlassPill(a.first, selected == a, Modifier.weight(1f)) { selected = a } }
                    }
                }
                GlassTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, "Czas", keyboardType = KeyboardType.Number, suffix = "min")
            }
        },
        confirmButton = {
            val m = minutes.toIntOrNull() ?: 0
            TextButton(onClick = { if (m > 0) onSave(selected.first.drop(2).trim(), m, selected.second) }, enabled = m > 0) {
                Text("Zapisz", color = Glass.Teal, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj", color = Glass.TextSecondary) } },
    )
}

// ---------------------------------------------------------------- session

@Composable
fun WorkoutSessionScreen(vm: MainViewModel, nav: Nav, dayIndex: Int) {
    val plan by vm.workoutPlan.collectAsState()
    val day = plan?.days?.getOrNull(dayIndex)
    if (day == null) {
        Page { ScreenHeader("Trening", "Nie znaleziono dnia planu", onBack = nav::back) }
        return
    }
    val done = remember { mutableStateMapOf<String, Boolean>() }
    val startedAt = rememberSaveable { System.currentTimeMillis() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var restUntil by remember { mutableLongStateOf(0L) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val totalSets = day.exercises.sumOf { it.sets }
    val doneSets = done.count { it.value }
    val elapsedMin = ((now - startedAt) / 60_000).toInt()

    Page {
        ScreenHeader(day.name, day.focus, onBack = nav::back)
        GlassCard(Modifier.fillMaxWidth(), strong = true) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val secs = (now - startedAt) / 1000
                StatChip("%d:%02d".format(secs / 60, secs % 60), "czas", Glass.Teal)
                StatChip("$doneSets/$totalSets", "serie", Glass.Violet)
                val rest = ((restUntil - now) / 1000).coerceAtLeast(0)
                StatChip(if (rest > 0) "${rest}s" else "—", "przerwa", if (rest > 0) Glass.Amber else Glass.TextTertiary)
            }
        }
        day.exercises.forEachIndexed { ei, ex ->
            Spacer(Modifier.height(10.dp))
            GlassCard(Modifier.fillMaxWidth(), corner = 22.dp) {
                Text(ex.name, color = Glass.Text, style = MaterialTheme.typography.titleMedium)
                Text("${ex.sets} × ${ex.reps}" + if (ex.restSec > 0) " · przerwa ${ex.restSec}s" else "", color = Glass.Teal, style = MaterialTheme.typography.labelLarge)
                if (ex.tip.isNotBlank()) Text(ex.tip, color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..ex.sets).forEach { s ->
                        val key = "$ei-$s"
                        val isDone = done[key] == true
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isDone) Glass.accent else Brush.linearGradient(listOf(Glass.Fill, Glass.Fill)))
                                .border(1.dp, Color.White.copy(alpha = if (isDone) 0.6f else 0.25f), CircleShape)
                                .clickable {
                                    done[key] = !isDone
                                    if (!isDone && ex.restSec > 0) restUntil = System.currentTimeMillis() + ex.restSec * 1000L
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isDone) Icon(Icons.Rounded.Check, null, tint = Color(0xFF07122A), modifier = Modifier.size(20.dp))
                            else Text("$s", color = Glass.Text, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        if (day.finisher.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            GlassCard(Modifier.fillMaxWidth(), corner = 22.dp) {
                Text("🔥 Finisher", color = Glass.Amber, style = MaterialTheme.typography.titleSmall)
                Text(day.finisher, color = Glass.Text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        GlassButton(if (finished) "Zapisano ✓" else "Zakończ trening", Modifier.fillMaxWidth(), icon = Icons.Rounded.Check, enabled = !finished && doneSets > 0) {
            vm.logWorkout(day.name.substringAfter(": "), elapsedMin.coerceAtLeast(5), doneSets)
            finished = true
            nav.back()
        }
        if (doneSets == 0) Text("Odhaczaj wykonane serie – po każdej uruchomi się minutnik przerwy.", color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { done.clear(); restUntil = 0 }) {
                Icon(Icons.Rounded.Refresh, null, tint = Glass.TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Resetuj serie", color = Glass.TextSecondary)
            }
        }
    }
}

fun exercisesWord(n: Int): String = when {
    n == 1 -> "ćwiczenie"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "ćwiczenia"
    else -> "ćwiczeń"
}

fun setsWord(n: Int): String = when {
    n == 1 -> "seria"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "serie"
    else -> "serii"
}
