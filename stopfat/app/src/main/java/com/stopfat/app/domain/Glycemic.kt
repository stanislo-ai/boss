package com.stopfat.app.domain

import com.stopfat.app.data.Food
import com.stopfat.app.data.db.FoodEntry
import kotlin.math.roundToInt

enum class GiLevel(val label: String, val short: String) {
    NONE("Bez węglowodanów", "0"),
    LOW("Niski IG", "niski"),
    MEDIUM("Średni IG", "średni"),
    HIGH("Wysoki IG", "wysoki"),
    UNKNOWN("IG nieznany", "?"),
}

data class GlycemicDay(
    val load: Double,
    val target: Int,
    val highGiCarbs: Double,
    val unknownCarbs: Double,
    val highGiItems: List<FoodEntry>,
    val mealLoads: Map<String, Double>,
)

/**
 * Glycemic index (IG) and glycemic load (ŁG) helpers for the insulin-resistance mode.
 * Values come from the International GI tables (Atkinson et al.) rounded for typical Polish products.
 */
object Glycemic {

    /** GI per built-in product id (0 = practically carbohydrate free). */
    val giById: Map<String, Int> = mapOf(
        // dairy & eggs
        "egg" to 0, "egg_white" to 0, "milk2" to 31, "milk05" to 32, "milk_oat" to 69, "yogurt_nat" to 35,
        "yogurt_greek" to 12, "skyr" to 30, "kefir" to 32, "cottage" to 30, "twarog_pt" to 30, "twarog_chudy" to 30,
        "cheese_gouda" to 0, "mozzarella" to 0, "feta" to 0, "parmesan" to 0, "butter" to 0, "cream18" to 30,
        "cream_cheese" to 30, "homogen" to 45, "protein_pudding" to 40,
        // meat & fish
        "chicken_breast" to 0, "chicken_grill" to 0, "chicken_thigh" to 0, "turkey_breast" to 0, "beef_mince" to 0,
        "beef_steak" to 0, "pork_loin" to 0, "pork_chop" to 55, "ham" to 0, "ham_pork" to 0, "sausage" to 28,
        "parowki" to 28, "bacon" to 0, "salmon" to 0, "tuna_can" to 0, "cod" to 0, "shrimp" to 0, "herring" to 0,
        // bread & grains
        "bread_wheat" to 75, "bread_rye" to 50, "roll" to 73, "roll_graham" to 55, "tortilla" to 30, "crispbread" to 70,
        "oats" to 55, "cornflakes" to 81, "granola" to 55, "rice" to 73, "rice_brown" to 50, "pasta" to 49,
        "pasta_ww" to 42, "buckwheat" to 54, "millet" to 71, "bulgur" to 46, "quinoa" to 53, "potato" to 78,
        "sweet_potato" to 82, "fries" to 63,
        // vegetables & legumes
        "tomato" to 15, "cucumber" to 15, "pickle" to 15, "pepper" to 15, "lettuce" to 15, "salad_mix" to 15,
        "carrot" to 35, "broccoli" to 15, "cauliflower" to 15, "spinach" to 15, "onion" to 15, "zucchini" to 15,
        "mushrooms" to 15, "corn" to 55, "beans" to 24, "chickpeas" to 28, "lentils" to 30, "peas" to 48,
        "avocado" to 15, "tofu" to 15,
        // fruit
        "apple" to 36, "banana" to 51, "orange" to 43, "mandarin" to 47, "strawberries" to 40, "blueberries" to 53,
        "raspberries" to 32, "grapes" to 59, "pear" to 38, "kiwi" to 50, "watermelon" to 76, "mango" to 51,
        "pineapple" to 59, "plum" to 39, "dates" to 62, "raisins" to 64,
        // fats & nuts
        "olive_oil" to 0, "peanut_butter" to 14, "almonds" to 15, "walnuts" to 15, "cashews" to 25, "peanuts" to 14,
        "chia" to 15, "mayo" to 0, "ketchup" to 55, "hummus_spread" to 15,
        // drinks
        "water" to 0, "coffee_black" to 0, "latte" to 30, "tea" to 0, "sugar" to 65, "honey" to 58,
        "juice_orange" to 50, "cola" to 63, "cola_zero" to 0, "beer" to 66, "wine" to 0, "vodka" to 0,
        "energy" to 70, "protein_shake" to 20, "smoothie" to 45,
        // sweets & snacks
        "chocolate_milk" to 43, "chocolate_dark" to 23, "chips" to 56, "cookies" to 70, "cake" to 55, "donut" to 76,
        "ice_cream" to 51, "bar_candy" to 55, "bar_protein" to 30, "gummies" to 78, "popcorn" to 65, "jam" to 51,
        "nutella" to 33,
        // dishes & fast food
        "pierogi_ruskie" to 60, "pierogi_meat" to 55, "rosol" to 50, "zurek" to 45, "pomidorowa" to 38, "bigos" to 30,
        "golabki" to 50, "nalesniki" to 60, "placki" to 75, "spaghetti_bol" to 50, "lasagne" to 47, "risotto" to 69,
        "curry" to 60, "sushi" to 52, "salad_caesar" to 30, "sandwich" to 70, "pizza" to 60, "pizza_pep" to 60,
        "burger" to 66, "kebab" to 60, "hotdog" to 68, "nuggets" to 46, "zapiekanka" to 70, "falafel" to 32,
    )

    /** Recommended daily glycemic load for people with insulin resistance (general population < 100). */
    const val DAILY_LOAD_TARGET_IR = 80
    const val DAILY_LOAD_TARGET = 100
    /** Carbohydrate limit per meal suggested in IR diets. */
    const val CARBS_PER_MEAL_IR = 45

    fun withGi(food: Food): Food = if (food.gi != null) food else food.copy(gi = giById[food.id])

    fun level(gi: Int?, carbsPer100: Double? = null): GiLevel = when {
        gi == null || gi < 0 -> GiLevel.UNKNOWN
        gi == 0 || (carbsPer100 != null && carbsPer100 < 3) -> GiLevel.NONE
        gi <= 55 -> GiLevel.LOW
        gi <= 69 -> GiLevel.MEDIUM
        else -> GiLevel.HIGH
    }

    /** Glycemic load = GI × digestible carbohydrates (g) / 100. ≤10 low, 11–19 medium, ≥20 high. */
    fun load(gi: Int, carbs: Double): Double = if (gi <= 0) 0.0 else gi * carbs / 100.0

    fun loadLabel(load: Double): String = when {
        load <= 10 -> "niski"
        load < 20 -> "średni"
        else -> "wysoki"
    }

    fun day(entries: List<FoodEntry>, insulinResistance: Boolean): GlycemicDay {
        var load = 0.0
        var high = 0.0
        var unknown = 0.0
        val highItems = ArrayList<FoodEntry>()
        val meals = HashMap<String, Double>()
        for (e in entries) {
            if (e.gi < 0) {
                if (e.carbs >= 5) unknown += e.carbs
                continue
            }
            val l = load(e.gi, e.carbs)
            load += l
            meals[e.meal] = (meals[e.meal] ?: 0.0) + l
            if (e.gi >= 70 && e.carbs >= 10) {
                high += e.carbs
                highItems += e
            }
        }
        return GlycemicDay(load, if (insulinResistance) DAILY_LOAD_TARGET_IR else DAILY_LOAD_TARGET, high, unknown, highItems, meals)
    }

    /** Practical swaps for high-GI products. */
    fun swapFor(name: String): String? {
        val n = name.lowercase()
        return when {
            "chleb" in n && "żyt" !in n || "bułk" in n || "kajzer" in n -> "chleb żytni razowy na zakwasie lub pieczywo pełnoziarniste"
            "ryż" in n && "brąz" !in n -> "ryż brązowy, basmati, kasza gryczana lub bulgur"
            "ziemniak" in n || "frytk" in n || "placki" in n -> "kasza gryczana, bulgur lub ziemniaki ugotowane i schłodzone (skrobia oporna)"
            "batat" in n -> "batat gotowany zamiast pieczonego (niższy IG) i w mniejszej porcji"
            "kasza jaglana" in n || "jaglan" in n -> "kasza gryczana, pęczak lub komosa ryżowa"
            "płatki kukurydz" in n || "corn flakes" in n -> "płatki owsiane górskie z orzechami i jogurtem"
            "arbuz" in n -> "jagody, maliny, truskawki lub jabłko"
            "cola" in n || "energet" in n || "sok" in n -> "woda z cytryną, napoje zero lub herbata"
            "żelk" in n || "pączek" in n || "ciast" in n -> "gorzka czekolada 70%+ lub jogurt grecki z owocami"
            "wafl" in n || "chrupk" in n -> "pieczywo żytnie razowe lub krakersy pełnoziarniste"
            "piwo" in n -> "wytrawne wino w małej ilości lub napoje bezalkoholowe zero"
            else -> null
        }
    }

    /** Educational rules shown on the insulin-resistance screen. */
    val rules: List<Pair<String, String>> = listOf(
        "🥗" to "Kolejność na talerzu: najpierw warzywa, potem białko i tłuszcz, węglowodany na końcu – to zmniejsza skok glukozy nawet o 30–40%.",
        "🍞" to "Wybieraj węglowodany o niskim IG (≤55): pieczywo żytnie razowe, kasza gryczana, pęczak, strączki, makaron al dente.",
        "🥚" to "Każdy posiłek z węglowodanami łącz z białkiem, tłuszczem i błonnikiem – nigdy „gołe” węglowodany.",
        "⏰" to "3–4 posiłki dziennie co 3–4 godziny, bez ciągłego podjadania. Ostatni posiłek 2–3 h przed snem.",
        "🚶" to "10–15 minut spaceru po posiłku obniża poposiłkowy poziom glukozy.",
        "🏋️" to "Trening siłowy 2–3× w tygodniu – mięśnie to główny „magazyn” glukozy i poprawiają wrażliwość na insulinę.",
        "🌾" to "Celuj w 25–35 g błonnika dziennie (warzywa, strączki, siemię lniane, nasiona chia).",
        "🥤" to "Unikaj słodzonych napojów i soków – płynne cukry działają najszybciej.",
        "😴" to "Śpij 7–9 h – jedna nieprzespana noc pogarsza wrażliwość na insulinę nawet o 20–25%.",
        "🩺" to "Insulinooporność diagnozuje lekarz (glukoza + insulina na czczo, HOMA-IR, krzywa cukrowa). Aplikacja wspiera dietę, ale nie zastępuje leczenia.",
    )

    fun describe(gi: Int?, carbs: Double): String {
        val lvl = level(gi)
        if (lvl == GiLevel.UNKNOWN) return "IG nieznany"
        if (lvl == GiLevel.NONE) return "bez węglowodanów"
        return "IG $gi · ŁG ${load(gi!!, carbs).roundToInt()}"
    }
}
