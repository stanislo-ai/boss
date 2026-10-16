package com.stopfat.app.ai

import com.stopfat.app.data.ChatLine
import com.stopfat.app.data.FoodGuess
import com.stopfat.app.data.PlannedExercise
import com.stopfat.app.data.Recipe
import com.stopfat.app.data.UserProfile
import com.stopfat.app.data.WorkoutDay
import com.stopfat.app.data.WorkoutPlan
import com.stopfat.app.data.WorkoutPreferences
import com.stopfat.app.domain.Nutrition
import com.stopfat.app.domain.WeightLossPlanner
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

/** All StopFat AI features: prompts, JSON schemas and response parsing. */
class AiService(private val client: AiClient) {

    data class FoodAnalysis(val items: List<FoodGuess>, val notes: String)

    private val baseSystem = """
        Jesteś StopFat AI – doświadczonym dietetykiem klinicznym i trenerem personalnym.
        Odpowiadasz zawsze po polsku, konkretnie i życzliwie. Bazujesz na aktualnej wiedzy naukowej
        (deficyt kaloryczny, odpowiednia podaż białka, trening siłowy, sen, NEAT).
        Nie stawiasz diagnoz medycznych; przy chorobach, ciąży lub zaburzeniach odżywiania zalecasz konsultację z lekarzem.
    """.trimIndent()

    // ---------- Food recognition ----------

    private val foodSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "items" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "name" to mapOf("type" to "string"),
                        "grams" to mapOf("type" to "number"),
                        "kcal" to mapOf("type" to "number"),
                        "protein" to mapOf("type" to "number"),
                        "carbs" to mapOf("type" to "number"),
                        "fat" to mapOf("type" to "number"),
                        "confidence" to mapOf("type" to "string"),
                    ),
                    "required" to listOf("name", "grams", "kcal", "protein", "carbs", "fat", "confidence"),
                    "additionalProperties" to false,
                ),
            ),
            "notes" to mapOf("type" to "string"),
        ),
        "required" to listOf("items", "notes"),
        "additionalProperties" to false,
    )

    private val foodInstructions = """
        Zwróć WYŁĄCZNIE obiekt JSON w formacie:
        {"items":[{"name":"nazwa po polsku","grams":liczba,"kcal":liczba,"protein":liczba,"carbs":liczba,"fat":liczba,"confidence":"wysoka|średnia|niska"}],"notes":"krótka uwaga"}
        Wartości kcal i makroskładników podawaj dla CAŁEJ porcji (nie na 100 g). Rozbij posiłek na osobne składniki
        (np. ryż, kurczak, sos, olej użyty do smażenia). Szacuj realistycznie wielkości porcji. Jeśli to nie jest jedzenie, zwróć pustą listę i wyjaśnij w notes.
    """.trimIndent()

    suspend fun analyzePhoto(jpegBase64: String, hint: String): FoodAnalysis {
        val prompt = buildString {
            append("Przeanalizuj zdjęcie posiłku. Rozpoznaj każdy produkt, oszacuj gramaturę i wartości odżywcze.\n")
            if (hint.isNotBlank()) append("Dodatkowy opis od użytkownika: \"$hint\"\n")
            append(foodInstructions)
        }
        val answer = client.ask(baseSystem, prompt, AiClient.Effort.MEDIUM, foodSchema, imageJpegBase64 = jpegBase64)
        return parseFood(answer)
    }

    suspend fun analyzeText(description: String): FoodAnalysis {
        val prompt = "Użytkownik opisał co zjadł: \"$description\"\nOblicz kaloryczność i makroskładniki każdego produktu.\n$foodInstructions"
        val answer = client.ask(baseSystem, prompt, AiClient.Effort.LOW, foodSchema)
        return parseFood(answer)
    }

    // ---------- Workout plans ----------

    private val workoutSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "title" to mapOf("type" to "string"),
            "summary" to mapOf("type" to "string"),
            "days" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "name" to mapOf("type" to "string"),
                        "focus" to mapOf("type" to "string"),
                        "exercises" to mapOf(
                            "type" to "array",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "name" to mapOf("type" to "string"),
                                    "sets" to mapOf("type" to "integer"),
                                    "reps" to mapOf("type" to "string"),
                                    "restSec" to mapOf("type" to "integer"),
                                    "tip" to mapOf("type" to "string"),
                                ),
                                "required" to listOf("name", "sets", "reps", "restSec", "tip"),
                                "additionalProperties" to false,
                            ),
                        ),
                        "finisher" to mapOf("type" to "string"),
                    ),
                    "required" to listOf("name", "focus", "exercises", "finisher"),
                    "additionalProperties" to false,
                ),
            ),
            "tips" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
        ),
        "required" to listOf("title", "summary", "days", "tips"),
        "additionalProperties" to false,
    )

    suspend fun workoutPlan(profile: UserProfile, prefs: WorkoutPreferences): WorkoutPlan {
        val prompt = """
            Ułóż spersonalizowany tygodniowy plan treningowy.
            Dane: ${profile.sex.label}, ${profile.age} lat, ${profile.heightCm} cm, ${"%.1f".format(profile.weightKg)} kg, cel: ${"%.1f".format(profile.targetWeightKg)} kg.
            Miejsce: ${prefs.location.label}${if (prefs.location.name == "HOME") ", sprzęt: ${prefs.equipment.label}" else ""}.
            Poziom: ${prefs.level.label}. Cel treningowy: ${prefs.goal.label}.
            Dni treningowe w tygodniu: ${prefs.daysPerWeek}. Czas jednej sesji: ${prefs.minutes} min.
            ${if (prefs.notes.isNotBlank()) "Uwagi / kontuzje / preferencje: ${prefs.notes}" else ""}
            Każdy dzień: 5–8 ćwiczeń z rozgrzewką ujętą jako pierwsze ćwiczenie, serie, powtórzenia (np. "8-12" albo "40 s"),
            przerwa w sekundach i krótka wskazówka techniczna. "finisher" to krótkie cardio/interwały na koniec (lub pusty tekst).
            "tips" – 4–6 wskazówek dot. progresji, regeneracji i diety. Nazwy ćwiczeń po polsku.
            Zwróć WYŁĄCZNIE JSON: {"title":"","summary":"","days":[{"name":"","focus":"","exercises":[{"name":"","sets":3,"reps":"","restSec":60,"tip":""}],"finisher":""}],"tips":[""]}
        """.trimIndent()
        val answer = client.ask(baseSystem, prompt, AiClient.Effort.MEDIUM, workoutSchema)
        val o = JSONObject(extractJson(answer))
        val days = o.optJSONArray("days") ?: JSONArray()
        return WorkoutPlan(
            title = o.optString("title", "Plan treningowy AI"),
            summary = o.optString("summary"),
            days = (0 until days.length()).map { i ->
                val d = days.getJSONObject(i)
                val ex = d.optJSONArray("exercises") ?: JSONArray()
                WorkoutDay(
                    name = d.optString("name", "Dzień ${i + 1}"),
                    focus = d.optString("focus"),
                    exercises = (0 until ex.length()).map { j ->
                        val e = ex.getJSONObject(j)
                        PlannedExercise(
                            name = e.optString("name"),
                            sets = e.optInt("sets", 3).coerceIn(1, 10),
                            reps = e.optString("reps", "10"),
                            restSec = e.optInt("restSec", 60).coerceIn(0, 600),
                            tip = e.optString("tip"),
                        )
                    },
                    finisher = d.optString("finisher"),
                )
            }.filter { it.exercises.isNotEmpty() },
            tips = o.optJSONArray("tips").toStringList(),
            source = "AI Claude",
            location = prefs.location,
        ).also { if (it.days.isEmpty()) throw AiException("AI nie zwróciło żadnego dnia treningowego.") }
    }

    // ---------- Weight-loss analysis ----------

    suspend fun weightLossAnalysis(profile: UserProfile, avgKcal7d: Int?, avgSteps7d: Int?, weightTrend: String): String {
        val plan = WeightLossPlanner.plan(profile)
        val prompt = """
            Przygotuj szczegółową, spersonalizowaną analizę: jak bezpiecznie schudnąć z ${"%.1f".format(profile.weightKg)} kg do ${"%.1f".format(profile.targetWeightKg)} kg
            do dnia ${WeightLossPlanner.formatDate(profile.targetDate)} (${plan.daysLeft} dni).
            Dane: ${profile.sex.label}, ${profile.age} lat, ${profile.heightCm} cm, aktywność: ${profile.activity.label}, dieta: ${profile.diet.label}.
            Wyliczenia aplikacji: BMR ${plan.bmr} kcal, TDEE ${plan.tdee} kcal, wymagany deficyt ${plan.requiredDailyDeficit} kcal/dzień,
            tempo ${"%.2f".format(plan.weeklyLossKg)} kg/tydz. (${"%.2f".format(plan.weeklyLossPercent)}% masy), ocena: ${plan.safety.label},
            zalecane spożycie ${plan.recommendedIntake} kcal, dodatkowe kroki potrzebne: ${plan.extraStepsNeeded}, bezpieczna data celu: ${WeightLossPlanner.formatDate(plan.safeTargetDate)}.
            Ostatnie 7 dni: średnio ${avgKcal7d ?: "brak danych"} kcal/dzień, ${avgSteps7d ?: "brak danych"} kroków/dzień. Trend wagi: $weightTrend.

            Struktura odpowiedzi (nagłówki zaczynaj od "## "):
            ## Ocena celu – czy termin jest realny i bezpieczny
            ## Kalorie i makroskładniki – konkretne liczby
            ## Plan tygodniowy – etapy i kamienie milowe (miesiąc po miesiącu)
            ## Ruch – kroki, trening siłowy, cardio
            ## Przykładowy dzień jedzenia – 4 posiłki z kaloriami
            ## Najczęstsze pułapki i jak ich uniknąć
            ## Co zrobić, gdy waga stoi
            Pisz zwięźle, w punktach zaczynających się od "- ". Używaj **pogrubień** dla kluczowych liczb.
        """.trimIndent()
        return client.ask(baseSystem, prompt, AiClient.Effort.MEDIUM)
    }

    // ---------- Recipes ----------

    private val recipeSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "name" to mapOf("type" to "string"),
            "emoji" to mapOf("type" to "string"),
            "minutes" to mapOf("type" to "integer"),
            "servings" to mapOf("type" to "integer"),
            "kcal" to mapOf("type" to "integer"),
            "protein" to mapOf("type" to "integer"),
            "carbs" to mapOf("type" to "integer"),
            "fat" to mapOf("type" to "integer"),
            "ingredients" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            "steps" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
        ),
        "required" to listOf("name", "emoji", "minutes", "servings", "kcal", "protein", "carbs", "fat", "ingredients", "steps"),
        "additionalProperties" to false,
    )

    suspend fun recipeFromIngredients(ingredients: String, maxKcal: Int, profile: UserProfile): Recipe {
        val prompt = """
            Wymyśl jeden zdrowy, smaczny przepis wykorzystujący głównie te składniki: $ingredients.
            Można dodać podstawowe przyprawy, olej, sól, pieprz. Dieta użytkownika: ${profile.diet.label}.
            Jedna porcja powinna mieć maksymalnie ok. $maxKcal kcal. Podaj kalorie i makro NA PORCJĘ.
            Składniki z gramaturą, kroki krótko i konkretnie.
            Zwróć WYŁĄCZNIE JSON: {"name":"","emoji":"🍳","minutes":20,"servings":2,"kcal":450,"protein":30,"carbs":40,"fat":15,"ingredients":[""],"steps":[""]}
        """.trimIndent()
        val answer = client.ask(baseSystem, prompt, AiClient.Effort.LOW, recipeSchema)
        val o = JSONObject(extractJson(answer))
        return Recipe(
            id = "ai_" + System.currentTimeMillis(),
            name = o.optString("name", "Przepis AI"),
            emoji = o.optString("emoji", "🍳").ifBlank { "🍳" },
            category = "AI",
            minutes = o.optInt("minutes", 20),
            servings = o.optInt("servings", 1).coerceAtLeast(1),
            kcal = o.optInt("kcal"),
            protein = o.optInt("protein"),
            carbs = o.optInt("carbs"),
            fat = o.optInt("fat"),
            ingredients = o.optJSONArray("ingredients").toStringList(),
            steps = o.optJSONArray("steps").toStringList(),
            tags = listOf("AI"),
        )
    }

    // ---------- Coach chat ----------

    suspend fun coach(profile: UserProfile, todaySummary: String, history: List<ChatLine>, question: String): String {
        val plan = WeightLossPlanner.plan(profile)
        val macros = Nutrition.macroTargets(profile)
        val system = """
            $baseSystem
            Jesteś osobistym coachem użytkownika w aplikacji StopFat. Znasz jego dane:
            imię: ${profile.name.ifBlank { "nie podano" }}, ${profile.sex.label}, ${profile.age} lat, ${profile.heightCm} cm,
            waga ${"%.1f".format(profile.weightKg)} kg → cel ${"%.1f".format(profile.targetWeightKg)} kg do ${WeightLossPlanner.formatDate(profile.targetDate)}.
            Dzienny cel: ${macros.kcal} kcal, B ${macros.proteinG} g, W ${macros.carbsG} g, T ${macros.fatG} g. TDEE ${plan.tdee} kcal.
            Dieta: ${profile.diet.label}. Cel kroków: ${profile.stepGoal}.
            Dzisiaj (${LocalDate.now()}): $todaySummary
            Odpowiadaj krótko (maks. ~150 słów, chyba że użytkownik prosi o więcej), praktycznie i motywująco.
            Używaj prostych list z "- " i **pogrubień** dla liczb.
        """.trimIndent()
        return client.ask(system, question, AiClient.Effort.LOW, history = history.takeLast(16), maxTokens = 8000L)
    }

    // ---------- helpers ----------

    private fun parseFood(answer: String): FoodAnalysis {
        val o = try {
            JSONObject(extractJson(answer))
        } catch (e: Exception) {
            throw AiException("Nie udało się odczytać odpowiedzi AI. Spróbuj ponownie.", e)
        }
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i ->
            val it = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = it.optString("name").trim()
            if (name.isEmpty()) return@mapNotNull null
            FoodGuess(
                name = name.replaceFirstChar { c -> c.uppercase() },
                grams = it.optDouble("grams", 100.0).takeIf { g -> !g.isNaN() && g > 0 } ?: 100.0,
                kcal = it.optDouble("kcal", 0.0).nonNeg(),
                protein = it.optDouble("protein", 0.0).nonNeg(),
                carbs = it.optDouble("carbs", 0.0).nonNeg(),
                fat = it.optDouble("fat", 0.0).nonNeg(),
                confidence = it.optString("confidence"),
            )
        }
        return FoodAnalysis(items, o.optString("notes"))
    }

    private fun Double.nonNeg(): Double = if (isNaN() || this < 0) 0.0 else (this * 10).roundToInt() / 10.0

    companion object {
        /** Extracts the first JSON object from a model answer (tolerates ``` fences and prose around it). */
        fun extractJson(text: String): String {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) throw AiException("AI nie zwróciło danych w oczekiwanym formacie.")
            return text.substring(start, end + 1)
        }
    }
}

fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
}
