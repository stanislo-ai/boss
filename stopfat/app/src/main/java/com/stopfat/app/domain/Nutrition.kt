package com.stopfat.app.domain

import com.stopfat.app.data.DietType
import com.stopfat.app.data.Sex
import com.stopfat.app.data.UserProfile
import kotlin.math.max
import kotlin.math.roundToInt

const val KCAL_PER_KG_FAT = 7700.0

data class MacroTargets(val kcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)

object Nutrition {

    /** Mifflin–St Jeor basal metabolic rate. */
    fun bmr(sex: Sex, weightKg: Double, heightCm: Int, age: Int): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
        return if (sex == Sex.MALE) base + 5.0 else base - 161.0
    }

    fun tdee(p: UserProfile, weightKg: Double = p.weightKg): Double =
        bmr(p.sex, weightKg, p.heightCm, p.age) * p.activity.factor

    fun bmi(weightKg: Double, heightCm: Int): Double {
        val m = heightCm / 100.0
        return if (m <= 0) 0.0 else weightKg / (m * m)
    }

    fun bmiCategory(bmi: Double): String = when {
        bmi <= 0 -> "-"
        bmi < 18.5 -> "Niedowaga"
        bmi < 25 -> "Waga prawidłowa"
        bmi < 30 -> "Nadwaga"
        bmi < 35 -> "Otyłość I stopnia"
        bmi < 40 -> "Otyłość II stopnia"
        else -> "Otyłość III stopnia"
    }

    /** Estimated body fat % (Deurenberg formula, BMI based). */
    fun bodyFatPercent(p: UserProfile, weightKg: Double = p.weightKg): Double {
        val b = bmi(weightKg, p.heightCm)
        val sexFactor = if (p.sex == Sex.MALE) 1 else 0
        return (1.2 * b + 0.23 * p.age - 10.8 * sexFactor - 5.4).coerceIn(3.0, 60.0)
    }

    fun minimumCalories(sex: Sex): Int = if (sex == Sex.MALE) 1500 else 1200

    /** Daily calorie goal – manual override if set, otherwise derived from the weight-loss plan. */
    fun calorieGoal(p: UserProfile): Int {
        if (p.calorieGoalOverride > 0) return p.calorieGoalOverride
        return WeightLossPlanner.plan(p).recommendedIntake
    }

    fun macroTargets(p: UserProfile, kcal: Int = calorieGoal(p)): MacroTargets {
        val diet = p.diet
        // Protein: at least 1.6 g per kg of body weight when reducing, but follow the diet split if higher.
        val proteinFromPct = kcal * diet.proteinPct / 100.0 / 4.0
        val proteinFloor = if (diet == DietType.KETO) 1.4 * p.weightKg else 1.6 * p.weightKg
        val protein = max(proteinFromPct, proteinFloor).coerceAtMost(kcal * 0.4 / 4.0)
        val fat = kcal * diet.fatPct / 100.0 / 9.0
        val carbs = max(0.0, (kcal - protein * 4.0 - fat * 9.0) / 4.0)
        return MacroTargets(kcal, protein.roundToInt(), carbs.roundToInt(), fat.roundToInt())
    }

    /** kcal burned by walking, roughly 0.5 kcal per kg per km, ~1300 steps per km. */
    fun stepsToKcal(steps: Int, weightKg: Double): Double = steps / 1300.0 * 0.5 * weightKg

    fun kcalToSteps(kcal: Double, weightKg: Double): Int =
        if (weightKg <= 0) 0 else (kcal / (0.5 * weightKg) * 1300.0).roundToInt()

    fun stepsToKm(steps: Int, heightCm: Int): Double = steps * heightCm * 0.415 / 100_000.0

    /** MET based estimate. */
    fun workoutKcal(met: Double, weightKg: Double, minutes: Int): Double = met * weightKg * minutes / 60.0

    fun waterGoalFor(weightKg: Double): Int = ((weightKg * 33).roundToInt() / 250) * 250
}
