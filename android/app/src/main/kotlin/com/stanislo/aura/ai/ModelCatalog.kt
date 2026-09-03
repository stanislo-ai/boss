package com.stanislo.aura.ai

/** Model gotowy do pokazania w Ustawieniach. */
data class ModelOption(
    val id: String,
    val label: String,
    val description: String,
    val recommended: Boolean = false,
)

/**
 * Wybor modelu. Lista pobierana jest na zywo z API, wiec aplikacja nadaza za
 * zmianami po stronie Google; statyczna lista sluzy tylko zanim pobranie sie uda.
 */
object ModelCatalog {

    const val PREFERRED_DEFAULT = "gemini-3.6-flash"

    /** Uzywana, dopoki nie uda sie pobrac listy z API. */
    val FALLBACK: List<ModelOption> = listOf(
        ModelOption("gemini-3.6-flash", "Gemini 3.6 Flash", "Zalecany - szybki i sprawny w zadaniach", recommended = true),
        ModelOption("gemini-3.8-flash", "Gemini 3.8 Flash", "Najnowszy z rodziny Flash"),
        ModelOption("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Najwyzsze limity w planie bezplatnym"),
        ModelOption("gemini-2.5-flash", "Gemini 2.5 Flash", "Starsza generacja"),
    )

    /**
     * Zamienia surowa liste z API na uporzadkowana propozycje dla uzytkownika:
     * bez wariantow eksperymentalnych i multimodalnych, od najnowszych.
     */
    fun fromRemote(models: List<RemoteModel>): List<ModelOption> = models
        .asSequence()
        .filter { it.supportsGenerateContent }
        .filter { isConversational(it.id) }
        .distinctBy { it.id }
        .sortedByDescending { score(it.id) }
        .map { model ->
            ModelOption(
                id = model.id,
                label = model.displayName.ifBlank { model.id },
                description = describe(model),
                recommended = model.id == PREFERRED_DEFAULT,
            )
        }
        .take(24)
        .toList()

    /** Najlepszy dostepny model, gdy zapisany przestal istniec. */
    fun bestAvailable(options: List<ModelOption>): String? =
        options.firstOrNull { it.id == PREFERRED_DEFAULT }?.id ?: options.firstOrNull()?.id

    private fun describe(model: RemoteModel): String {
        val limit = model.inputTokenLimit
        val context = when {
            limit >= 1_000_000 -> "${limit / 1_000_000} mln tokenow kontekstu"
            limit >= 1_000 -> "${limit / 1_000} tys. tokenow kontekstu"
            else -> null
        }
        val kind = when {
            model.id.contains("lite") -> "najwyzsze limity zapytan"
            model.id.contains("pro") -> "najdokladniejszy, niskie limity"
            else -> "dobry balans jakosci i szybkosci"
        }
        return listOfNotNull(kind, context).joinToString(" • ")
    }

    /** Odsiewa modele do obrazu, mowy, osadzen i warianty testowe. */
    private fun isConversational(id: String): Boolean {
        val excluded = listOf(
            "embedding", "aqa", "imagen", "image", "veo", "vision", "tts", "audio",
            "native-audio", "live", "robotics", "learnlm", "gemma", "computer-use",
        )
        if (excluded.any { id.contains(it) }) return false
        if (id.contains("exp") || id.contains("preview")) return false
        // Wersje przypiete do daty (np. -001, -09-2026) tylko zasmiecaja liste.
        if (Regex("-\\d{3,}$").containsMatchIn(id)) return false
        return id.startsWith("gemini-")
    }

    /** Wyzszy wynik = nowszy i lepiej pasujacy do asystenta glosowego. */
    private fun score(id: String): Double {
        val version = Regex("gemini-(\\d+)(?:\\.(\\d+))?")
            .find(id)
            ?.let { match ->
                val major = match.groupValues[1].toDoubleOrNull() ?: 0.0
                val minor = match.groupValues.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                major * 100 + minor
            } ?: 0.0

        val family = when {
            id.contains("flash-lite") -> 2.0
            id.contains("flash") -> 3.0
            id.contains("pro") -> 1.0
            else -> 0.0
        }
        val preferred = if (id == PREFERRED_DEFAULT) 1000.0 else 0.0
        return preferred + version * 10 + family
    }
}
