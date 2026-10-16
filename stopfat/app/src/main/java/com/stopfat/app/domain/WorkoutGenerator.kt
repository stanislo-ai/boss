package com.stopfat.app.domain

import com.stopfat.app.data.HomeEquipment
import com.stopfat.app.data.PlannedExercise
import com.stopfat.app.data.WorkoutDay
import com.stopfat.app.data.WorkoutGoal
import com.stopfat.app.data.WorkoutLevel
import com.stopfat.app.data.WorkoutLocation
import com.stopfat.app.data.WorkoutPlan
import com.stopfat.app.data.WorkoutPreferences

/** Offline rule-based plan generator, used without an AI key or when the AI call fails. */
object WorkoutGenerator {

    enum class Muscle { LEGS, GLUTES, CHEST, BACK, SHOULDERS, ARMS, CORE, CARDIO }

    private enum class Eq { GYM, NONE, DUMBBELLS, BANDS, BAR }

    private data class Ex(val name: String, val muscle: Muscle, val eq: Set<Eq>, val compound: Boolean, val tip: String, val timed: Boolean = false)

    private val library = listOf(
        // Legs / glutes
        Ex("Przysiad ze sztangą", Muscle.LEGS, setOf(Eq.GYM), true, "Kolana w linii stóp, plecy proste, schodź do równoległej."),
        Ex("Wypychanie nogami na suwnicy", Muscle.LEGS, setOf(Eq.GYM), true, "Nie prostuj kolan do końca, lędźwia przyklejone do oparcia."),
        Ex("Rumuński martwy ciąg", Muscle.GLUTES, setOf(Eq.GYM, Eq.DUMBBELLS), true, "Biodra w tył, sztanga blisko nóg, lekko ugięte kolana."),
        Ex("Hip thrust", Muscle.GLUTES, setOf(Eq.GYM), true, "Broda przy klatce, pełny wyprost bioder i spięcie pośladków."),
        Ex("Wykroki z hantlami", Muscle.LEGS, setOf(Eq.GYM, Eq.DUMBBELLS), true, "Tułów pionowo, tylne kolano prawie dotyka podłogi."),
        Ex("Prostowanie nóg na maszynie", Muscle.LEGS, setOf(Eq.GYM), false, "Zatrzymaj ruch na 1 s w górze."),
        Ex("Uginanie nóg leżąc", Muscle.LEGS, setOf(Eq.GYM), false, "Kontroluj fazę opuszczania 2–3 s."),
        Ex("Przysiad goblet", Muscle.LEGS, setOf(Eq.DUMBBELLS, Eq.GYM), true, "Hantel przy klatce, łokcie między kolanami."),
        Ex("Przysiad z masą ciała", Muscle.LEGS, setOf(Eq.NONE), true, "Ręce przed sobą, ciężar na piętach."),
        Ex("Wykroki naprzemienne", Muscle.LEGS, setOf(Eq.NONE), true, "Krok długi, kolano przednie nad kostką."),
        Ex("Przysiad bułgarski", Muscle.LEGS, setOf(Eq.NONE, Eq.DUMBBELLS), true, "Tylna stopa na krześle/kanapie, ciężar na przedniej nodze."),
        Ex("Most biodrowy", Muscle.GLUTES, setOf(Eq.NONE, Eq.BANDS), false, "Mocno spinaj pośladki w górze przez 2 s."),
        Ex("Przysiad z gumą", Muscle.LEGS, setOf(Eq.BANDS), true, "Guma pod stopami, trzymaj na barkach."),
        Ex("Odwodzenie nóg z gumą (monster walk)", Muscle.GLUTES, setOf(Eq.BANDS), false, "Guma nad kolanami, małe kroki w bok w półprzysiadzie."),
        // Chest
        Ex("Wyciskanie sztangi na ławce płaskiej", Muscle.CHEST, setOf(Eq.GYM), true, "Łopatki ściągnięte, stopy stabilnie na podłodze."),
        Ex("Wyciskanie hantli na ławce skośnej", Muscle.CHEST, setOf(Eq.GYM, Eq.DUMBBELLS), true, "Ławka 30°, łokcie ok. 45° od tułowia."),
        Ex("Rozpiętki na bramie", Muscle.CHEST, setOf(Eq.GYM), false, "Lekko ugięte łokcie, ruch łukiem."),
        Ex("Pompki", Muscle.CHEST, setOf(Eq.NONE, Eq.BAR), true, "Ciało w linii prostej, klatka blisko podłogi. Łatwiej: z kolan."),
        Ex("Pompki z nogami na podwyższeniu", Muscle.CHEST, setOf(Eq.NONE), true, "Trudniejszy wariant – mocno spięty brzuch."),
        Ex("Wyciskanie hantli leżąc na podłodze", Muscle.CHEST, setOf(Eq.DUMBBELLS), true, "Łokcie dotykają podłogi, wyciskaj dynamicznie."),
        Ex("Wyciskanie gumy w przód", Muscle.CHEST, setOf(Eq.BANDS), true, "Guma zaczepiona za plecami, wypychaj przed siebie."),
        // Back
        Ex("Podciąganie / ściąganie drążka wyciągu", Muscle.BACK, setOf(Eq.GYM, Eq.BAR), true, "Ciągnij łokciami w dół, klatka do drążka."),
        Ex("Wiosłowanie sztangą w opadzie", Muscle.BACK, setOf(Eq.GYM), true, "Plecy proste, ciągnij do pępka."),
        Ex("Wiosłowanie siedząc na wyciągu", Muscle.BACK, setOf(Eq.GYM), true, "Ściągaj łopatki na końcu ruchu."),
        Ex("Wiosłowanie hantlem jednorącz", Muscle.BACK, setOf(Eq.DUMBBELLS, Eq.GYM), true, "Ręka i kolano na ławce/krześle, łokieć blisko ciała."),
        Ex("Martwy ciąg klasyczny", Muscle.BACK, setOf(Eq.GYM), true, "Sztanga blisko piszczeli, neutralny kręgosłup, pchaj podłogę."),
        Ex("Wiosłowanie australijskie pod stołem", Muscle.BACK, setOf(Eq.NONE), true, "Chwyć krawędź stabilnego stołu, ciało proste."),
        Ex("Superman", Muscle.BACK, setOf(Eq.NONE), false, "Unieś ręce i nogi, zatrzymaj na 2 s."),
        Ex("Wiosłowanie z gumą", Muscle.BACK, setOf(Eq.BANDS), true, "Guma zaczepiona o drzwi, ciągnij do żeber."),
        Ex("Podciąganie na drążku", Muscle.BACK, setOf(Eq.BAR), true, "Pełny zwis, broda nad drążek. Łatwiej: z gumą."),
        // Shoulders
        Ex("Wyciskanie hantli nad głowę", Muscle.SHOULDERS, setOf(Eq.GYM, Eq.DUMBBELLS), true, "Nie wyginaj lędźwi, brzuch spięty."),
        Ex("Unoszenie hantli bokiem", Muscle.SHOULDERS, setOf(Eq.GYM, Eq.DUMBBELLS), false, "Do wysokości barków, małe ciężary."),
        Ex("Face pull na wyciągu", Muscle.SHOULDERS, setOf(Eq.GYM), false, "Ciągnij linkę do twarzy, łokcie wysoko."),
        Ex("Pompki pike", Muscle.SHOULDERS, setOf(Eq.NONE), true, "Biodra wysoko, głowa zmierza między dłonie."),
        Ex("Unoszenie gumy bokiem", Muscle.SHOULDERS, setOf(Eq.BANDS), false, "Stań na gumie, unoś ramiona bokiem."),
        // Arms
        Ex("Uginanie ramion z hantlami", Muscle.ARMS, setOf(Eq.GYM, Eq.DUMBBELLS), false, "Łokcie przy tułowiu, bez bujania."),
        Ex("Prostowanie ramion na wyciągu", Muscle.ARMS, setOf(Eq.GYM), false, "Łokcie nieruchomo, pełny wyprost."),
        Ex("Pompki w podporze tyłem (dipy na krześle)", Muscle.ARMS, setOf(Eq.NONE, Eq.DUMBBELLS), false, "Plecy blisko krzesła, łokcie do tyłu."),
        Ex("Uginanie ramion z gumą", Muscle.ARMS, setOf(Eq.BANDS), false, "Stań na gumie, kontroluj ruch."),
        // Core
        Ex("Deska (plank)", Muscle.CORE, setOf(Eq.NONE, Eq.GYM, Eq.DUMBBELLS, Eq.BANDS, Eq.BAR), false, "Ciało w linii, napnij pośladki i brzuch.", timed = true),
        Ex("Dead bug", Muscle.CORE, setOf(Eq.NONE, Eq.GYM, Eq.DUMBBELLS, Eq.BANDS, Eq.BAR), false, "Lędźwia przyklejone do podłogi."),
        Ex("Unoszenie nóg w zwisie", Muscle.CORE, setOf(Eq.GYM, Eq.BAR), false, "Bez bujania, kontrolowany ruch."),
        Ex("Russian twist", Muscle.CORE, setOf(Eq.NONE, Eq.DUMBBELLS), false, "Rotuj tułów, nie tylko ręce."),
        Ex("Deska boczna", Muscle.CORE, setOf(Eq.NONE, Eq.GYM, Eq.DUMBBELLS, Eq.BANDS, Eq.BAR), false, "Biodra wysoko, ciało w linii.", timed = true),
        // Cardio
        Ex("Pajacyki", Muscle.CARDIO, setOf(Eq.NONE, Eq.DUMBBELLS, Eq.BANDS, Eq.BAR), false, "Rytmicznie, lądowanie na palcach.", timed = true),
        Ex("Mountain climbers", Muscle.CARDIO, setOf(Eq.NONE, Eq.DUMBBELLS, Eq.BANDS, Eq.BAR), false, "Biodra nisko, szybkie kolana do klatki.", timed = true),
        Ex("Burpees", Muscle.CARDIO, setOf(Eq.NONE, Eq.DUMBBELLS, Eq.BANDS, Eq.BAR), true, "Płynnie: przysiad, deska, pompka, wyskok.", timed = true),
        Ex("Wioślarz (ergometr)", Muscle.CARDIO, setOf(Eq.GYM), true, "Nogi → tułów → ręce.", timed = true),
        Ex("Kettlebell swing", Muscle.CARDIO, setOf(Eq.GYM), true, "Ruch z bioder, nie z ramion.", timed = true),
    )

    fun generate(prefs: WorkoutPreferences): WorkoutPlan {
        val eqAllowed: Set<Eq> = when (prefs.location) {
            WorkoutLocation.GYM -> setOf(Eq.GYM)
            WorkoutLocation.HOME -> when (prefs.equipment) {
                HomeEquipment.NONE -> setOf(Eq.NONE)
                HomeEquipment.DUMBBELLS -> setOf(Eq.NONE, Eq.DUMBBELLS)
                HomeEquipment.BANDS -> setOf(Eq.NONE, Eq.BANDS)
                HomeEquipment.FULL -> setOf(Eq.NONE, Eq.DUMBBELLS, Eq.BANDS, Eq.BAR)
            }
        }
        val pool = library.filter { ex -> ex.eq.any { it in eqAllowed } }
        val days = prefs.daysPerWeek.coerceIn(2, 6)
        val split: List<Pair<String, List<Muscle>>> = when (days) {
            2, 3 -> listOf(
                "Full Body A" to listOf(Muscle.LEGS, Muscle.CHEST, Muscle.BACK, Muscle.SHOULDERS, Muscle.CORE),
                "Full Body B" to listOf(Muscle.GLUTES, Muscle.BACK, Muscle.CHEST, Muscle.ARMS, Muscle.CORE),
                "Full Body C" to listOf(Muscle.LEGS, Muscle.SHOULDERS, Muscle.BACK, Muscle.CHEST, Muscle.CORE),
            ).take(days)
            4 -> listOf(
                "Góra A" to listOf(Muscle.CHEST, Muscle.BACK, Muscle.SHOULDERS, Muscle.ARMS, Muscle.CORE),
                "Dół A" to listOf(Muscle.LEGS, Muscle.GLUTES, Muscle.LEGS, Muscle.CORE),
                "Góra B" to listOf(Muscle.BACK, Muscle.CHEST, Muscle.SHOULDERS, Muscle.ARMS, Muscle.CORE),
                "Dół B" to listOf(Muscle.GLUTES, Muscle.LEGS, Muscle.LEGS, Muscle.CORE),
            )
            else -> {
                val base = listOf(
                    "Push (klatka, barki, triceps)" to listOf(Muscle.CHEST, Muscle.CHEST, Muscle.SHOULDERS, Muscle.SHOULDERS, Muscle.ARMS),
                    "Pull (plecy, biceps)" to listOf(Muscle.BACK, Muscle.BACK, Muscle.BACK, Muscle.ARMS, Muscle.CORE),
                    "Legs (nogi, pośladki)" to listOf(Muscle.LEGS, Muscle.GLUTES, Muscle.LEGS, Muscle.GLUTES, Muscle.CORE),
                )
                if (days == 5) base + listOf(
                    "Góra (całość)" to listOf(Muscle.CHEST, Muscle.BACK, Muscle.SHOULDERS, Muscle.ARMS),
                    "Dół + core" to listOf(Muscle.LEGS, Muscle.GLUTES, Muscle.CORE, Muscle.CORE),
                ) else base + base.map { (n, m) -> "$n II" to m }
            }
        }

        val (sets, reps, rest) = when (prefs.goal) {
            WorkoutGoal.MUSCLE -> Triple(if (prefs.level == WorkoutLevel.BEGINNER) 3 else 4, "8-12", 90)
            WorkoutGoal.FAT_LOSS -> Triple(3, "10-15", 60)
            WorkoutGoal.FITNESS -> Triple(3, "12-15", 45)
        }
        val maxExercises = when {
            prefs.minutes <= 30 -> 4
            prefs.minutes <= 45 -> 5
            prefs.minutes <= 60 -> 6
            else -> 7
        }

        val used = HashMap<String, Int>()
        val planDays = split.mapIndexed { dayIndex, (dayName, muscles) ->
            val exercises = ArrayList<PlannedExercise>()
            exercises += PlannedExercise("Rozgrzewka: 5 min marszu/truchtu + krążenia ramion i bioder", 1, "5-8 min", 0, "Podnieś tętno i rozgrzej stawy.")
            val picked = HashSet<String>()
            for ((slot, muscle) in muscles.withIndex()) {
                if (exercises.size - 1 >= maxExercises) break
                val candidates = pool.filter { it.muscle == muscle && it.name !in picked }
                    .sortedWith(compareBy<Ex>({ used[it.name] ?: 0 }, { if (slot == 0 && !it.compound) 1 else 0 }))
                val ex = candidates.getOrNull(dayIndex % candidates.size.coerceAtLeast(1)) ?: candidates.firstOrNull() ?: continue
                picked += ex.name
                used[ex.name] = (used[ex.name] ?: 0) + 1
                val exReps = when {
                    ex.timed -> if (prefs.level == WorkoutLevel.BEGINNER) "30 s" else "45 s"
                    ex.compound && prefs.goal == WorkoutGoal.MUSCLE -> "6-10"
                    else -> reps
                }
                val exSets = if (prefs.level == WorkoutLevel.ADVANCED && ex.compound) sets + 1 else sets
                exercises += PlannedExercise(ex.name, exSets, exReps, if (ex.compound) rest + 15 else rest, ex.tip)
            }
            val finisher = when (prefs.goal) {
                WorkoutGoal.FAT_LOSS -> if (prefs.location == WorkoutLocation.GYM) "Interwały 10 min: 30 s szybko / 60 s wolno (bieżnia, rower lub wioślarz)" else "Tabata 8 min: 20 s pajacyki / burpees / mountain climbers, 10 s przerwy"
                WorkoutGoal.FITNESS -> "15 min cardio w strefie 2 (możesz swobodnie rozmawiać)"
                WorkoutGoal.MUSCLE -> "5 min rozciągania mięśni trenowanych w tym dniu"
            }
            WorkoutDay("Dzień ${dayIndex + 1}: $dayName", muscles.distinct().joinToString(", ") { muscleLabel(it) }, exercises, finisher)
        }

        val tips = listOf(
            "Progresja: gdy zrobisz górny zakres powtórzeń we wszystkich seriach, zwiększ ciężar o 2,5–5%.",
            "Zostawiaj 1–3 powtórzenia w zapasie (RIR) – technika ważniejsza niż ciężar.",
            "Między treningami tej samej partii zachowaj min. 48 h przerwy.",
            "W dni bez treningu celuj w ${if (prefs.goal == WorkoutGoal.FAT_LOSS) "10 000" else "8 000"} kroków – to najprostszy sposób na większy deficyt.",
            "Śpij 7–9 h i jedz 1,6–2,2 g białka na kg masy ciała.",
        )
        return WorkoutPlan(
            title = "${prefs.goal.label} – ${prefs.location.label}, ${days}× w tygodniu",
            summary = "Plan dla poziomu: ${prefs.level.label.lowercase()}. Sesja ok. ${prefs.minutes} min. " +
                if (prefs.location == WorkoutLocation.HOME) "Sprzęt: ${prefs.equipment.label.lowercase()}." else "Pełne wyposażenie siłowni.",
            days = planDays,
            tips = tips,
            source = "Generator offline",
            location = prefs.location,
        )
    }

    fun muscleLabel(m: Muscle) = when (m) {
        Muscle.LEGS -> "nogi"
        Muscle.GLUTES -> "pośladki"
        Muscle.CHEST -> "klatka"
        Muscle.BACK -> "plecy"
        Muscle.SHOULDERS -> "barki"
        Muscle.ARMS -> "ramiona"
        Muscle.CORE -> "brzuch"
        Muscle.CARDIO -> "kondycja"
    }

    /** MET value used to estimate burned calories of a session. */
    fun metFor(location: WorkoutLocation, goal: WorkoutGoal?): Double = when {
        goal == WorkoutGoal.FAT_LOSS -> 6.0
        location == WorkoutLocation.HOME -> 4.5
        else -> 5.0
    }
}
