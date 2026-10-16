package com.stopfat.app.domain

import com.stopfat.app.data.Food
import com.stopfat.app.data.FoodCatalog
import com.stopfat.app.data.FoodGuess

/**
 * Offline parser for meal descriptions written in Polish, e.g.
 * "2 jajka, kromka chleba z masłem i kawa z mlekiem" or "150g ryżu + 200 g kurczaka".
 * Used when no AI key is configured (and as a fallback when the AI call fails).
 */
object LocalFoodParser {

    data class Result(val items: List<FoodGuess>, val unknown: List<String>)

    private val numberWords = mapOf(
        "pół" to 0.5, "pol" to 0.5, "połowa" to 0.5, "półtora" to 1.5, "półtorej" to 1.5,
        "jeden" to 1.0, "jedna" to 1.0, "jedno" to 1.0, "jedną" to 1.0,
        "dwa" to 2.0, "dwie" to 2.0, "dwóch" to 2.0, "trzy" to 3.0, "trzech" to 3.0,
        "cztery" to 4.0, "czterech" to 4.0, "pięć" to 5.0, "sześć" to 6.0, "siedem" to 7.0,
        "osiem" to 8.0, "dziewięć" to 9.0, "dziesięć" to 10.0, "kilka" to 3.0, "parę" to 2.0,
    )

    private data class Unit(val prefixes: List<String>, val grams: Double?, val isMass: Boolean = false)

    private val units = listOf(
        Unit(listOf("kg", "kilo"), 1000.0, isMass = true),
        Unit(listOf("dag", "deko"), 10.0, isMass = true),
        Unit(listOf("g", "gr", "gram"), 1.0, isMass = true),
        Unit(listOf("ml", "mililitr"), 1.0, isMass = true),
        Unit(listOf("l", "litr"), 1000.0, isMass = true),
        Unit(listOf("łyżeczk", "lyzeczk"), 5.0),
        Unit(listOf("łyżk", "lyzk", "łyżek"), 15.0),
        Unit(listOf("szklank"), 250.0),
        Unit(listOf("kubek", "kubk"), 250.0),
        Unit(listOf("garś", "gars"), 30.0),
        Unit(listOf("kromk", "kromek"), null),
        Unit(listOf("plaster", "plastr", "plasterk"), null),
        Unit(listOf("szt", "sztuk"), null),
        Unit(listOf("porcj", "talerz", "misk", "kawał"), null),
        Unit(listOf("puszk", "butelk", "kieliszek", "kieliszk", "opakowan"), null),
    )

    private val stopWords = setOf("z", "ze", "i", "oraz", "na", "do", "w", "bez", "a", "trochę", "troche", "mały", "mała", "małe", "duży", "duża", "duże", "około", "ok", "zjadłem", "zjadłam", "wypiłem", "wypiłam", "jadłem", "jadłam", "dzisiaj", "dziś", "rano", "potem", "też", "jeszcze")

    fun parse(text: String, extraFoods: List<Food> = emptyList()): Result {
        val segments = splitSegments(text)
        val items = ArrayList<FoodGuess>()
        val unknown = ArrayList<String>()
        for (seg in segments) {
            val guess = parseSegment(seg, extraFoods)
            if (guess != null) items += guess else if (seg.isNotBlank()) unknown += seg.trim()
        }
        return Result(items, unknown)
    }

    internal fun splitSegments(text: String): List<String> {
        val normalized = text.lowercase()
            .replace("\n", ",")
            .replace(";", ",")
            .replace("+", ",")
            .replace(Regex("\\s+(i|oraz|a także|plus)\\s+"), ",")
            // "kawa z mlekiem" -> "kawa", "mlekiem"; keeps "masło orzechowe" intact
            .replace(Regex("\\s+(z|ze)\\s+"), ",")
        return normalized.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun parseSegment(segment: String, extraFoods: List<Food>): FoodGuess? {
        var s = segment.trim().replace(Regex("(\\d),(\\d)"), "$1.$2")
        var quantity: Double? = null
        var unitGrams: Double? = null
        var unitIsMass = false
        var unitIsPiece = false

        // number with optional glued unit: "150g", "1.5 l", "2 szt"
        val numMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*([a-ząćęłńóśźż]*)").find(s)
        if (numMatch != null) {
            quantity = numMatch.groupValues[1].toDoubleOrNull()
            val unitWord = numMatch.groupValues[2]
            val unit = matchUnit(unitWord)
            if (unit != null) {
                unitGrams = unit.grams
                unitIsMass = unit.isMass
                unitIsPiece = unit.grams == null
                s = s.removeRange(numMatch.range).trim()
            } else {
                s = s.removeRange(numMatch.groups[1]!!.range).trim()
            }
        } else {
            val words = s.split(" ")
            val first = words.firstOrNull()
            val wordNum = first?.let { numberWords[it] }
            if (wordNum != null) {
                quantity = wordNum
                s = words.drop(1).joinToString(" ")
                // "dwie kromki chleba"
                val maybeUnit = s.split(" ").firstOrNull()?.let { matchUnit(it) }
                if (maybeUnit != null) {
                    unitGrams = maybeUnit.grams
                    unitIsMass = maybeUnit.isMass
                    unitIsPiece = maybeUnit.grams == null
                    s = s.split(" ").drop(1).joinToString(" ")
                }
            } else {
                // "kromka chleba", "szklanka mleka"
                val maybeUnit = first?.let { matchUnit(it) }
                if (maybeUnit != null && words.size > 1) {
                    quantity = 1.0
                    unitGrams = maybeUnit.grams
                    unitIsMass = maybeUnit.isMass
                    unitIsPiece = maybeUnit.grams == null
                    s = words.drop(1).joinToString(" ")
                }
            }
        }

        val food = findFood(s, extraFoods) ?: return null
        val grams = when {
            quantity == null -> food.unitGrams
            unitIsMass && unitGrams != null -> quantity * unitGrams
            unitGrams != null -> quantity * unitGrams
            unitIsPiece -> quantity * food.unitGrams
            // bare number: big number = grams, small number = pieces
            quantity >= 20 -> quantity
            else -> quantity * food.unitGrams
        }
        val n = food.scaled(grams)
        return FoodGuess(food.name, grams, n.kcal, n.protein, n.carbs, n.fat, confidence = "baza offline")
    }

    private fun matchUnit(word: String): Unit? {
        if (word.isBlank()) return null
        val w = word.lowercase().trim('.', ',')
        // exact short units first
        units.firstOrNull { u -> u.isMass && u.prefixes.any { it == w } }?.let { return it }
        return units.firstOrNull { u -> !u.isMass && u.prefixes.any { w.startsWith(it) } }
            ?: units.firstOrNull { u -> u.isMass && w.length > 2 && u.prefixes.any { it.length > 2 && w.startsWith(it) } }
    }

    internal fun findFood(phrase: String, extraFoods: List<Food>): Food? {
        val words = phrase.lowercase().split(" ")
            .map { it.trim('.', ',', '!', '?') }
            .filter { it.isNotBlank() && it !in stopWords }
        if (words.isEmpty()) return null
        val joined = words.joinToString(" ")
        val candidates = extraFoods + FoodCatalog.foods

        var best: Food? = null
        var bestScore = 0
        for (food in candidates) {
            val score = matchScore(food, joined, words)
            if (score > bestScore) {
                bestScore = score
                best = food
            }
        }
        return if (bestScore >= 100) best else null
    }

    /**
     * Score = 100 per query word matched by any word of the food's name/aliases (inflection tolerant),
     * +10 per exact word match, +50 when the whole phrase equals a name/alias.
     */
    private fun matchScore(food: Food, joined: String, words: List<String>): Int {
        val names = (food.aliases + food.name).map { it.lowercase() }
        val tokens = names.flatMap { it.split(" ") }.filter { it.length >= 3 }.toSet()
        var score = 0
        for (w in words.toSet()) {
            if (w.length < 3) continue
            when {
                w in tokens -> score += 110
                tokens.any { stemEq(w, it) } -> score += 100
            }
        }
        if (score == 0) return 0
        if (names.any { it == joined }) score += 50
        else if (names.any { it.contains(" ") && joined.contains(it) }) score += 30
        return score
    }

    /** Very small Polish "stemmer": compare words on a common prefix that ignores inflection endings. */
    private fun stemEq(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 3 || b.length < 3) return false
        val minLen = minOf(a.length, b.length)
        val prefix = when {
            minLen <= 4 -> minLen - 1
            minLen <= 6 -> minLen - 2
            else -> minLen - 3
        }.coerceAtLeast(3)
        return a.take(prefix) == b.take(prefix)
    }
}
