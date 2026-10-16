package com.stopfat.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stopfat_prefs")

class ProfileStore(private val context: Context) {

    private object K {
        val name = stringPreferencesKey("name")
        val sex = stringPreferencesKey("sex")
        val age = intPreferencesKey("age")
        val height = intPreferencesKey("height")
        val weight = doublePreferencesKey("weight")
        val target = doublePreferencesKey("target")
        val targetDate = longPreferencesKey("target_date")
        val activity = stringPreferencesKey("activity")
        val diet = stringPreferencesKey("diet")
        val stepGoal = intPreferencesKey("step_goal")
        val waterGoal = intPreferencesKey("water_goal")
        val kcalOverride = intPreferencesKey("kcal_override")
        val onboarded = booleanPreferencesKey("onboarded")
        val apiKey = stringPreferencesKey("api_key")
        val model = stringPreferencesKey("ai_model")
        val reminders = booleanPreferencesKey("reminders")
        val fastingStart = longPreferencesKey("fasting_start")
        val fastingHours = intPreferencesKey("fasting_hours")
        val workoutPlan = stringPreferencesKey("workout_plan_json")
        val workoutPrefs = stringPreferencesKey("workout_prefs_json")
        val baseUrl = stringPreferencesKey("api_base_url")
        val stepBaseline = longPreferencesKey("step_baseline")
        val stepBaselineDay = longPreferencesKey("step_baseline_day")
        val stepLastCounter = longPreferencesKey("step_last_counter")
        val stepLastDay = longPreferencesKey("step_last_day")
        val provider = stringPreferencesKey("ai_provider")
        val geminiKey = stringPreferencesKey("gemini_key")
        val geminiModel = stringPreferencesKey("gemini_model")
        val insulin = booleanPreferencesKey("insulin_resistance")
    }

    val profile: Flow<UserProfile> = context.dataStore.data.map { p ->
        val d = UserProfile()
        UserProfile(
            name = p[K.name] ?: d.name,
            sex = p[K.sex]?.let { runCatching { Sex.valueOf(it) }.getOrNull() } ?: d.sex,
            age = p[K.age] ?: d.age,
            heightCm = p[K.height] ?: d.heightCm,
            weightKg = p[K.weight] ?: d.weightKg,
            targetWeightKg = p[K.target] ?: d.targetWeightKg,
            targetDateEpochDay = p[K.targetDate] ?: d.targetDateEpochDay,
            activity = p[K.activity]?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() } ?: d.activity,
            diet = p[K.diet]?.let { runCatching { DietType.valueOf(it) }.getOrNull() } ?: d.diet,
            stepGoal = p[K.stepGoal] ?: d.stepGoal,
            waterGoalMl = p[K.waterGoal] ?: d.waterGoalMl,
            calorieGoalOverride = p[K.kcalOverride] ?: 0,
            onboarded = p[K.onboarded] ?: false,
            apiKey = p[K.apiKey] ?: "",
            aiModel = p[K.model] ?: DEFAULT_AI_MODEL,
            remindersEnabled = p[K.reminders] ?: true,
            fastingStartMillis = p[K.fastingStart] ?: 0L,
            fastingHours = p[K.fastingHours] ?: 16,
            // Installs from before Gemini support only had a Claude key – keep them on Claude.
            aiProvider = p[K.provider]?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }
                ?: if (!p[K.apiKey].isNullOrBlank()) AiProvider.CLAUDE else AiProvider.GEMINI,
            geminiKey = p[K.geminiKey] ?: "",
            geminiModel = p[K.geminiModel] ?: DEFAULT_GEMINI_MODEL,
            insulinResistance = p[K.insulin] ?: false,
        )
    }

    val baseUrl: Flow<String> = context.dataStore.data.map { it[K.baseUrl] ?: "" }
    val workoutPlanJson: Flow<String?> = context.dataStore.data.map { it[K.workoutPlan] }
    val workoutPrefsJson: Flow<String?> = context.dataStore.data.map { it[K.workoutPrefs] }

    suspend fun current(): UserProfile = profile.first()

    suspend fun save(p: UserProfile) {
        context.dataStore.edit {
            it[K.name] = p.name
            it[K.sex] = p.sex.name
            it[K.age] = p.age
            it[K.height] = p.heightCm
            it[K.weight] = p.weightKg
            it[K.target] = p.targetWeightKg
            it[K.targetDate] = p.targetDateEpochDay
            it[K.activity] = p.activity.name
            it[K.diet] = p.diet.name
            it[K.stepGoal] = p.stepGoal
            it[K.waterGoal] = p.waterGoalMl
            it[K.kcalOverride] = p.calorieGoalOverride
            it[K.onboarded] = p.onboarded
            it[K.apiKey] = p.apiKey.trim()
            it[K.model] = p.aiModel
            it[K.reminders] = p.remindersEnabled
            it[K.fastingStart] = p.fastingStartMillis
            it[K.fastingHours] = p.fastingHours
            it[K.provider] = p.aiProvider.name
            it[K.geminiKey] = p.geminiKey.trim()
            it[K.geminiModel] = p.geminiModel
            it[K.insulin] = p.insulinResistance
        }
    }

    suspend fun update(transform: (UserProfile) -> UserProfile) = save(transform(current()))

    suspend fun setBaseUrl(url: String) = context.dataStore.edit { it[K.baseUrl] = url.trim() }

    suspend fun setWorkoutPlan(json: String?) = context.dataStore.edit {
        if (json == null) it.remove(K.workoutPlan) else it[K.workoutPlan] = json
    }

    suspend fun setWorkoutPrefs(json: String) = context.dataStore.edit { it[K.workoutPrefs] = json }

    // ---- step counter bookkeeping ----
    data class StepState(val baseline: Long, val baselineDay: Long, val lastCounter: Long, val lastDay: Long)

    suspend fun stepState(): StepState {
        val p = context.dataStore.data.first()
        return StepState(p[K.stepBaseline] ?: -1L, p[K.stepBaselineDay] ?: -1L, p[K.stepLastCounter] ?: -1L, p[K.stepLastDay] ?: -1L)
    }

    suspend fun saveStepState(s: StepState) = context.dataStore.edit {
        it[K.stepBaseline] = s.baseline
        it[K.stepBaselineDay] = s.baselineDay
        it[K.stepLastCounter] = s.lastCounter
        it[K.stepLastDay] = s.lastDay
    }
}
