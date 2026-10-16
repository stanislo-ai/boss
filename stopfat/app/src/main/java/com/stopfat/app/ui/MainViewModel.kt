package com.stopfat.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stopfat.app.StopFatApp
import com.stopfat.app.ai.AiService
import com.stopfat.app.data.ChatLine
import com.stopfat.app.data.Food
import com.stopfat.app.data.FoodGuess
import com.stopfat.app.data.MealType
import com.stopfat.app.data.PlanJson
import com.stopfat.app.data.Recipe
import com.stopfat.app.data.UserProfile
import com.stopfat.app.data.WorkoutPlan
import com.stopfat.app.data.WorkoutPreferences
import com.stopfat.app.data.db.ChatMessage
import com.stopfat.app.data.db.CustomFood
import com.stopfat.app.data.db.DayStat
import com.stopfat.app.data.db.FastingLog
import com.stopfat.app.data.db.FoodEntry
import com.stopfat.app.data.db.WeightEntry
import com.stopfat.app.data.db.WorkoutLog
import com.stopfat.app.domain.Nutrition
import com.stopfat.app.domain.WorkoutGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    val container = (app as StopFatApp).container
    private val db = container.db
    private val store = container.store

    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val profileOrNull: StateFlow<UserProfile?> = store.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val profile: StateFlow<UserProfile> = store.profile.stateIn(viewModelScope, SharingStarted.Eagerly, UserProfile())
    val baseUrl: StateFlow<String> = store.baseUrl.state("")

    private val today: Long get() = LocalDate.now().toEpochDay()

    private val _selectedDay = MutableStateFlow(LocalDate.now().toEpochDay())
    val selectedDay = _selectedDay.asStateFlow()

    val dayEntries: StateFlow<List<FoodEntry>> = _selectedDay.flatMapLatest { db.food().entriesForDay(it) }.state(emptyList())
    val todayEntries: StateFlow<List<FoodEntry>> = db.food().entriesForDay(LocalDate.now().toEpochDay()).state(emptyList())
    val todayStat: StateFlow<DayStat?> = db.dayStats().day(LocalDate.now().toEpochDay()).state(null)
    val todayWorkouts: StateFlow<List<WorkoutLog>> = db.workouts().forDay(LocalDate.now().toEpochDay()).state(emptyList())
    val weights: StateFlow<List<WeightEntry>> = db.weights().all().state(emptyList())
    val allStats: StateFlow<List<DayStat>> = db.dayStats().all().state(emptyList())
    val workoutLogs: StateFlow<List<WorkoutLog>> = db.workouts().all().state(emptyList())
    val customFoods: StateFlow<List<CustomFood>> = db.customFoods().all().state(emptyList())
    val recentEntries: StateFlow<List<FoodEntry>> = db.food().recent().state(emptyList())
    val loggedDays: StateFlow<List<Long>> = db.food().loggedDays().state(emptyList())
    val fastingLogs: StateFlow<List<FastingLog>> = db.fasting().all().state(emptyList())
    val chat: StateFlow<List<ChatMessage>> = db.chat().all().state(emptyList())
    val kcalLast7 = db.food().kcalPerDay(LocalDate.now().toEpochDay() - 6, LocalDate.now().toEpochDay()).state(emptyList())
    val kcalLast30 = db.food().kcalPerDay(LocalDate.now().toEpochDay() - 29, LocalDate.now().toEpochDay()).state(emptyList())

    val workoutPlan: StateFlow<WorkoutPlan?> = store.workoutPlanJson.map { it?.let(PlanJson::fromJson) }.state(null)
    val workoutPrefs: StateFlow<WorkoutPreferences> = store.workoutPrefsJson.map { PlanJson.prefsFromJson(it) }.state(WorkoutPreferences())

    // ---- transient UI state ----
    val chatBusy = MutableStateFlow(false)
    val chatError = MutableStateFlow<String?>(null)
    val planBusy = MutableStateFlow(false)
    val planMessage = MutableStateFlow<String?>(null)
    val aiRecipes = MutableStateFlow<List<Recipe>>(emptyList())
    val aiScanCount: StateFlow<Int> = db.food().countBySource(SOURCE_PHOTO).state(0)

    fun selectDay(day: Long) { _selectedDay.value = day.coerceAtMost(today) }

    // ---------- profile ----------
    fun saveProfile(p: UserProfile) = viewModelScope.launch {
        val old = store.current()
        store.save(p)
        if (old.weightKg != p.weightKg || !old.onboarded) db.weights().upsert(WeightEntry(today, p.weightKg))
    }

    fun setBaseUrl(url: String) = viewModelScope.launch { store.setBaseUrl(url) }

    fun calorieGoal(p: UserProfile) = Nutrition.calorieGoal(p)

    // ---------- food ----------
    fun addFood(meal: MealType, name: String, grams: Double, kcal: Double, p: Double, c: Double, f: Double, source: String, day: Long = _selectedDay.value) =
        viewModelScope.launch {
            db.food().insert(FoodEntry(epochDay = day, meal = meal.name, name = name, grams = grams, kcal = kcal, protein = p, carbs = c, fat = f, source = source))
        }

    fun addFood(meal: MealType, food: Food, grams: Double, source: String = "baza") {
        val n = food.scaled(grams)
        addFood(meal, food.name, grams, n.kcal, n.protein, n.carbs, n.fat, source)
    }

    fun addGuesses(meal: MealType, items: List<FoodGuess>, source: String) = viewModelScope.launch {
        val day = _selectedDay.value
        db.food().insertAll(items.map { FoodEntry(epochDay = day, meal = meal.name, name = it.name, grams = it.grams, kcal = it.kcal, protein = it.protein, carbs = it.carbs, fat = it.fat, source = source) })
    }

    /** Recipes are stored with "grams" = servings × 100, so editing the amount scales per serving. */
    fun addRecipe(meal: MealType, r: Recipe, servings: Double) = addFood(
        meal, r.name, servings * 100.0, r.kcal * servings, r.protein * servings, r.carbs * servings, r.fat * servings, SOURCE_RECIPE,
    )

    fun deleteFood(id: Long) = viewModelScope.launch { db.food().delete(id) }

    fun updateFoodGrams(entry: FoodEntry, grams: Double) = viewModelScope.launch {
        if (entry.grams <= 0) return@launch
        val f = grams / entry.grams
        db.food().update(entry.copy(grams = grams, kcal = entry.kcal * f, protein = entry.protein * f, carbs = entry.carbs * f, fat = entry.fat * f))
    }

    fun saveCustomFood(food: CustomFood) = viewModelScope.launch { db.customFoods().upsert(food) }
    fun deleteCustomFood(id: Long) = viewModelScope.launch { db.customFoods().delete(id) }

    suspend fun customByBarcode(code: String): CustomFood? = db.customFoods().byBarcode(code)

    fun copyYesterday() = viewModelScope.launch {
        val day = _selectedDay.value
        val prev = db.food().entriesForDayOnce(day - 1)
        db.food().insertAll(prev.map { it.copy(id = 0, epochDay = day, createdAt = System.currentTimeMillis()) })
    }

    // ---------- water / weight ----------
    fun addWater(ml: Int) = viewModelScope.launch {
        val s = db.dayStats().dayOnce(today) ?: DayStat(today)
        db.dayStats().upsert(s.copy(waterMl = (s.waterMl + ml).coerceIn(0, 10_000)))
    }

    fun logWeight(kg: Double, day: Long = today) = viewModelScope.launch {
        db.weights().upsert(WeightEntry(day, kg))
        if (day >= (weights.value.maxOfOrNull { it.epochDay } ?: 0L)) store.update { it.copy(weightKg = kg) }
    }

    fun deleteWeight(day: Long) = viewModelScope.launch { db.weights().delete(day) }

    // ---------- steps ----------
    fun refreshSteps() = viewModelScope.launch { container.stepCounter.readOnce() }

    // ---------- fasting ----------
    fun startFast(hours: Int) = viewModelScope.launch { store.update { it.copy(fastingStartMillis = System.currentTimeMillis(), fastingHours = hours) } }

    fun stopFast() = viewModelScope.launch {
        val p = store.current()
        if (p.fastingStartMillis > 0) {
            val now = System.currentTimeMillis()
            if (now - p.fastingStartMillis > 60 * 60 * 1000L) db.fasting().insert(FastingLog(startMillis = p.fastingStartMillis, endMillis = now, targetHours = p.fastingHours))
        }
        store.update { it.copy(fastingStartMillis = 0L) }
    }

    // ---------- workouts ----------
    fun generatePlan(prefs: WorkoutPreferences, useAi: Boolean) = viewModelScope.launch {
        planBusy.value = true
        planMessage.value = null
        store.setWorkoutPrefs(PlanJson.prefsToJson(prefs))
        val ai = if (useAi) container.ai() else null
        val plan = if (ai != null) {
            try {
                ai.workoutPlan(store.current(), prefs)
            } catch (e: Exception) {
                planMessage.value = "${e.message}\nWygenerowano plan offline."
                WorkoutGenerator.generate(prefs)
            }
        } else {
            WorkoutGenerator.generate(prefs)
        }
        store.setWorkoutPlan(PlanJson.toJson(plan))
        planBusy.value = false
    }

    fun clearPlan() = viewModelScope.launch { store.setWorkoutPlan(null) }

    fun logWorkout(name: String, minutes: Int, sets: Int) = viewModelScope.launch {
        val p = store.current()
        val plan = workoutPlan.value
        val met = WorkoutGenerator.metFor(plan?.location ?: com.stopfat.app.data.WorkoutLocation.GYM, workoutPrefs.value.goal)
        val kcal = Nutrition.workoutKcal(met, p.weightKg, minutes).roundToInt()
        db.workouts().insert(WorkoutLog(epochDay = today, name = name, minutes = minutes, kcal = kcal, completedSets = sets))
    }

    fun logCustomActivity(name: String, minutes: Int, met: Double) = viewModelScope.launch {
        val p = store.current()
        db.workouts().insert(WorkoutLog(epochDay = today, name = name, minutes = minutes, kcal = Nutrition.workoutKcal(met, p.weightKg, minutes).roundToInt()))
    }

    fun deleteWorkout(id: Long) = viewModelScope.launch { db.workouts().delete(id) }

    // ---------- AI ----------
    suspend fun ai(): AiService? = container.ai()

    fun todaySummary(): String {
        val e = todayEntries.value
        val kcal = e.sumOf { it.kcal }.roundToInt()
        val p = e.sumOf { it.protein }.roundToInt()
        val steps = todayStat.value?.steps ?: 0
        val water = todayStat.value?.waterMl ?: 0
        val meals = e.groupBy { it.meal }.entries.joinToString("; ") { (m, list) ->
            "${MealType.fromName(m).label}: " + list.joinToString(", ") { "${it.name} ${it.grams.roundToInt()} g" }
        }
        val burned = todayWorkouts.value.sumOf { it.kcal }
        return "zjedzone $kcal kcal ($p g białka), kroki $steps, woda $water ml, trening $burned kcal. Posiłki: ${meals.ifBlank { "brak wpisów" }}."
    }

    fun sendChat(text: String) {
        val question = text.trim()
        if (question.isEmpty() || chatBusy.value) return
        viewModelScope.launch {
            chatError.value = null
            val history = chat.value.map { ChatLine(it.fromUser, it.text) }
            db.chat().insert(ChatMessage(fromUser = true, text = question))
            val ai = container.ai()
            if (ai == null) {
                chatError.value = "Dodaj klucz API w Ustawieniach, aby rozmawiać z AI Coachem."
                return@launch
            }
            chatBusy.value = true
            try {
                val answer = ai.coach(store.current(), todaySummary(), history, question)
                db.chat().insert(ChatMessage(fromUser = false, text = answer))
            } catch (e: Exception) {
                chatError.value = e.message ?: "Błąd AI"
            } finally {
                chatBusy.value = false
            }
        }
    }

    fun clearChat() = viewModelScope.launch { db.chat().clear() }

    companion object {
        const val SOURCE_PHOTO = "AI zdjęcie"
        const val SOURCE_TEXT = "AI opis"
        const val SOURCE_BARCODE = "kod kreskowy"
        const val SOURCE_RECIPE = "przepis"
    }
}
