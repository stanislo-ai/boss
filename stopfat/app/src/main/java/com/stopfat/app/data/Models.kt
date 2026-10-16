package com.stopfat.app.data

import java.time.LocalDate

enum class Sex(val label: String) { MALE("Mężczyzna"), FEMALE("Kobieta") }

enum class ActivityLevel(val label: String, val description: String, val factor: Double) {
    SEDENTARY("Siedzący", "Praca biurowa, brak treningów", 1.2),
    LIGHT("Lekko aktywny", "Spacery, trening 1–3× w tygodniu", 1.375),
    MODERATE("Umiarkowany", "Trening 3–5× w tygodniu", 1.55),
    ACTIVE("Bardzo aktywny", "Trening 6–7× w tygodniu", 1.725),
    ATHLETE("Ekstremalny", "Praca fizyczna + codzienny trening", 1.9),
}

enum class DietType(val label: String, val proteinPct: Int, val fatPct: Int, val carbsPct: Int) {
    BALANCED("Zbilansowana", 25, 30, 45),
    HIGH_PROTEIN("Wysokobiałkowa", 35, 30, 35),
    LOW_CARB("Niskowęglowodanowa", 30, 45, 25),
    KETO("Ketogeniczna", 25, 70, 5),
    VEGETARIAN("Wegetariańska", 22, 30, 48),
    LOW_GI("Niski IG (insulinooporność)", 30, 35, 35),
}

enum class MealType(val label: String, val emoji: String) {
    BREAKFAST("Śniadanie", "🌅"),
    LUNCH("Obiad", "🍲"),
    DINNER("Kolacja", "🌙"),
    SNACK("Przekąski", "🍎");

    companion object {
        fun fromName(name: String?): MealType = entries.firstOrNull { it.name == name } ?: suggestedForHour(java.time.LocalTime.now().hour)

        fun suggestedForHour(hour: Int): MealType = when (hour) {
            in 4..10 -> BREAKFAST
            in 11..15 -> LUNCH
            in 16..17 -> SNACK
            else -> DINNER
        }
    }
}

enum class AiProvider(val label: String, val short: String) {
    GEMINI("Google Gemini – darmowy klucz", "Gemini"),
    CLAUDE("Anthropic Claude – płatny", "Claude"),
}

const val DEFAULT_AI_MODEL = "claude-opus-5-5"
const val DEFAULT_GEMINI_MODEL = "gemini-flash-latest"

val GEMINI_MODELS = listOf(
    "gemini-flash-latest" to "Gemini Flash (zawsze najnowszy) – zalecany",
    "gemini-3.8-flash" to "Gemini 3.8 Flash",
    "gemini-3.5-flash-lite" to "Gemini 3.5 Flash-Lite – najszybszy, największy darmowy limit",
)

val AI_MODELS = listOf(
    "claude-opus-5-5" to "Claude Opus 5.5 – najdokładniejszy",
    "claude-sonnet-5-5" to "Claude Sonnet 5.5 – szybszy i tańszy",
    "claude-haiku-5-5" to "Claude Haiku 5.5 – najszybszy",
)

data class UserProfile(
    val name: String = "",
    val sex: Sex = Sex.MALE,
    val age: Int = 30,
    val heightCm: Int = 175,
    val weightKg: Double = 85.0,
    val targetWeightKg: Double = 75.0,
    val targetDateEpochDay: Long = LocalDate.now().plusDays(120).toEpochDay(),
    val activity: ActivityLevel = ActivityLevel.LIGHT,
    val diet: DietType = DietType.BALANCED,
    val stepGoal: Int = 8000,
    val waterGoalMl: Int = 2500,
    val calorieGoalOverride: Int = 0,
    val onboarded: Boolean = false,
    val apiKey: String = "",
    val aiModel: String = DEFAULT_AI_MODEL,
    val remindersEnabled: Boolean = true,
    val fastingStartMillis: Long = 0L,
    val fastingHours: Int = 16,
    val aiProvider: AiProvider = AiProvider.GEMINI,
    val geminiKey: String = "",
    val geminiModel: String = DEFAULT_GEMINI_MODEL,
    val insulinResistance: Boolean = false,
) {
    val targetDate: LocalDate get() = LocalDate.ofEpochDay(targetDateEpochDay)

    /** Key of the currently selected AI provider. */
    val activeKey: String get() = if (aiProvider == AiProvider.GEMINI) geminiKey else apiKey
    val hasAi: Boolean get() = activeKey.isNotBlank()
}

data class Food(
    val id: String,
    val name: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val category: String,
    val unitGrams: Double = 100.0,
    val unitName: String = "porcja",
    val aliases: List<String> = emptyList(),
    val barcode: String? = null,
    /** Glycemic index, null when unknown. 0 = practically no carbohydrates. */
    val gi: Int? = null,
) {
    fun scaled(grams: Double) = Nutrients(
        kcal = kcal * grams / 100.0,
        protein = protein * grams / 100.0,
        carbs = carbs * grams / 100.0,
        fat = fat * grams / 100.0,
    )
}

data class Nutrients(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
) {
    operator fun plus(o: Nutrients) = Nutrients(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat)
    fun times(f: Double) = Nutrients(kcal * f, protein * f, carbs * f, fat * f)
}

/** A food item recognised from a photo, a text description or a barcode – editable before saving. */
data class FoodGuess(
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val confidence: String = "",
    /** Estimated glycemic index, -1 when unknown. */
    val gi: Int = -1,
) {
    fun withGrams(newGrams: Double): FoodGuess {
        if (grams <= 0.0) return copy(grams = newGrams)
        val f = newGrams / grams
        return copy(grams = newGrams, kcal = kcal * f, protein = protein * f, carbs = carbs * f, fat = fat * f)
    }
}

data class Recipe(
    val id: String,
    val name: String,
    val emoji: String,
    val category: String,
    val minutes: Int,
    val servings: Int,
    val kcal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val ingredients: List<String>,
    val steps: List<String>,
    val tags: List<String> = emptyList(),
)

enum class WorkoutLocation(val label: String) { GYM("Siłownia"), HOME("Dom") }

enum class WorkoutLevel(val label: String) { BEGINNER("Początkujący"), INTERMEDIATE("Średniozaawansowany"), ADVANCED("Zaawansowany") }

enum class WorkoutGoal(val label: String) { FAT_LOSS("Redukcja tkanki tłuszczowej"), MUSCLE("Budowa mięśni"), FITNESS("Kondycja i zdrowie") }

enum class HomeEquipment(val label: String) { NONE("Bez sprzętu"), DUMBBELLS("Hantle"), BANDS("Gumy oporowe"), FULL("Hantle + drążek + gumy") }

data class WorkoutPreferences(
    val location: WorkoutLocation = WorkoutLocation.GYM,
    val level: WorkoutLevel = WorkoutLevel.BEGINNER,
    val goal: WorkoutGoal = WorkoutGoal.FAT_LOSS,
    val daysPerWeek: Int = 3,
    val minutes: Int = 50,
    val equipment: HomeEquipment = HomeEquipment.NONE,
    val notes: String = "",
)

data class PlannedExercise(
    val name: String,
    val sets: Int,
    val reps: String,
    val restSec: Int,
    val tip: String = "",
)

data class WorkoutDay(
    val name: String,
    val focus: String,
    val exercises: List<PlannedExercise>,
    val finisher: String = "",
)

data class WorkoutPlan(
    val title: String,
    val summary: String,
    val days: List<WorkoutDay>,
    val tips: List<String>,
    val source: String,
    val location: WorkoutLocation = WorkoutLocation.GYM,
)

data class ChatLine(val fromUser: Boolean, val text: String)
