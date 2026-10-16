package com.stopfat.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stopfat.app.data.MealType
import com.stopfat.app.domain.Nutrition
import com.stopfat.app.domain.WeightLossPlanner
import com.stopfat.app.ui.MainViewModel
import com.stopfat.app.ui.Nav
import com.stopfat.app.ui.Routes
import com.stopfat.app.ui.components.GlassCard
import com.stopfat.app.ui.components.GlassIconButton
import com.stopfat.app.ui.components.IconBadge
import com.stopfat.app.ui.components.MacroBar
import com.stopfat.app.ui.components.ProgressRing
import com.stopfat.app.ui.components.SectionTitle
import com.stopfat.app.ui.theme.Glass
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun HomeScreen(vm: MainViewModel, nav: Nav, onOpenTab: (Int) -> Unit) {
    val profile by vm.profile.collectAsState()
    val entries by vm.todayEntries.collectAsState()
    val stat by vm.todayStat.collectAsState()
    val workouts by vm.todayWorkouts.collectAsState()
    val weights by vm.weights.collectAsState()
    val loggedDays by vm.loggedDays.collectAsState()

    val goal = Nutrition.calorieGoal(profile)
    val macros = Nutrition.macroTargets(profile, goal)
    val eaten = entries.sumOf { it.kcal }
    val burned = workouts.sumOf { it.kcal }
    val remaining = goal + burned - eaten
    val steps = stat?.steps ?: 0
    val water = stat?.waterMl ?: 0
    val streak = com.stopfat.app.domain.Achievements.streak(loggedDays, LocalDate.now().toEpochDay())
    val suggestedMeal = MealType.suggestedForHour(LocalTime.now().hour)

    val stepCounter = vm.container.stepCounter
    var hasStepPermission by remember { mutableStateOf(stepCounter.hasPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasStepPermission = granted
        if (granted) vm.refreshSteps()
    }
    LaunchedEffect(hasStepPermission) { if (hasStepPermission) vm.refreshSteps() }

    Page(tabPage = true) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greeting() + if (profile.name.isNotBlank()) ", ${profile.name}" else "", color = Glass.TextSecondary, style = MaterialTheme.typography.bodyLarge)
                Text("StopFat", style = MaterialTheme.typography.headlineLarge, color = Glass.Text)
            }
            if (streak > 0) {
                GlassCard(corner = 18.dp, padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("🔥 $streak", color = Glass.Amber, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
            }
            GlassIconButton(Icons.Rounded.Settings, "Ustawienia") { nav.go(Routes.SETTINGS) }
        }

        // Calories ring
        GlassCard(Modifier.fillMaxWidth().padding(top = 8.dp), strong = true, onClick = { onOpenTab(1) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing((eaten / max(1, goal + burned)).toFloat(), Modifier.size(150.dp), stroke = 16.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (remaining >= 0) remaining.roundToInt().thousands() else "+${(-remaining).roundToInt().thousands()}",
                            style = MaterialTheme.typography.headlineMedium, color = if (remaining >= 0) Glass.Text else Glass.Red,
                        )
                        Text(if (remaining >= 0) "kcal zostało" else "kcal ponad", color = Glass.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KcalRow("🎯", "Cel", goal.thousands())
                    KcalRow("🍽️", "Zjedzone", eaten.roundToInt().thousands())
                    KcalRow("🔥", "Trening", "+${burned.thousands()}")
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroBar("Białko", entries.sumOf { it.protein }, macros.proteinG, Glass.protein, Modifier.weight(1f))
                MacroBar("Węgle", entries.sumOf { it.carbs }, macros.carbsG, Glass.carbs, Modifier.weight(1f))
                MacroBar("Tłuszcz", entries.sumOf { it.fat }, macros.fatG, Glass.fat, Modifier.weight(1f))
            }
        }

        // Quick add
        SectionTitle("Szybkie dodawanie")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction("Zdjęcie AI", Icons.Rounded.CameraAlt, Glass.accent, Modifier.weight(1f)) { nav.go(Routes.photo(suggestedMeal)) }
            QuickAction("Kod", Icons.Rounded.QrCodeScanner, Brush.linearGradient(listOf(Glass.Sky, Glass.Violet)), Modifier.weight(1f)) { nav.go(Routes.barcode(suggestedMeal)) }
            QuickAction("Opis", Icons.Rounded.EditNote, Brush.linearGradient(listOf(Glass.Pink, Glass.Violet)), Modifier.weight(1f)) { nav.go(Routes.text(suggestedMeal)) }
            QuickAction("Szukaj", Icons.Rounded.Add, Glass.warm, Modifier.weight(1f)) { nav.go(Routes.search(suggestedMeal)) }
        }

        // Steps + water
        SectionTitle("Aktywność i nawodnienie")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.weight(1f), onClick = {
                if (!hasStepPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                else vm.refreshSteps()
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, tint = Glass.Lime, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kroki", color = Glass.TextSecondary, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(10.dp))
                ProgressRing(
                    steps / profile.stepGoal.toFloat().coerceAtLeast(1f),
                    Modifier.size(104.dp).align(Alignment.CenterHorizontally), stroke = 10.dp,
                    colors = listOf(Glass.Lime, Glass.Teal, Glass.Lime),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(steps.thousands(), color = Glass.Text, style = MaterialTheme.typography.titleLarge)
                        Text("/ ${profile.stepGoal.thousands()}", color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        !stepCounter.isAvailable -> "Brak czujnika kroków w telefonie"
                        !hasStepPermission -> "Dotknij, aby połączyć z licznikiem kroków"
                        else -> "${Nutrition.stepsToKcal(steps, profile.weightKg).roundToInt()} kcal · ${Nutrition.stepsToKm(steps, profile.heightCm).oneDecimal()} km"
                    },
                    color = Glass.TextSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            GlassCard(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WaterDrop, null, tint = Glass.Sky, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Woda", color = Glass.TextSecondary, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(10.dp))
                ProgressRing(
                    water / profile.waterGoalMl.toFloat().coerceAtLeast(1f),
                    Modifier.size(104.dp).align(Alignment.CenterHorizontally), stroke = 10.dp,
                    colors = listOf(Glass.Sky, Glass.Violet, Glass.Sky),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(water / 1000.0).oneDecimal()} l", color = Glass.Text, style = MaterialTheme.typography.titleLarge)
                        Text("/ ${(profile.waterGoalMl / 1000.0).oneDecimal()} l", color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    com.stopfat.app.ui.components.GlassPill("−", false, Modifier.weight(1f)) { vm.addWater(-250) }
                    com.stopfat.app.ui.components.GlassPill("+250", true, Modifier.weight(1.6f)) { vm.addWater(250) }
                }
            }
        }

        // Fasting
        FastingMiniCard(vm, nav)

        // Goal progress
        val plan = WeightLossPlanner.plan(profile)
        val startWeight = weights.firstOrNull()?.kg ?: profile.weightKg
        val totalToLose = (startWeight - profile.targetWeightKg).coerceAtLeast(0.01)
        val lost = (startWeight - profile.weightKg).coerceAtLeast(0.0)
        SectionTitle("Twój cel", action = "Analiza", onAction = { nav.go(Routes.ANALYSIS) })
        GlassCard(Modifier.fillMaxWidth(), onClick = { nav.go(Routes.ANALYSIS) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${profile.weightKg.oneDecimal()} kg → ${profile.targetWeightKg.oneDecimal()} kg", color = Glass.Text, style = MaterialTheme.typography.titleLarge)
                    Text("do ${WeightLossPlanner.formatDate(profile.targetDate)} · zostało ${plan.daysLeft} dni", color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Text(plan.safety.label, color = safetyColor(plan.safety), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(12.dp))
            MacroBar("Postęp: −${lost.oneDecimal()} kg", (lost / totalToLose * 100).coerceIn(0.0, 100.0), 100, Glass.Teal)
        }

        // AI coach teaser
        GlassCard(Modifier.fillMaxWidth().padding(top = 14.dp), onClick = { onOpenTab(4) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.AutoAwesome, Glass.accent)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Zapytaj Coacha AI", color = Glass.Text, style = MaterialTheme.typography.titleMedium)
                    Text("„Co zjeść na kolację, żeby zmieścić się w limicie?”", color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun KcalRow(emoji: String, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = Glass.TextSecondary, style = MaterialTheme.typography.labelSmall)
            Text(value, color = Glass.Text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, brush: Brush, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, corner = 22.dp, padding = PaddingValues(vertical = 14.dp, horizontal = 6.dp), onClick = onClick) {
        IconBadge(icon, brush, Modifier.align(Alignment.CenterHorizontally), size = 42.dp)
        Spacer(Modifier.height(8.dp))
        Text(label, color = Glass.Text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun FastingMiniCard(vm: MainViewModel, nav: Nav) {
    val profile by vm.profile.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(profile.fastingStartMillis) {
        while (profile.fastingStartMillis > 0) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    SectionTitle("Post przerywany")
    GlassCard(Modifier.fillMaxWidth(), onClick = { nav.go(Routes.FASTING) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Timer, Brush.linearGradient(listOf(Glass.Amber, Glass.Pink)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (profile.fastingStartMillis > 0) {
                    val elapsed = (now - profile.fastingStartMillis).coerceAtLeast(0)
                    val h = elapsed / 3_600_000
                    val m = (elapsed / 60_000) % 60
                    Text("Trwa post ${profile.fastingHours}:${24 - profile.fastingHours}", color = Glass.Text, style = MaterialTheme.typography.titleMedium)
                    Text("${h} h ${m} min z ${profile.fastingHours} h", color = Glass.Amber, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Rozpocznij post", color = Glass.Text, style = MaterialTheme.typography.titleMedium)
                    Text("16:8, 18:6, 20:4 lub OMAD z licznikiem", color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Dzień dobry"
    in 12..17 -> "Cześć"
    else -> "Dobry wieczór"
}
