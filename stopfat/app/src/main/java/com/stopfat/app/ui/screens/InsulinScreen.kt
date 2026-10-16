package com.stopfat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stopfat.app.data.DietType
import com.stopfat.app.data.FoodCatalog
import com.stopfat.app.data.MealType
import com.stopfat.app.domain.GiLevel
import com.stopfat.app.domain.Glycemic
import com.stopfat.app.ui.MainViewModel
import com.stopfat.app.ui.Nav
import com.stopfat.app.ui.Routes
import com.stopfat.app.ui.components.FloatingEmoji
import com.stopfat.app.ui.components.GlassButton
import com.stopfat.app.ui.components.GlassCard
import com.stopfat.app.ui.components.ProgressRing
import com.stopfat.app.ui.components.ScreenHeader
import com.stopfat.app.ui.components.SectionTitle
import com.stopfat.app.ui.theme.Glass
import kotlin.math.roundToInt

fun giColor(level: GiLevel): Color = when (level) {
    GiLevel.NONE, GiLevel.LOW -> Glass.TealText
    GiLevel.MEDIUM -> Glass.AmberText
    GiLevel.HIGH -> Glass.Red
    GiLevel.UNKNOWN -> Glass.TextTertiary
}

/** Small coloured "IG" chip used in food lists when the insulin-resistance mode is on. */
@Composable
fun GiBadge(gi: Int?, modifier: Modifier = Modifier) {
    val level = Glycemic.level(gi)
    if (level == GiLevel.UNKNOWN) return
    val c = giColor(level)
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(if (level == GiLevel.NONE) "IG 0" else "IG $gi", color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun InsulinScreen(vm: MainViewModel, nav: Nav) {
    val profile by vm.profile.collectAsState()
    val entries by vm.todayEntries.collectAsState()
    val day = Glycemic.day(entries, true)
    val carbsPerMeal = entries.groupBy { it.meal }.mapValues { (_, l) -> l.sumOf { it.carbs } }

    Page {
        ScreenHeader("Insulinooporność", "Indeks i ładunek glikemiczny pod kontrolą", onBack = nav::back)

        GlassCard(Modifier.fillMaxWidth(), strong = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tryb insulinooporności", color = Glass.Text, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (profile.insulinResistance) "Włączony – dieta o niskim IG, oznaczenia IG w dzienniku i AI dostosowane do IO."
                        else "Włącz, aby aplikacja oznaczała IG produktów, liczyła ładunek glikemiczny i dostosowała AI.",
                        color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    profile.insulinResistance,
                    { on -> vm.saveProfile(profile.copy(insulinResistance = on, diet = if (on && profile.diet == DietType.BALANCED) DietType.LOW_GI else profile.diet)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Glass.Violet),
                )
            }
        }

        SectionTitle("Dzisiaj")
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing((day.load / day.target).toFloat(), Modifier.size(120.dp), stroke = 13.dp, colors = listOf(Glass.Teal, Glass.Amber, Glass.Pink, Glass.Teal)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${day.load.roundToInt()}", color = Glass.Text, style = MaterialTheme.typography.headlineMedium)
                        Text("ŁG / ${day.target}", color = Glass.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ładunek glikemiczny", color = Glass.Text, style = MaterialTheme.typography.titleSmall)
                    Text("Cel przy IO: poniżej ${Glycemic.DAILY_LOAD_TARGET_IR} dziennie.", color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    if (day.unknownCarbs > 0) Text("${day.unknownCarbs.roundToInt()} g węgli z produktów bez znanego IG", color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Węglowodany w posiłkach (zalecane ≤ ${Glycemic.CARBS_PER_MEAL_IR} g)", color = Glass.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            MealType.entries.forEach { m ->
                val c = carbsPerMeal[m.name] ?: 0.0
                val over = c > Glycemic.CARBS_PER_MEAL_IR
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${m.emoji} ${m.label}", color = Glass.Text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${c.roundToInt()} g", color = if (over) Glass.Red else Glass.TealText, fontWeight = FontWeight.Bold)
                    Text(" · ŁG ${(day.mealLoads[m.name] ?: 0.0).roundToInt()}", color = Glass.TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (day.highGiItems.isNotEmpty()) {
            SectionTitle("Zamień na niższy IG")
            GlassCard(Modifier.fillMaxWidth(), corner = 22.dp) {
                day.highGiItems.distinctBy { it.name }.forEach { e ->
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.name, color = Glass.Text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            GiBadge(e.gi)
                        }
                        Glycemic.swapFor(e.name)?.let { Text("➜ $it", color = Glass.TealText, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }

        SectionTitle("Najlepsze wybory (niski IG)")
        GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, padding = PaddingValues(14.dp)) {
            val good = FoodCatalog.foods.filter { (it.gi ?: -1) in 1..45 && it.carbs >= 8 }.sortedBy { it.gi }.take(14)
            ChipFlow(good.map { "${it.name} · IG ${it.gi}" }, Glass.TealText)
        }
        SectionTitle("Ogranicz (wysoki IG)")
        GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, padding = PaddingValues(14.dp)) {
            val bad = FoodCatalog.foods.filter { (it.gi ?: -1) >= 70 }.sortedByDescending { it.gi }.take(12)
            ChipFlow(bad.map { "${it.name} · IG ${it.gi}" }, Glass.Red)
        }

        SectionTitle("Zasady diety przy IO")
        GlassCard(Modifier.fillMaxWidth(), corner = 22.dp) {
            Glycemic.rules.forEach { (emoji, text) ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text(emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(text, color = Glass.Text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Przepisy niski IG", Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.MenuBook, secondary = true) {
                nav.go(Routes.recipes(MealType.suggestedForHour(java.time.LocalTime.now().hour)))
            }
            GlassButton("Zapytaj AI", Modifier.weight(1f), icon = Icons.Rounded.AutoAwesome) {
                vm.sendChat("Mam insulinooporność. Oceń mój dzisiejszy dzień pod kątem indeksu glikemicznego i podpowiedz, co zjeść na kolejny posiłek.")
                vm.openTabRequest.value = 4
                nav.back()
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { FloatingEmoji("🩺", 26) }
        Text(
            "Aplikacja wspiera dietę przy insulinooporności, ale nie zastępuje lekarza ani diabetologa. Przy lekach (np. metforminie) konsultuj zmiany diety.",
            color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(items: List<String>, color: Color) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.10f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) { Text(it, color = color, style = MaterialTheme.typography.labelMedium) }
        }
    }
}
