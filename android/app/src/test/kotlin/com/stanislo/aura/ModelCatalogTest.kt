package com.stanislo.aura

import com.stanislo.aura.ai.ModelCatalog
import com.stanislo.aura.ai.RemoteModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lista modeli przychodzi z API i bywa dluga oraz pelna wariantow,
 * ktorych asystent glosowy nie uzyje. Te testy pilnuja doboru i kolejnosci.
 */
class ModelCatalogTest {

    private fun model(id: String, methods: List<String> = listOf("generateContent")) =
        RemoteModel(
            name = "models/$id",
            displayName = id,
            supportedGenerationMethods = methods,
            inputTokenLimit = 1_048_576,
        )

    @Test
    fun `odsiewa modele nieprzydatne w rozmowie`() {
        val options = ModelCatalog.fromRemote(
            listOf(
                model("gemini-3.6-flash"),
                model("gemini-embedding-001"),
                model("imagen-4.0-generate"),
                model("gemini-2.5-flash-preview-tts"),
                model("gemini-3.5-flash-preview-09-2026"),
                model("gemma-3-27b-it"),
                model("gemini-3.5-flash-exp"),
                model("text-bison-001", methods = listOf("generateText")),
            ),
        )

        assertEquals(listOf("gemini-3.6-flash"), options.map { it.id })
    }

    @Test
    fun `nowsze modele sa wyzej, a zalecany na samej gorze`() {
        val options = ModelCatalog.fromRemote(
            listOf(
                model("gemini-2.5-flash"),
                model("gemini-3.8-flash"),
                model("gemini-3.6-flash"),
                model("gemini-3.5-flash-lite"),
                model("gemini-3.5-pro"),
            ),
        )

        assertEquals("gemini-3.6-flash", options.first().id)
        assertTrue(options.first().recommended)

        val rest = options.drop(1).map { it.id }
        assertEquals("gemini-3.8-flash", rest.first())
        assertTrue(
            "nowsze wersje musza wyprzedzac starsze",
            rest.indexOf("gemini-3.5-pro") < rest.indexOf("gemini-2.5-flash"),
        )
        assertFalse(options.drop(1).any { it.recommended })
    }

    @Test
    fun `zastepczy model to zalecany, a gdy go brak - pierwszy dostepny`() {
        val withPreferred = ModelCatalog.fromRemote(
            listOf(model("gemini-3.8-flash"), model("gemini-3.6-flash")),
        )
        assertEquals("gemini-3.6-flash", ModelCatalog.bestAvailable(withPreferred))

        val withoutPreferred = ModelCatalog.fromRemote(
            listOf(model("gemini-2.5-flash"), model("gemini-3.8-flash")),
        )
        assertEquals("gemini-3.8-flash", ModelCatalog.bestAvailable(withoutPreferred))

        assertEquals(null, ModelCatalog.bestAvailable(emptyList()))
    }

    @Test
    fun `opis modelu mowi o limitach i kontekscie`() {
        val lite = ModelCatalog.fromRemote(listOf(model("gemini-3.5-flash-lite"))).single()
        assertTrue(lite.description.contains("limity"))
        assertTrue(lite.description.contains("mln tokenow"))
    }

    @Test
    fun `lista awaryjna zawiera zalecany model`() {
        assertTrue(ModelCatalog.FALLBACK.any { it.id == ModelCatalog.PREFERRED_DEFAULT })
    }
}
