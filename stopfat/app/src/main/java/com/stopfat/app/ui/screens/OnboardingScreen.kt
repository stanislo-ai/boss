package com.stopfat.app.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.stopfat.app.data.ActivityLevel
import com.stopfat.app.data.DietType
import com.stopfat.app.data.Sex
import com.stopfat.app.data.UserProfile
import com.stopfat.app.domain.Nutrition
import com.stopfat.app.domain.PlanSafety
import com.stopfat.app.domain.WeightLossPlanner
import com.stopfat.app.ui.MainViewModel
import com.stopfat.app.ui.components.GlassButton
import com.stopfat.app.ui.components.GlassCard
import com.stopfat.app.ui.components.GlassPill
import com.stopfat.app.ui.components.GlassTextField
import com.stopfat.app.ui.theme.Glass
import java.time.LocalDate

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(vm: MainViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf(Sex.MALE) }
    var age by rememberSaveable { mutableStateOf("30") }
    var height by rememberSaveable { mutableStateOf("175") }
    var weight by rememberSaveable { mutableStateOf("85") }
    var target by rememberSaveable { mutableStateOf("75") }
    var targetDate by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(120).toEpochDay()) }
    var activity by rememberSaveable { mutableStateOf(ActivityLevel.LIGHT) }
    var diet by rememberSaveable { mutableStateOf(DietType.BALANCED) }
    var stepGoal by rememberSaveable { mutableStateOf(8000f) }
    var apiKey by rememberSaveable { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    fun buildProfile(onboarded: Boolean) = UserProfile(
        name = name.trim(),
        sex = sex,
        age = age.toIntOrNull()?.coerceIn(12, 100) ?: 30,
        heightCm = height.toIntOrNull()?.coerceIn(120, 230) ?: 175,
        weightKg = parseNumber(weight)?.coerceIn(30.0, 350.0) ?: 85.0,
        targetWeightKg = parseNumber(target)?.coerceIn(30.0, 350.0) ?: 75.0,
        targetDateEpochDay = targetDate,
        activity = activity,
        diet = diet,
        stepGoal = (stepGoal.toInt() / 500) * 500,
        waterGoalMl = Nutrition.waterGoalFor(parseNumber(weight) ?: 85.0).coerceIn(1500, 4000),
        apiKey = apiKey.trim(),
        onboarded = onboarded,
    )

    val basicsValid = (age.toIntOrNull() ?: 0) in 12..100 && (height.toIntOrNull() ?: 0) in 120..230 && (parseNumber(weight) ?: 0.0) in 30.0..350.0
    val goalValid = (parseNumber(target) ?: 0.0) in 30.0..350.0 && targetDate > LocalDate.now().toEpochDay()

    Page {
        Spacer(Modifier.height(18.dp))
        // progress dots
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(5) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .height(6.dp)
                        .width(if (i == step) 28.dp else 10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i <= step) Glass.accent else androidx.compose.ui.graphics.SolidColor(Glass.FillStrong)),
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        AnimatedContent(step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onb") { s ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (s) {
                    0 -> {
                        Spacer(Modifier.height(30.dp))
                        Text("🔥", fontSize = 72.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Text("StopFat", style = MaterialTheme.typography.displaySmall, color = Glass.Text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Text(
                            "Twój osobisty dietetyk i trener z AI.\nLiczenie kalorii, skaner kodów i zdjęć, plany treningowe, kroki i dokładny plan odchudzania – w jednym miejscu.",
                            color = Glass.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        listOf(
                            "📸" to "Zrób zdjęcie posiłku – AI policzy kalorie",
                            "🔍" to "Skanuj kody kreskowe produktów",
                            "🏋️" to "Plan treningowy AI – siłownia lub dom",
                            "📈" to "Dokładna analiza: ile kg do jakiej daty",
                            "👟" to "Kroki z wbudowanego licznika telefonu",
                        ).forEach { (e, t) ->
                            GlassCard(Modifier.fillMaxWidth(), corner = 20.dp, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(e, fontSize = 22.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(t, color = Glass.Text)
                                }
                            }
                        }
                    }
                    1 -> {
                        Text("Poznajmy się", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                        Text("Te dane są potrzebne do wyliczenia Twojego zapotrzebowania kalorycznego.", color = Glass.TextSecondary)
                        GlassTextField(name, { name = it }, "Imię (opcjonalnie)")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Sex.entries.forEach { GlassPill(it.label, sex == it, Modifier.weight(1f)) { sex = it } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassTextField(age, { age = it.filter(Char::isDigit).take(3) }, "Wiek", Modifier.weight(1f), KeyboardType.Number, suffix = "lat")
                            GlassTextField(height, { height = it.filter(Char::isDigit).take(3) }, "Wzrost", Modifier.weight(1f), KeyboardType.Number, suffix = "cm")
                        }
                        GlassTextField(weight, { weight = it.take(6) }, "Aktualna waga", keyboardType = KeyboardType.Decimal, suffix = "kg")
                    }
                    2 -> {
                        Text("Twój cel", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                        GlassTextField(target, { target = it.take(6) }, "Waga docelowa", keyboardType = KeyboardType.Decimal, suffix = "kg")
                        GlassCard(Modifier.fillMaxWidth(), corner = 20.dp, onClick = {
                            val d = LocalDate.ofEpochDay(targetDate)
                            DatePickerDialog(context, { _, y, m, day -> targetDate = LocalDate.of(y, m + 1, day).toEpochDay() }, d.year, d.monthValue - 1, d.dayOfMonth).apply {
                                datePicker.minDate = System.currentTimeMillis() + 86_400_000L
                            }.show()
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Icon(Icons.Rounded.CalendarMonth, null, tint = Glass.Teal)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Data osiągnięcia celu", color = Glass.TextSecondary, style = MaterialTheme.typography.labelMedium)
                                    Text(WeightLossPlanner.formatDate(LocalDate.ofEpochDay(targetDate)), color = Glass.Text, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(60, 90, 120, 180).forEach { days ->
                                GlassPill("${days / 30} mies.", false, Modifier.weight(1f)) { targetDate = LocalDate.now().plusDays(days.toLong()).toEpochDay() }
                            }
                        }
                        if (goalValid && basicsValid) {
                            val plan = WeightLossPlanner.plan(buildProfile(false))
                            GlassCard(Modifier.fillMaxWidth(), strong = true) {
                                Text("Szybka ocena", color = Glass.TextSecondary, style = MaterialTheme.typography.labelMedium)
                                Text(plan.safety.label, color = safetyColor(plan.safety), style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (plan.kgToLose > 0) "Do zrzucenia ${plan.kgToLose.oneDecimal()} kg w ${plan.daysLeft} dni (${"%.2f".format(PL, plan.weeklyLossKg)} kg/tydz.). Zalecane: ${plan.recommendedIntake} kcal dziennie."
                                    else "Cel utrzymania wagi – ok. ${plan.recommendedIntake} kcal dziennie.",
                                    color = Glass.Text,
                                )
                                if (plan.safety == PlanSafety.TOO_FAST) Text("Bezpieczna data: ${WeightLossPlanner.formatDate(plan.safeTargetDate)}", color = Glass.Amber, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                        Text("Styl diety", style = MaterialTheme.typography.titleMedium, color = Glass.Text)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DietType.entries.forEach { GlassPill(it.label, diet == it) { diet = it } }
                        }
                    }
                    3 -> {
                        Text("Aktywność", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                        ActivityLevel.entries.forEach { a ->
                            GlassCard(Modifier.fillMaxWidth(), corner = 20.dp, strong = a == activity, onClick = { activity = a }, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                                Text(a.label, color = if (a == activity) Glass.Teal else Glass.Text, style = MaterialTheme.typography.titleMedium)
                                Text(a.description, color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text("Dzienny cel kroków: ${(stepGoal.toInt() / 500 * 500).thousands()}", color = Glass.Text, style = MaterialTheme.typography.titleMedium)
                        Slider(
                            stepGoal, { stepGoal = it }, valueRange = 3000f..20000f,
                            colors = SliderDefaults.colors(thumbColor = Glass.Teal, activeTrackColor = Glass.Teal, inactiveTrackColor = Glass.FillStrong),
                        )
                    }
                    else -> {
                        Text("Połącz z AI", style = MaterialTheme.typography.headlineMedium, color = Glass.Text)
                        Text(
                            "StopFat używa modeli Claude (Anthropic) do analizy zdjęć posiłków, liczenia kalorii z opisu, planów treningowych i Coacha AI. " +
                                "Wklej swój klucz API z console.anthropic.com. Możesz to zrobić też później w Ustawieniach – bez klucza działają funkcje offline.",
                            color = Glass.TextSecondary,
                        )
                        GlassTextField(apiKey, { apiKey = it.trim() }, "Klucz API (sk-ant-...)", leading = Icons.Rounded.Key)
                        GlassCard(Modifier.fillMaxWidth(), corner = 20.dp) {
                            Text("Uprawnienia", color = Glass.Text, style = MaterialTheme.typography.titleMedium)
                            Text("Zezwól na dostęp do aktywności fizycznej (licznik kroków) i powiadomień (przypomnienia).", color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(10.dp))
                            GlassButton("Zezwól", Modifier.fillMaxWidth(), secondary = true) {
                                val perms = buildList {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
                                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) GlassButton("Wstecz", Modifier.weight(1f), secondary = true) { step-- }
            val canNext = when (step) {
                1 -> basicsValid
                2 -> goalValid
                else -> true
            }
            GlassButton(
                when (step) {
                    0 -> "Zaczynamy"
                    4 -> "Gotowe"
                    else -> "Dalej"
                },
                Modifier.weight(if (step > 0) 1.4f else 1f),
                enabled = canNext,
            ) {
                if (step < 4) step++ else {
                    vm.saveProfile(buildProfile(true))
                    onDone()
                }
            }
        }
    }
}

fun safetyColor(s: PlanSafety) = when (s) {
    PlanSafety.MAINTAIN, PlanSafety.GENTLE -> Glass.Sky
    PlanSafety.OPTIMAL -> Glass.Teal
    PlanSafety.AMBITIOUS -> Glass.Amber
    PlanSafety.TOO_FAST -> Glass.Red
}
