package com.stopfat.app.domain

import com.stopfat.app.data.UserProfile
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class PlanSafety(val label: String, val description: String) {
    MAINTAIN("Utrzymanie", "Jesteś na wadze docelowej lub poniżej – plan skupia się na utrzymaniu."),
    GENTLE("Łagodne tempo", "Bardzo bezpieczne tempo, łatwe do utrzymania, minimalna utrata mięśni."),
    OPTIMAL("Optymalne tempo", "Zalecane tempo 0,5–1% masy ciała tygodniowo – najlepszy kompromis."),
    AMBITIOUS("Ambitne tempo", "Wymaga dyscypliny i dużej ilości białka oraz ruchu. Wykonalne krótkoterminowo."),
    TOO_FAST("Zbyt agresywne", "Ten termin wymagałby niebezpiecznie dużego deficytu. Proponujemy bezpieczniejszą datę lub więcej ruchu."),
}

data class ProjectionPoint(val day: Int, val date: LocalDate, val weightKg: Double)

data class Milestone(val date: LocalDate, val weightKg: Double, val label: String)

data class WeightLossPlan(
    val currentWeight: Double,
    val targetWeight: Double,
    val kgToLose: Double,
    val daysLeft: Int,
    val weeksLeft: Double,
    val bmr: Int,
    val tdee: Int,
    val requiredDailyDeficit: Int,
    val requiredIntake: Int,
    val recommendedIntake: Int,
    val recommendedDeficit: Int,
    val minimumIntake: Int,
    val weeklyLossKg: Double,
    val weeklyLossPercent: Double,
    val safety: PlanSafety,
    /** Extra kcal per day that must be burned by activity when the intake hits the safe minimum. */
    val extraBurnNeeded: Int,
    val extraStepsNeeded: Int,
    val safeTargetDate: LocalDate,
    val expectedWeightAtTarget: Double,
    val projection: List<ProjectionPoint>,
    val milestones: List<Milestone>,
    val tips: List<String>,
)

object WeightLossPlanner {

    /** Max sustainable loss per week as a fraction of body weight. */
    private const val SAFE_WEEKLY_FRACTION = 0.0075
    private const val MAX_WEEKLY_FRACTION = 0.01

    fun plan(p: UserProfile, today: LocalDate = LocalDate.now()): WeightLossPlan {
        val current = p.weightKg
        val target = p.targetWeightKg
        val kgToLose = current - target
        val daysLeft = max(1, ChronoUnit.DAYS.between(today, p.targetDate).toInt())
        val bmr = Nutrition.bmr(p.sex, current, p.heightCm, p.age)
        val tdee = Nutrition.tdee(p)
        val minIntake = Nutrition.minimumCalories(p.sex)

        if (kgToLose <= 0.05) {
            val maintain = tdee.roundToInt()
            return WeightLossPlan(
                currentWeight = current, targetWeight = target, kgToLose = 0.0, daysLeft = daysLeft,
                weeksLeft = daysLeft / 7.0, bmr = bmr.roundToInt(), tdee = maintain, requiredDailyDeficit = 0,
                requiredIntake = maintain, recommendedIntake = maintain, recommendedDeficit = 0, minimumIntake = minIntake,
                weeklyLossKg = 0.0, weeklyLossPercent = 0.0, safety = PlanSafety.MAINTAIN, extraBurnNeeded = 0,
                extraStepsNeeded = 0, safeTargetDate = today, expectedWeightAtTarget = current,
                projection = simulate(p, maintain, today, min(daysLeft, 180)),
                milestones = emptyList(),
                tips = listOf(
                    "Twoja waga jest już na poziomie celu – utrzymuj kaloryczność ok. $maintain kcal.",
                    "Skup się na sile i kondycji: 2–4 treningi w tygodniu.",
                ),
            )
        }

        // Exact intake that reaches the target on the target date, accounting for the TDEE drop as weight goes down.
        val requiredIntake = solveIntake(p, daysLeft)
        val requiredDeficit = tdee - requiredIntake
        val weeklyLoss = kgToLose / (daysLeft / 7.0)
        val weeklyPct = weeklyLoss / current * 100.0

        val maxDeficit = current * MAX_WEEKLY_FRACTION * KCAL_PER_KG_FAT / 7.0
        val safety = when {
            requiredIntake < minIntake && requiredDeficit > maxDeficit -> PlanSafety.TOO_FAST
            weeklyPct > 1.5 -> PlanSafety.TOO_FAST
            weeklyPct > 1.0 || requiredIntake < minIntake -> PlanSafety.AMBITIOUS
            weeklyPct >= 0.5 -> PlanSafety.OPTIMAL
            else -> PlanSafety.GENTLE
        }

        val cappedDeficit = min(requiredDeficit, maxDeficit)
        val recommendedIntake = max(minIntake.toDouble(), tdee - cappedDeficit).roundToInt()
        val recommendedDeficit = max(0, (tdee - recommendedIntake).roundToInt())

        // When the food side is capped, the remaining deficit must come from extra activity.
        val extraBurn = max(0.0, requiredDeficit - recommendedDeficit).roundToInt()
        val extraSteps = Nutrition.kcalToSteps(extraBurn.toDouble(), current)

        val safeDays = ceil(kgToLose / (current * SAFE_WEEKLY_FRACTION) * 7.0).toLong()
        val safeDate = today.plusDays(safeDays)

        val projectionDays = max(daysLeft, min(safeDays.toInt(), 730))
        val projection = simulate(p, recommendedIntake, today, projectionDays)
        val expectedAtTarget = projection.lastOrNull { it.day <= daysLeft }?.weightKg ?: current

        val milestones = buildMilestones(projection, target, today)

        val tips = buildTips(p, safety, recommendedIntake, extraSteps, kgToLose, weeklyLoss, safeDate)

        return WeightLossPlan(
            currentWeight = current,
            targetWeight = target,
            kgToLose = kgToLose,
            daysLeft = daysLeft,
            weeksLeft = daysLeft / 7.0,
            bmr = bmr.roundToInt(),
            tdee = tdee.roundToInt(),
            requiredDailyDeficit = requiredDeficit.roundToInt(),
            requiredIntake = requiredIntake.roundToInt(),
            recommendedIntake = recommendedIntake,
            recommendedDeficit = recommendedDeficit,
            minimumIntake = minIntake,
            weeklyLossKg = weeklyLoss,
            weeklyLossPercent = weeklyPct,
            safety = safety,
            extraBurnNeeded = extraBurn,
            extraStepsNeeded = extraSteps,
            safeTargetDate = safeDate,
            expectedWeightAtTarget = expectedAtTarget,
            projection = projection,
            milestones = milestones,
            tips = tips,
        )
    }

    /**
     * Day-by-day simulation with metabolic adaptation: TDEE is recomputed from the current
     * simulated weight, so the loss naturally slows down as the body gets lighter.
     */
    fun solveIntake(p: UserProfile, days: Int): Double {
        var lo = 0.0
        var hi = Nutrition.tdee(p) * 1.5
        repeat(50) {
            val mid = (lo + hi) / 2
            if (finalWeight(p, mid, days) > p.targetWeightKg) hi = mid else lo = mid
        }
        return (lo + hi) / 2
    }

    private fun finalWeight(p: UserProfile, intake: Double, days: Int): Double {
        var w = p.weightKg
        repeat(days) { w -= (Nutrition.tdee(p, w) - intake) / KCAL_PER_KG_FAT }
        return w
    }

    fun simulate(p: UserProfile, intake: Int, start: LocalDate, days: Int): List<ProjectionPoint> {
        val points = ArrayList<ProjectionPoint>()
        var w = p.weightKg
        val step = when {
            days > 365 -> 14
            days > 120 -> 7
            days > 45 -> 3
            else -> 1
        }
        points.add(ProjectionPoint(0, start, w))
        for (d in 1..days) {
            val tdee = Nutrition.tdee(p, w)
            val delta = (tdee - intake) / KCAL_PER_KG_FAT
            w -= delta
            if (w < p.targetWeightKg && p.weightKg > p.targetWeightKg) w = max(w, p.targetWeightKg - 0.0)
            if (d % step == 0 || d == days) points.add(ProjectionPoint(d, start.plusDays(d.toLong()), w))
        }
        return points
    }

    private fun buildMilestones(projection: List<ProjectionPoint>, target: Double, today: LocalDate): List<Milestone> {
        if (projection.isEmpty()) return emptyList()
        val start = projection.first().weightKg
        val result = ArrayList<Milestone>()
        val fractions = listOf(0.25 to "25% celu", 0.5 to "Połowa drogi", 0.75 to "75% celu", 1.0 to "Cel osiągnięty! 🎉")
        for ((frac, label) in fractions) {
            val goalWeight = start - (start - target) * frac
            val hit = projection.firstOrNull { it.weightKg <= goalWeight + 0.05 }
            if (hit != null) result.add(Milestone(hit.date, goalWeight, label))
        }
        // Monthly checkpoints
        var month = 1
        while (month <= 12) {
            val date = today.plusMonths(month.toLong())
            val pt = projection.lastOrNull { !it.date.isAfter(date) } ?: break
            if (pt.date.isBefore(date.minusDays(15))) break
            result.add(Milestone(date, pt.weightKg, "Po $month mies."))
            month++
        }
        return result.sortedBy { it.date }
    }

    private fun buildTips(
        p: UserProfile,
        safety: PlanSafety,
        intake: Int,
        extraSteps: Int,
        kgToLose: Double,
        weeklyLoss: Double,
        safeDate: LocalDate,
    ): List<String> {
        val tips = ArrayList<String>()
        val protein = (p.weightKg * 1.8).roundToInt()
        tips += "Jedz ok. $intake kcal dziennie i min. $protein g białka, żeby chronić mięśnie."
        when (safety) {
            PlanSafety.TOO_FAST -> {
                tips += "Wybrany termin wymaga utraty ${"%.2f".format(weeklyLoss)} kg/tydz. – to za szybko. Bezpieczna data: ${formatDate(safeDate)}."
                if (extraSteps > 0) tips += "Aby zdążyć, potrzebujesz dodatkowo ok. ${formatThousands(extraSteps)} kroków dziennie (lub równoważnego treningu)."
            }
            PlanSafety.AMBITIOUS -> {
                tips += "Tempo jest ambitne – zaplanuj 3–4 treningi siłowe w tygodniu i min. ${formatThousands(p.stepGoal.coerceAtLeast(9000))} kroków dziennie."
                if (extraSteps > 0) tips += "Dodatkowe ${formatThousands(extraSteps)} kroków dziennie pokryje brakujący deficyt."
            }
            PlanSafety.OPTIMAL -> tips += "Świetny plan! Tempo ${"%.2f".format(weeklyLoss)} kg/tydz. jest zgodne z zaleceniami dietetyków."
            PlanSafety.GENTLE -> tips += "Masz komfortowy zapas czasu – możesz pozwolić sobie na 1 luźniejszy posiłek w tygodniu."
            PlanSafety.MAINTAIN -> Unit
        }
        tips += "Waż się rano, na czczo, 2–3 razy w tygodniu i patrz na średnią – waga waha się o 0,5–1,5 kg dziennie."
        tips += "Pij min. ${p.waterGoalMl / 1000.0} l wody i śpij 7–9 h – niewyspanie zwiększa apetyt nawet o 300 kcal."
        if (kgToLose > 10) tips += "Przy dużym celu rób co 8–12 tygodni 1–2 tyg. przerwy na kaloryczności podtrzymującej (diet break)."
        return tips
    }

    fun formatDate(d: LocalDate): String {
        val months = listOf("stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września", "października", "listopada", "grudnia")
        return "${d.dayOfMonth} ${months[d.monthValue - 1]} ${d.year}"
    }

    fun formatThousands(n: Int): String {
        val s = abs(n).toString().reversed().chunked(3).joinToString(" ").reversed()
        return if (n < 0) "-$s" else s
    }
}
