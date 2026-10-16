package com.stopfat.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stopfat.app.data.MealType
import com.stopfat.app.data.Recipe
import com.stopfat.app.data.RecipeCatalog
import com.stopfat.app.domain.Nutrition
import com.stopfat.app.ui.MainViewModel
import com.stopfat.app.ui.Nav
import com.stopfat.app.ui.Routes
import com.stopfat.app.ui.components.GlassButton
import com.stopfat.app.ui.components.GlassCard
import com.stopfat.app.ui.components.GlassPill
import com.stopfat.app.ui.components.GlassTextField
import com.stopfat.app.ui.components.ScreenHeader
import com.stopfat.app.ui.components.SectionTitle
import com.stopfat.app.ui.components.StatChip
import com.stopfat.app.ui.theme.Glass
import kotlinx.coroutines.launch

@Composable
fun RecipesScreen(vm: MainViewModel, nav: Nav, meal: MealType) {
    val aiRecipes by vm.aiRecipes.collectAsState()
    var category by remember { mutableStateOf(defaultCategory(meal)) }
    var query by remember { mutableStateOf("") }
    var highProtein by remember { mutableStateOf(false) }
    val profile by vm.profile.collectAsState()
    var lowGi by remember { mutableStateOf(profile.insulinResistance) }
    val list = (aiRecipes + RecipeCatalog.recipes).filter {
        (category == "Wszystkie" || it.category == category || (category == "AI" && it.category == "AI")) &&
            (query.isBlank() || it.name.contains(query, true) || it.ingredients.any { i -> i.contains(query, true) }) &&
            (!highProtein || it.protein * 4.0 / it.kcal.coerceAtLeast(1) >= 0.25) &&
            (!lowGi || RecipeCatalog.isLowGi(it))
    }
    Page {
        ScreenHeader("Przepisy", "${RecipeCatalog.recipes.size} fit przepisów z kaloriami", onBack = nav::back)
        GlassTextField(query, { query = it }, "Szukaj przepisu lub składnika", leading = Icons.Rounded.Search)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (RecipeCatalog.categories + if (aiRecipes.isNotEmpty()) listOf("AI") else emptyList()).forEach { c -> GlassPill(c, category == c) { category = c } }
            GlassPill("🩺 Niski IG", lowGi) { lowGi = !lowGi }
            GlassPill("💪 Dużo białka", highProtein) { highProtein = !highProtein }
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, onClick = { nav.go(Routes.fridge(meal)) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧊", fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Co mam w lodówce?", color = Glass.Text, style = MaterialTheme.typography.titleMedium)
                    Text("AI ułoży przepis z Twoich składników", color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        list.forEach { r ->
            Spacer(Modifier.height(10.dp))
            RecipeCard(r) { nav.go(Routes.recipe(r.id, meal)) }
        }
        if (list.isEmpty()) Text("Brak przepisów dla tych filtrów.", color = Glass.TextSecondary, modifier = Modifier.padding(top = 16.dp))
    }
}

private fun defaultCategory(meal: MealType) = when (meal) {
    MealType.BREAKFAST -> "Śniadanie"
    MealType.LUNCH -> "Obiad"
    MealType.DINNER -> "Kolacja"
    MealType.SNACK -> "Przekąska"
}

@Composable
fun RecipeCard(r: Recipe, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), corner = 24.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.emoji, fontSize = 38.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, color = Glass.Text, style = MaterialTheme.typography.titleMedium)
                Text("⏱ ${r.minutes} min · ${r.category}" + if (r.tags.isNotEmpty()) " · ${r.tags.first()}" else "", color = Glass.TextSecondary, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                MacroLine(r.kcal.toDouble(), r.protein.toDouble(), r.carbs.toDouble(), r.fat.toDouble())
            }
        }
    }
}

@Composable
fun RecipeDetailScreen(vm: MainViewModel, nav: Nav, id: String, meal: MealType) {
    val aiRecipes by vm.aiRecipes.collectAsState()
    val recipe = RecipeCatalog.byId(id) ?: aiRecipes.firstOrNull { it.id == id }
    if (recipe == null) {
        Page { ScreenHeader("Przepis", "Nie znaleziono przepisu", onBack = nav::back) }
        return
    }
    var servings by remember { mutableDoubleStateOf(1.0) }
    var selectedMeal by remember { mutableStateOf(meal) }
    var added by remember { mutableStateOf(false) }
    Page {
        ScreenHeader(recipe.name, "${recipe.category} · ${recipe.minutes} min · ${recipe.servings} porcj${if (recipe.servings == 1) "a" else if (recipe.servings in 2..4) "e" else "i"}", onBack = nav::back)
        GlassCard(Modifier.fillMaxWidth(), strong = true) {
            Text(recipe.emoji, fontSize = 64.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Na 1 porcję", color = Glass.TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatChip("${recipe.kcal}", "kcal", Glass.Text)
                StatChip("${recipe.protein} g", "białko", Glass.protein)
                StatChip("${recipe.carbs} g", "węgle", Glass.carbs)
                StatChip("${recipe.fat} g", "tłuszcz", Glass.fat)
            }
        }
        if (recipe.tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recipe.tags.forEach { GlassPill("#$it", false) {} }
            }
        }
        SectionTitle("Składniki")
        GlassCard(Modifier.fillMaxWidth(), corner = 22.dp) {
            recipe.ingredients.forEach { Text("• $it", color = Glass.Text, modifier = Modifier.padding(vertical = 3.dp)) }
        }
        SectionTitle("Przygotowanie")
        GlassCard(Modifier.fillMaxWidth(), corner = 22.dp) {
            recipe.steps.forEachIndexed { i, s ->
                Row(Modifier.padding(vertical = 5.dp)) {
                    Text("${i + 1}", color = Glass.TealText, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                    Text(s, color = Glass.Text)
                }
            }
        }
        SectionTitle("Dodaj do dziennika")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.5, 1.0, 1.5, 2.0).forEach { s -> GlassPill(if (s == 1.0) "1 porcja" else "${s.toString().replace(".0", "").replace('.', ',')} porcji", servings == s, Modifier.weight(1f)) { servings = s } }
        }
        Spacer(Modifier.height(10.dp))
        MealSelector(selectedMeal) { selectedMeal = it }
        Spacer(Modifier.height(12.dp))
        GlassButton(if (added) "Dodano ✓" else "Dodaj ${(recipe.kcal * servings).toInt()} kcal", Modifier.fillMaxWidth(), icon = Icons.Rounded.Add, enabled = !added) {
            vm.addRecipe(selectedMeal, recipe, servings)
            added = true
        }
    }
}

@Composable
fun FridgeScreen(vm: MainViewModel, nav: Nav, meal: MealType) {
    val profile by vm.profile.collectAsState()
    val scope = rememberCoroutineScope()
    var ingredients by remember { mutableStateOf("") }
    var maxKcal by remember { mutableStateOf((Nutrition.calorieGoal(profile) / 3.5).toInt().coerceIn(250, 900).toString()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Recipe?>(null) }

    Page {
        ScreenHeader("Co mam w lodówce?", "AI wymyśli fit przepis", onBack = nav::back)
        if (!profile.hasAi) {
            ErrorBanner("Ta funkcja wymaga klucza API w Ustawieniach. Bez AI wyszukaj przepisy po składniku w zakładce Przepisy.")
            Spacer(Modifier.height(10.dp))
        }
        GlassTextField(ingredients, { ingredients = it }, "Składniki", singleLine = false, minLines = 3, placeholder = "np. jajka, szpinak, feta, pomidory, makaron")
        Spacer(Modifier.height(10.dp))
        GlassTextField(maxKcal, { maxKcal = it.filter(Char::isDigit).take(4) }, "Maks. kalorii na porcję", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, suffix = "kcal")
        Spacer(Modifier.height(14.dp))
        GlassButton("Wymyśl przepis", Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome, loading = busy, enabled = ingredients.isNotBlank() && profile.hasAi) {
            busy = true
            error = null
            scope.launch {
                try {
                    val service = vm.ai() ?: throw IllegalStateException("Brak klucza API")
                    val r = service.recipeFromIngredients(ingredients, maxKcal.toIntOrNull() ?: 600, profile)
                    vm.aiRecipes.value = listOf(r) + vm.aiRecipes.value
                    result = r
                } catch (e: Exception) {
                    error = e.message
                } finally {
                    busy = false
                }
            }
        }
        ErrorBanner(error)
        result?.let { r ->
            Spacer(Modifier.height(14.dp))
            RecipeCard(r) { nav.go(Routes.recipe(r.id, meal)) }
            Spacer(Modifier.height(8.dp))
            Text("Dotknij przepisu, aby zobaczyć szczegóły i dodać go do dziennika.", color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall)
        }
    }
}
