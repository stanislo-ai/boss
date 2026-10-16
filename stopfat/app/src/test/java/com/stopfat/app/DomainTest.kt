package com.stopfat.app

import com.stopfat.app.data.ActivityLevel
import com.stopfat.app.data.FoodCatalog
import com.stopfat.app.data.FoodGuess
import com.stopfat.app.data.HomeEquipment
import com.stopfat.app.data.PlanJson
import com.stopfat.app.data.ProfileStore.StepState
import com.stopfat.app.data.RecipeCatalog
import com.stopfat.app.data.Sex
import com.stopfat.app.data.UserProfile
import com.stopfat.app.data.WorkoutGoal
import com.stopfat.app.data.WorkoutLevel
import com.stopfat.app.data.WorkoutLocation
import com.stopfat.app.data.WorkoutPreferences
import com.stopfat.app.domain.AchievementInput
import com.stopfat.app.domain.Achievements
import com.stopfat.app.domain.LocalFoodParser
import com.stopfat.app.domain.Nutrition
import com.stopfat.app.domain.PlanSafety
import com.stopfat.app.domain.StepMath
import com.stopfat.app.domain.WeightLossPlanner
import com.stopfat.app.domain.WorkoutGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NutritionTest {
    @Test fun bmrMifflinStJeor() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 1780
        assertEquals(1780.0, Nutrition.bmr(Sex.MALE, 80.0, 180, 30), 0.01)
        // 10*65 + 6.25*165 - 5*28 - 161 = 1380.25
        assertEquals(1380.25, Nutrition.bmr(Sex.FEMALE, 65.0, 165, 28), 0.01)
    }

    @Test fun bmiAndCategory() {
        assertEquals(24.69, Nutrition.bmi(80.0, 180), 0.01)
        assertEquals("Waga prawidłowa", Nutrition.bmiCategory(24.69))
        assertEquals("Nadwaga", Nutrition.bmiCategory(27.0))
    }

    @Test fun macrosAddUpToCalories() {
        val p = UserProfile(weightKg = 90.0)
        val m = Nutrition.macroTargets(p, 2000)
        val kcal = m.proteinG * 4 + m.carbsG * 4 + m.fatG * 9
        assertTrue("macros $m sum to $kcal", kotlin.math.abs(kcal - 2000) < 30)
        assertTrue(m.proteinG >= 144) // 1.6 g/kg
    }

    @Test fun stepsKcalRoundTrip() {
        val kcal = Nutrition.stepsToKcal(10_000, 80.0)
        assertEquals(307.7, kcal, 1.0)
        assertEquals(10_000.0, Nutrition.kcalToSteps(kcal, 80.0).toDouble(), 2.0)
    }
}

class WeightLossPlannerTest {
    private val today = LocalDate.of(2026, 1, 1)

    private fun profile(weight: Double, target: Double, days: Long) = UserProfile(
        sex = Sex.MALE, age = 30, heightCm = 180, weightKg = weight, targetWeightKg = target,
        targetDateEpochDay = today.plusDays(days).toEpochDay(), activity = ActivityLevel.LIGHT,
    )

    @Test fun optimalPlan() {
        val plan = WeightLossPlanner.plan(profile(90.0, 84.0, 84), today) // 6 kg in 12 weeks = 0.5 kg/wk
        assertEquals(6.0, plan.kgToLose, 0.001)
        assertEquals(84, plan.daysLeft)
        assertEquals(0.5, plan.weeklyLossKg, 0.001)
        assertEquals(PlanSafety.OPTIMAL, plan.safety)
        // 6*7700/84 = 550 kcal without adaptation; slightly more because TDEE falls with weight
        assertTrue("deficit ${plan.requiredDailyDeficit}", plan.requiredDailyDeficit in 551..620)
        assertEquals((plan.tdee - plan.requiredDailyDeficit).toDouble(), plan.recommendedIntake.toDouble(), 1.5)
        assertEquals(0, plan.extraStepsNeeded)
        assertTrue(plan.projection.isNotEmpty())
        // with adaptation the simulated weight at the target date is close to (slightly above) the goal
        assertEquals(84.0, plan.expectedWeightAtTarget, 0.15)
    }

    @Test fun tooFastPlanIsCappedAndSuggestsSaferDate() {
        val plan = WeightLossPlanner.plan(profile(90.0, 75.0, 30), today) // 15 kg in a month
        assertEquals(PlanSafety.TOO_FAST, plan.safety)
        assertTrue(plan.recommendedIntake >= Nutrition.minimumCalories(Sex.MALE))
        assertTrue(plan.extraStepsNeeded > 0)
        assertTrue(plan.safeTargetDate.isAfter(today.plusDays(30)))
        assertTrue(plan.expectedWeightAtTarget > 75.0)
        assertTrue(plan.tips.any { it.contains("Bezpieczna data") })
    }

    @Test fun gentlePlan() {
        val plan = WeightLossPlanner.plan(profile(80.0, 78.0, 180), today)
        assertEquals(PlanSafety.GENTLE, plan.safety)
    }

    @Test fun maintenanceWhenAtGoal() {
        val plan = WeightLossPlanner.plan(profile(70.0, 72.0, 60), today)
        assertEquals(PlanSafety.MAINTAIN, plan.safety)
        assertEquals(plan.tdee, plan.recommendedIntake)
    }

    @Test fun milestonesAreChronological() {
        val plan = WeightLossPlanner.plan(profile(100.0, 88.0, 200), today)
        val dates = plan.milestones.map { it.date }
        assertEquals(dates.sorted(), dates)
        assertTrue(plan.milestones.any { it.label.startsWith("Cel") })
    }

    @Test fun formatting() {
        assertEquals("1 marca 2026", WeightLossPlanner.formatDate(LocalDate.of(2026, 3, 1)))
        assertEquals("12 345", WeightLossPlanner.formatThousands(12345))
    }
}

class LocalFoodParserTest {
    private fun parse(t: String) = LocalFoodParser.parse(t)

    @Test fun countsAndUnits() {
        val r = parse("2 jajka, kromka chleba z masłem i kawa z mlekiem")
        val names = r.items.map { it.name }
        assertTrue(names.toString(), names.contains("Jajko kurze"))
        assertTrue(names.toString(), names.contains("Chleb pszenny"))
        assertTrue(names.toString(), names.contains("Masło"))
        assertTrue(names.toString(), names.contains("Kawa czarna"))
        assertTrue(names.toString(), names.contains("Mleko 2%"))
        val eggs = r.items.first { it.name == "Jajko kurze" }
        assertEquals(110.0, eggs.grams, 0.01)
        assertEquals(157.3, eggs.kcal, 0.1)
    }

    @Test fun gramsGluedToNumber() {
        val r = parse("150g ryżu + 200 g kurczaka")
        assertEquals(2, r.items.size)
        assertEquals("Ryż biały gotowany", r.items[0].name)
        assertEquals(150.0, r.items[0].grams, 0.01)
        assertEquals("Pierś z kurczaka", r.items[1].name)
        assertEquals(198.0, r.items[1].kcal, 0.1)
    }

    @Test fun wordNumbersAndHalf() {
        val r = parse("pół banana oraz dwie kromki chleba żytniego")
        val banana = r.items.first { it.name == "Banan" }
        assertEquals(60.0, banana.grams, 0.01)
        val bread = r.items.first { it.name.startsWith("Chleb") }
        assertEquals(80.0, bread.grams, 0.01) // 2 × 40 g rye slices
    }

    @Test fun spoonAndGlass() {
        val r = parse("łyżka oliwy, szklanka mleka")
        assertEquals(15.0, r.items.first { it.name == "Oliwa z oliwek" }.grams, 0.01)
        assertEquals(250.0, r.items.first { it.name == "Mleko 2%" }.grams, 0.01)
    }

    @Test fun unknownWordsReported() {
        val r = parse("qwertyuiop")
        assertTrue(r.items.isEmpty())
        assertEquals(listOf("qwertyuiop"), r.unknown)
    }

    @Test fun guessScaling() {
        val g = FoodGuess("x", 100.0, 200.0, 10.0, 20.0, 5.0).withGrams(150.0)
        assertEquals(300.0, g.kcal, 0.001)
        assertEquals(7.5, g.fat, 0.001)
    }

    @Test fun catalogSearch() {
        assertEquals("Banan", FoodCatalog.search("banan").first().name)
        assertTrue(FoodCatalog.search("kurczak").any { it.name == "Pierś z kurczaka" })
        assertTrue(FoodCatalog.foods.map { it.id }.toSet().size == FoodCatalog.foods.size)
        assertTrue(FoodCatalog.foods.size >= 150)
    }
}

class StepMathTest {
    private val empty = StepState(-1, -1, -1, -1)

    @Test fun firstReadingStartsAtZero() {
        val r = StepMath.update(empty, 50_000, 100)
        assertEquals(0, r.todaySteps)
    }

    @Test fun accumulatesSameDay() {
        var r = StepMath.update(empty, 50_000, 100)
        r = StepMath.update(r.state, 53_500, 100)
        assertEquals(3_500, r.todaySteps)
    }

    @Test fun newDayStartsFromLastReading() {
        var r = StepMath.update(empty, 50_000, 100)
        r = StepMath.update(r.state, 58_000, 100)
        r = StepMath.update(r.state, 58_400, 101)
        assertEquals(400, r.todaySteps)
    }

    @Test fun rebootSameDayKeepsSteps() {
        var r = StepMath.update(empty, 50_000, 100)
        r = StepMath.update(r.state, 54_000, 100) // 4000 today
        r = StepMath.update(r.state, 300, 100)    // reboot, counter restarted
        assertEquals(4_300, r.todaySteps)
        r = StepMath.update(r.state, 1_000, 100)
        assertEquals(5_000, r.todaySteps)
    }

    @Test fun rebootOvernight() {
        var r = StepMath.update(empty, 50_000, 100)
        r = StepMath.update(r.state, 51_000, 100)
        r = StepMath.update(r.state, 700, 101)
        assertEquals(700, r.todaySteps)
    }
}

class WorkoutGeneratorTest {
    @Test fun gymPlansForAllSplits() {
        for (days in 2..6) {
            val plan = WorkoutGenerator.generate(WorkoutPreferences(WorkoutLocation.GYM, WorkoutLevel.INTERMEDIATE, WorkoutGoal.MUSCLE, days, 60))
            assertEquals(days, plan.days.size)
            plan.days.forEach { d ->
                assertTrue("${d.name} has ${d.exercises.size}", d.exercises.size >= 4)
                assertEquals(d.exercises.size, d.exercises.map { it.name }.toSet().size)
            }
        }
    }

    @Test fun homeNoEquipmentUsesBodyweightOnly() {
        val plan = WorkoutGenerator.generate(WorkoutPreferences(WorkoutLocation.HOME, WorkoutLevel.BEGINNER, WorkoutGoal.FAT_LOSS, 3, 30, HomeEquipment.NONE))
        val names = plan.days.flatMap { it.exercises }.map { it.name }
        assertTrue(names.none { it.contains("sztang", true) || it.contains("maszyn", true) || it.contains("wyciąg", true) })
        assertTrue(plan.days.all { it.finisher.isNotBlank() })
    }

    @Test fun planJsonRoundTrip() {
        val plan = WorkoutGenerator.generate(WorkoutPreferences(daysPerWeek = 4))
        val back = PlanJson.fromJson(PlanJson.toJson(plan))
        assertNotNull(back)
        assertEquals(plan, back)
        val prefs = WorkoutPreferences(WorkoutLocation.HOME, WorkoutLevel.ADVANCED, WorkoutGoal.FITNESS, 5, 45, HomeEquipment.FULL, "kolano")
        assertEquals(prefs, PlanJson.prefsFromJson(PlanJson.prefsToJson(prefs)))
        assertEquals(WorkoutPreferences(), PlanJson.prefsFromJson("garbage"))
    }
}

class AchievementsTest {
    @Test fun streaks() {
        assertEquals(3, Achievements.streak(listOf(10, 9, 8, 6), 10))
        assertEquals(3, Achievements.streak(listOf(9, 8, 7), 10)) // today not logged yet
        assertEquals(0, Achievements.streak(listOf(5), 10))
        assertEquals(4, Achievements.bestStreak(listOf(1, 2, 3, 4, 8, 9)))
    }

    @Test fun unlocks() {
        val list = Achievements.compute(
            AchievementInput(listOf(1, 2, 3), 3, mapOf(1L to 12_000), 8000, mapOf(1L to 2500), 2500, 1, 90.0, 88.5, 0, 1),
        )
        val unlocked = list.filter { it.unlocked }.map { it.title }
        assertTrue(unlocked.containsAll(listOf("Pierwszy krok", "Rozgrzewka", "10 000!", "Nawodniony", "Pierwszy trening", "Minus 1 kg", "Oko AI")))
        assertTrue("Minus 5 kg" !in unlocked)
    }
}

class RecipeCatalogTest {
    @Test fun recipesAreConsistent() {
        assertTrue(RecipeCatalog.recipes.size >= 25)
        assertEquals(RecipeCatalog.recipes.size, RecipeCatalog.recipes.map { it.id }.toSet().size)
        RecipeCatalog.recipes.forEach { r ->
            val macroKcal = r.protein * 4 + r.carbs * 4 + r.fat * 9
            assertTrue("${r.name}: $macroKcal vs ${r.kcal}", kotlin.math.abs(macroKcal - r.kcal) <= r.kcal * 0.15 + 15)
            assertTrue(r.category in RecipeCatalog.categories)
            assertTrue(r.ingredients.isNotEmpty() && r.steps.isNotEmpty())
        }
    }
}
