package com.stopfat.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stopfat.app.data.Food
import com.stopfat.app.data.FoodCatalog
import com.stopfat.app.data.FoodGuess
import com.stopfat.app.data.MealType
import com.stopfat.app.data.db.CustomFood
import com.stopfat.app.domain.LocalFoodParser
import com.stopfat.app.ui.MainViewModel
import com.stopfat.app.ui.Nav
import com.stopfat.app.ui.Routes
import com.stopfat.app.ui.components.GlassButton
import com.stopfat.app.ui.components.GlassCard
import com.stopfat.app.ui.components.GlassPill
import com.stopfat.app.ui.components.GlassTextField
import com.stopfat.app.ui.components.ScreenHeader
import com.stopfat.app.ui.components.SectionTitle
import com.stopfat.app.ui.theme.Glass
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

fun CustomFood.toFood() = Food("custom_$id", name, kcal, protein, carbs, fat, "Moje produkty", unitGrams, if (unitGrams == 100.0) "100 g" else "porcja", barcode = barcode)

// ---------------------------------------------------------------- search

@Composable
fun FoodSearchScreen(vm: MainViewModel, nav: Nav, meal: MealType) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<Food?>(null) }
    var added by remember { mutableStateOf<String?>(null) }
    val custom by vm.customFoods.collectAsState()
    val recent by vm.recentEntries.collectAsState()
    val customFoods = custom.map { it.toFood() }
    val results = remember(query, category, custom) {
        FoodCatalog.search(query, customFoods).filter { category == null || it.category == category }.take(80)
    }

    Page {
        ScreenHeader("Produkty", "Dodajesz do: ${meal.label}", onBack = nav::back)
        GlassTextField(query, { query = it }, "Szukaj produktu", leading = Icons.Rounded.Search, placeholder = "np. jogurt, kurczak, banan",
            trailing = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Wyczyść", tint = Glass.TextSecondary) } })
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassPill("Wszystko", category == null) { category = null }
            (listOf("Moje produkty") + FoodCatalog.categories).forEach { c -> GlassPill(c, category == c) { category = if (category == c) null else c } }
        }
        added?.let { Text("✅ Dodano: $it", color = Glass.Teal, modifier = Modifier.padding(top = 10.dp)) }

        if (query.isBlank() && category == null && recent.isNotEmpty()) {
            SectionTitle("Ostatnio dodawane")
            GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                recent.take(8).forEach { e ->
                    FoodRow(e.name, "${amountLabel(e)} · ${e.kcal.roundToInt()} kcal") {
                        vm.addFood(meal, e.name, e.grams, e.kcal, e.protein, e.carbs, e.fat, e.source.ifBlank { "ostatnie" })
                        added = e.name
                    }
                }
            }
        }

        SectionTitle(if (query.isBlank()) "Baza produktów" else "Wyniki (${results.size})", action = "+ Własny", onAction = { nav.go(Routes.customFood(meal)) })
        GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
            if (results.isEmpty()) {
                Text("Nie znaleziono. Spróbuj opisu tekstem (AI) lub dodaj własny produkt.", color = Glass.TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
            }
            results.forEach { f ->
                FoodRow(f.name, "${f.kcal.roundToInt()} kcal / 100 g · B ${f.protein.oneDecimal()} W ${f.carbs.oneDecimal()} T ${f.fat.oneDecimal()}") { picked = f }
            }
        }
    }

    picked?.let { f ->
        AmountDialog(
            title = f.name,
            per100 = f.scaled(100.0),
            unitGrams = f.unitGrams,
            unitName = f.unitName,
            initialMeal = meal,
            onDismiss = { picked = null },
        ) { grams, m ->
            vm.addFood(m, f, grams)
            added = f.name
            picked = null
        }
    }
}

@Composable
fun FoodRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Glass.Text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Icon(Icons.Rounded.Add, "Dodaj", tint = Glass.Teal)
    }
}

// ---------------------------------------------------------------- custom food

@Composable
fun CustomFoodScreen(vm: MainViewModel, nav: Nav, meal: MealType, barcode: String?) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var portion by remember { mutableStateOf("100") }
    var selectedMeal by remember { mutableStateOf(meal) }
    val valid = name.isNotBlank() && (parseNumber(kcal) ?: -1.0) >= 0
    Page {
        ScreenHeader("Własny produkt", if (barcode != null) "Kod: $barcode" else "Wartości z etykiety (na 100 g)", onBack = nav::back)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassTextField(name, { name = it }, "Nazwa produktu")
            GlassTextField(kcal, { kcal = it }, "Kalorie na 100 g", keyboardType = KeyboardType.Decimal, suffix = "kcal")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassTextField(protein, { protein = it }, "Białko", Modifier.weight(1f), KeyboardType.Decimal, suffix = "g")
                GlassTextField(carbs, { carbs = it }, "Węgle", Modifier.weight(1f), KeyboardType.Decimal, suffix = "g")
                GlassTextField(fat, { fat = it }, "Tłuszcz", Modifier.weight(1f), KeyboardType.Decimal, suffix = "g")
            }
            GlassTextField(portion, { portion = it }, "Zjedzona ilość", keyboardType = KeyboardType.Decimal, suffix = "g")
            MealSelector(selectedMeal) { selectedMeal = it }
            Spacer(Modifier.height(6.dp))
            GlassButton("Zapisz i dodaj do dziennika", Modifier.fillMaxWidth(), enabled = valid) {
                val cf = CustomFood(
                    name = name.trim(), kcal = parseNumber(kcal) ?: 0.0, protein = parseNumber(protein) ?: 0.0,
                    carbs = parseNumber(carbs) ?: 0.0, fat = parseNumber(fat) ?: 0.0,
                    unitGrams = parseNumber(portion)?.takeIf { it > 0 } ?: 100.0, barcode = barcode,
                )
                vm.saveCustomFood(cf)
                vm.addFood(selectedMeal, cf.toFood(), cf.unitGrams, if (barcode != null) MainViewModel.SOURCE_BARCODE else "własny")
                nav.back()
            }
        }
    }
}

// ---------------------------------------------------------------- AI / parsed results editor

@Composable
fun GuessesEditor(items: SnapshotStateList<FoodGuess>, meal: MealType, onMealChange: (MealType) -> Unit, onAdd: () -> Unit, notes: String? = null) {
    if (items.isEmpty()) return
    SectionTitle("Rozpoznane produkty")
    GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, padding = PaddingValues(14.dp)) {
        items.forEachIndexed { index, item ->
            var gramsText by remember(item.name, index) { mutableStateOf(item.grams.roundToInt().toString()) }
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = Glass.Text, style = MaterialTheme.typography.titleSmall)
                        MacroLine(item.kcal, item.protein, item.carbs, item.fat)
                        if (item.confidence.isNotBlank()) Text("pewność: ${item.confidence}", color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassTextField(
                        gramsText,
                        { v ->
                            gramsText = v
                            parseNumber(v)?.takeIf { it > 0 }?.let { g -> items[index] = items[index].withGrams(g) }
                        },
                        "g", Modifier.width(92.dp), KeyboardType.Decimal,
                    )
                    IconButton(onClick = { items.removeAt(index) }) { Icon(Icons.Rounded.Close, "Usuń", tint = Glass.TextSecondary) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Razem", color = Glass.TextSecondary, modifier = Modifier.weight(1f))
            Text(items.sumOf { it.kcal }.kcal(), color = Glass.Teal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        if (!notes.isNullOrBlank()) Text("💡 $notes", color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
    Spacer(Modifier.height(12.dp))
    MealSelector(meal, onMealChange)
    Spacer(Modifier.height(12.dp))
    GlassButton("Dodaj do: ${meal.label}", Modifier.fillMaxWidth(), icon = Icons.Rounded.Add, onClick = onAdd)
}

// ---------------------------------------------------------------- text

@Composable
fun TextFoodScreen(vm: MainViewModel, nav: Nav, meal: MealType) {
    val profile by vm.profile.collectAsState()
    val custom by vm.customFoods.collectAsState()
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf<String?>(null) }
    var selectedMeal by remember { mutableStateOf(meal) }
    val items = remember { mutableStateListOf<FoodGuess>() }

    fun runLocal(prefix: String? = null) {
        val r = LocalFoodParser.parse(text, custom.map { it.toFood() })
        items.clear(); items.addAll(r.items)
        notes = listOfNotNull(prefix, if (r.unknown.isNotEmpty()) "Nie rozpoznano: ${r.unknown.joinToString(", ")}" else null).joinToString("\n").ifBlank { null }
        if (r.items.isEmpty()) error = "Nie udało się rozpoznać produktów. Podaj np. „150 g ryżu, 2 jajka, banan”."
    }

    Page {
        ScreenHeader("Opisz posiłek", if (profile.hasAi) "AI policzy kalorie z opisu" else "Tryb offline – dodaj klucz AI dla lepszej dokładności", onBack = nav::back)
        GlassTextField(
            text, { text = it }, "Co zjadłeś/aś?", singleLine = false, minLines = 3,
            placeholder = "np. owsianka na mleku z bananem i łyżką masła orzechowego, kawa z mlekiem",
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("2 jajka i kromka chleba z masłem", "kurczak 150 g z ryżem i brokułami", "kebab w bułce i cola", "jogurt grecki z granolą").forEach {
                GlassPill(it, false) { text = it }
            }
        }
        Spacer(Modifier.height(14.dp))
        GlassButton(if (profile.hasAi) "Oblicz z AI" else "Oblicz", Modifier.fillMaxWidth(), loading = busy, enabled = text.isNotBlank()) {
            error = null
            notes = null
            val ai = profile.hasAi
            if (!ai) {
                runLocal()
            } else {
                busy = true
                scope.launch {
                    try {
                        val service = vm.ai()
                        if (service == null) runLocal() else {
                            val r = service.analyzeText(text)
                            items.clear(); items.addAll(r.items)
                            notes = r.notes
                            if (r.items.isEmpty()) error = r.notes.ifBlank { "AI nie rozpoznało produktów." }
                        }
                    } catch (e: Exception) {
                        runLocal("AI niedostępne (${e.message}) – użyto bazy offline.")
                    } finally {
                        busy = false
                    }
                }
            }
        }
        ErrorBanner(error)
        GuessesEditor(items, selectedMeal, { selectedMeal = it }, notes = notes, onAdd = {
            vm.addGuesses(selectedMeal, items.toList(), if (profile.hasAi) MainViewModel.SOURCE_TEXT else "opis")
            nav.back()
        })
    }
}
