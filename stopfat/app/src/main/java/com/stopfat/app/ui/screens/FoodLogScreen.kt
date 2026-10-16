package com.stopfat.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stopfat.app.data.MealType
import com.stopfat.app.data.db.FoodEntry
import com.stopfat.app.domain.Nutrition
import com.stopfat.app.ui.MainViewModel
import com.stopfat.app.ui.Nav
import com.stopfat.app.ui.Routes
import com.stopfat.app.ui.components.GlassCard
import com.stopfat.app.ui.components.GlassTextField
import com.stopfat.app.ui.components.IconBadge
import com.stopfat.app.ui.components.MacroBar
import com.stopfat.app.ui.components.ScreenHeader
import com.stopfat.app.ui.components.SectionTitle
import com.stopfat.app.ui.theme.Glass
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun FoodLogScreen(vm: MainViewModel, nav: Nav) {
    val profile by vm.profile.collectAsState()
    val day by vm.selectedDay.collectAsState()
    val entries by vm.dayEntries.collectAsState()
    var addFor by remember { mutableStateOf<MealType?>(null) }
    var editing by remember { mutableStateOf<FoodEntry?>(null) }
    val goal = Nutrition.calorieGoal(profile)
    val macros = Nutrition.macroTargets(profile, goal)
    val today = LocalDate.now().toEpochDay()

    Page(tabPage = true) {
        ScreenHeader("Dziennik", "Wszystko, co dziś jesz")
        // date switcher
        GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, padding = PaddingValues(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.selectDay(day - 1) }) { Icon(Icons.Rounded.ChevronLeft, "Poprzedni dzień", tint = Glass.Text) }
                Text(dayLabel(day), color = Glass.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { vm.selectDay(day + 1) }, enabled = day < today) {
                    Icon(Icons.Rounded.ChevronRight, "Następny dzień", tint = if (day < today) Glass.Text else Glass.TextTertiary)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val eaten = entries.sumOf { it.kcal }
        GlassCard(Modifier.fillMaxWidth(), strong = true) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(eaten.roundToInt().thousands(), style = MaterialTheme.typography.headlineLarge, color = Glass.Text)
                Text(" / ${goal.thousands()} kcal", color = Glass.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroBar("Białko", entries.sumOf { it.protein }, macros.proteinG, Glass.protein, Modifier.weight(1f))
                MacroBar("Węgle", entries.sumOf { it.carbs }, macros.carbsG, Glass.carbs, Modifier.weight(1f))
                MacroBar("Tłuszcz", entries.sumOf { it.fat }, macros.fatG, Glass.fat, Modifier.weight(1f))
            }
        }

        MealType.entries.forEach { meal ->
            val list = entries.filter { it.meal == meal.name }
            SectionTitle("${meal.emoji} ${meal.label}", action = "+ Dodaj", onAction = { addFor = meal })
            GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                if (list.isEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { addFor = meal }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = Glass.Teal)
                        Spacer(Modifier.width(8.dp))
                        Text("Dodaj ${meal.label.lowercase()}", color = Glass.TextSecondary)
                    }
                } else {
                    list.forEach { e -> EntryRow(e, showGi = profile.insulinResistance, onClick = { editing = e }) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.End) {
                        Text("Razem: ${list.sumOf { it.kcal }.kcal()}", color = Glass.TealText, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        if (entries.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth(), corner = 20.dp, onClick = { vm.copyYesterday() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ContentCopy, null, tint = Glass.Sky)
                    Spacer(Modifier.width(10.dp))
                    Text("Skopiuj posiłki z poprzedniego dnia", color = Glass.Text)
                }
            }
        }
    }

    addFor?.let { meal ->
        AddMethodDialog(meal, onDismiss = { addFor = null }) { route ->
            addFor = null
            nav.go(route)
        }
    }
    editing?.let { e ->
        EditEntryDialog(
            e,
            onDismiss = { editing = null },
            onDelete = { vm.deleteFood(e.id); editing = null },
            onSave = { grams -> vm.updateFoodGrams(e, grams); editing = null },
        )
    }
}

@Composable
fun EntryRow(e: FoodEntry, showGi: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.name, color = Glass.Text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${amountLabel(e)} · B ${e.protein.roundToInt()} · W ${e.carbs.roundToInt()} · T ${e.fat.roundToInt()}" + if (e.source.isNotBlank()) " · ${e.source}" else "",
                color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (showGi) {
            GiBadge(e.gi)
            Spacer(Modifier.width(8.dp))
        }
        Text(e.kcal.roundToInt().toString(), color = Glass.Text, fontWeight = FontWeight.Bold)
        Text(" kcal", color = Glass.TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

fun amountLabel(e: FoodEntry): String =
    if (e.source == MainViewModel.SOURCE_RECIPE) "${(e.grams / 100.0).oneDecimal()} porcji" else "${e.grams.roundToInt()} g"

@Composable
fun AddMethodDialog(meal: MealType, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    data class M(val title: String, val sub: String, val icon: ImageVector, val brush: Brush, val route: String)
    val methods = listOf(
        M("Zdjęcie AI", "Zrób zdjęcie – AI rozpozna i policzy", Icons.Rounded.CameraAlt, Glass.accent, Routes.photo(meal)),
        M("Skaner kodu kreskowego", "Baza Open Food Facts", Icons.Rounded.QrCodeScanner, Brush.linearGradient(listOf(Glass.Sky, Glass.Violet)), Routes.barcode(meal)),
        M("Opisz tekstem", "„2 jajka i kromka chleba z masłem”", Icons.Rounded.EditNote, Brush.linearGradient(listOf(Glass.Pink, Glass.Violet)), Routes.text(meal)),
        M("Lista produktów", "Wyszukaj w bazie ${com.stopfat.app.data.FoodCatalog.foods.size}+ produktów", Icons.Rounded.Search, Glass.warm, Routes.search(meal)),
        M("Gotowe przepisy", "Fit przepisy z kaloriami", Icons.AutoMirrored.Rounded.MenuBook, Brush.linearGradient(listOf(Glass.Lime, Glass.Teal)), Routes.recipes(meal)),
        M("Co mam w lodówce?", "AI wymyśli przepis z Twoich składników", Icons.Rounded.Kitchen, Brush.linearGradient(listOf(Glass.Amber, Glass.Lime)), Routes.fridge(meal)),
        M("Własny produkt", "Wpisz wartości z etykiety", Icons.Rounded.PostAdd, Brush.linearGradient(listOf(Glass.Violet, Glass.Pink)), Routes.customFood(meal)),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Glass.Dialog,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Dodaj: ${meal.label}", color = Glass.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                methods.forEach { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPick(m.route) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(m.icon, m.brush, size = 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(m.title, color = Glass.Text, style = MaterialTheme.typography.titleSmall)
                            Text(m.sub, color = Glass.TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij", color = Glass.TextSecondary) } },
    )
}

@Composable
fun EditEntryDialog(e: FoodEntry, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Double) -> Unit) {
    val isRecipe = e.source == MainViewModel.SOURCE_RECIPE
    var grams by remember { mutableStateOf(if (isRecipe) (e.grams / 100.0).oneDecimal() else e.grams.roundToInt().toString()) }
    val g = (parseNumber(grams) ?: 0.0) * if (isRecipe) 100.0 else 1.0
    val f = if (e.grams > 0) g / e.grams else 1.0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Glass.Dialog,
        shape = RoundedCornerShape(28.dp),
        title = { Text(e.name, color = Glass.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassTextField(grams, { grams = it }, if (isRecipe) "Liczba porcji" else "Ilość", keyboardType = KeyboardType.Decimal, suffix = if (isRecipe) "porcji" else "g")
                MacroLine(e.kcal * f, e.protein * f, e.carbs * f, e.fat * f)
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onDelete).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Delete, null, tint = Glass.Red, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Usuń wpis", color = Glass.Red, fontSize = 14.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (g > 0) onSave(g) }, enabled = g > 0) { Text("Zapisz", color = Glass.TealText, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj", color = Glass.TextSecondary) } },
    )
}
