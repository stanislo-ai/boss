package com.stopfat.app.domain

data class Achievement(val emoji: String, val title: String, val description: String, val unlocked: Boolean, val progress: Float)

data class AchievementInput(
    val loggedDays: List<Long>,
    val today: Long,
    val stepsPerDay: Map<Long, Int>,
    val stepGoal: Int,
    val waterPerDay: Map<Long, Int>,
    val waterGoal: Int,
    val workouts: Int,
    val startWeight: Double?,
    val currentWeight: Double?,
    val fastsCompleted: Int,
    val aiScans: Int,
)

object Achievements {

    /** Current streak of consecutive days with at least one logged meal (today or ending yesterday). */
    fun streak(loggedDays: List<Long>, today: Long): Int {
        val set = loggedDays.toHashSet()
        var day = if (today in set) today else today - 1
        var count = 0
        while (day in set) {
            count++
            day--
        }
        return count
    }

    fun bestStreak(loggedDays: List<Long>): Int {
        val sorted = loggedDays.distinct().sorted()
        var best = 0
        var cur = 0
        var prev = Long.MIN_VALUE
        for (d in sorted) {
            cur = if (d == prev + 1) cur + 1 else 1
            if (cur > best) best = cur
            prev = d
        }
        return best
    }

    fun compute(i: AchievementInput): List<Achievement> {
        val best = bestStreak(i.loggedDays)
        val stepGoalDays = i.stepsPerDay.values.count { it >= i.stepGoal }
        val maxSteps = i.stepsPerDay.values.maxOrNull() ?: 0
        val waterDays = i.waterPerDay.values.count { it >= i.waterGoal }
        val lost = if (i.startWeight != null && i.currentWeight != null) i.startWeight - i.currentWeight else 0.0

        fun a(emoji: String, title: String, desc: String, value: Double, target: Double) =
            Achievement(emoji, title, desc, value >= target, (value / target).coerceIn(0.0, 1.0).toFloat())

        return listOf(
            a("🍽️", "Pierwszy krok", "Zapisz pierwszy posiłek", i.loggedDays.size.toDouble(), 1.0),
            a("🔥", "Rozgrzewka", "3 dni z rzędu z dziennikiem", best.toDouble(), 3.0),
            a("📅", "Tydzień mocy", "7 dni z rzędu z dziennikiem", best.toDouble(), 7.0),
            a("🏆", "Nawyk na lata", "30 dni z rzędu z dziennikiem", best.toDouble(), 30.0),
            a("👟", "Spacerowicz", "Osiągnij cel kroków", stepGoalDays.toDouble(), 1.0),
            a("🚶", "Maszyna do chodzenia", "Cel kroków przez 7 dni", stepGoalDays.toDouble(), 7.0),
            a("⚡", "10 000!", "Zrób 10 000 kroków w jeden dzień", maxSteps.toDouble(), 10_000.0),
            a("🏃", "Maratończyk", "Zrób 20 000 kroków w jeden dzień", maxSteps.toDouble(), 20_000.0),
            a("💧", "Nawodniony", "Wypij dzienny cel wody", waterDays.toDouble(), 1.0),
            a("🌊", "Wodnik", "Cel wody przez 7 dni", waterDays.toDouble(), 7.0),
            a("💪", "Pierwszy trening", "Ukończ trening z planu", i.workouts.toDouble(), 1.0),
            a("🏋️", "Regularność", "Ukończ 10 treningów", i.workouts.toDouble(), 10.0),
            a("🥇", "Żelazna wola", "Ukończ 50 treningów", i.workouts.toDouble(), 50.0),
            a("⏳", "Post", "Ukończ pierwszy post przerywany", i.fastsCompleted.toDouble(), 1.0),
            a("📸", "Oko AI", "Przeanalizuj posiłek ze zdjęcia", i.aiScans.toDouble(), 1.0),
            a("⬇️", "Minus 1 kg", "Schudnij pierwszy kilogram", lost, 1.0),
            a("🎯", "Minus 5 kg", "Schudnij 5 kg", lost, 5.0),
            a("👑", "Minus 10 kg", "Schudnij 10 kg", lost, 10.0),
        )
    }
}
