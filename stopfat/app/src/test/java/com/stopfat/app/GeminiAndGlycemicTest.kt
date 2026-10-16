package com.stopfat.app

import com.stopfat.app.ai.AiException
import com.stopfat.app.ai.AiService
import com.stopfat.app.ai.GeminiClient
import com.stopfat.app.data.ChatLine
import com.stopfat.app.data.DietType
import com.stopfat.app.data.FoodCatalog
import com.stopfat.app.data.RecipeCatalog
import com.stopfat.app.data.UserProfile
import com.stopfat.app.data.db.FoodEntry
import com.stopfat.app.domain.GiLevel
import com.stopfat.app.domain.Glycemic
import com.stopfat.app.domain.LocalFoodParser
import com.stopfat.app.domain.Nutrition
import com.stopfat.app.domain.WeightLossPlanner
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GeminiClientTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }

    @After fun tearDown() = server.shutdown()

    private fun service(model: String = "gemini-flash-latest", ir: Boolean = false) =
        AiService(GeminiClient("AIza-test", model, server.url("/").toString()), ir)

    private fun reply(text: String, finish: String = "STOP") = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(
            JSONObject().put(
                "candidates",
                JSONArray().put(
                    JSONObject()
                        .put("content", JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", "myślę…").put("thought", true)).put(JSONObject().put("text", text))))
                        .put("finishReason", finish),
                ),
            ).toString(),
        )

    private fun error(code: Int, status: String, message: String) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(JSONObject().put("error", JSONObject().put("code", code).put("status", status).put("message", message)).toString())

    @Test fun textAnalysisRequestAndParsing() = runBlocking {
        server.enqueue(reply("""{"items":[{"name":"owsianka","grams":250,"kcal":320,"protein":11,"carbs":52,"fat":7,"confidence":"wysoka","gi":55}],"notes":"ok"}"""))
        val r = service(ir = true).analyzeText("owsianka")
        assertEquals("Owsianka", r.items.single().name)
        assertEquals(55, r.items.single().gi)

        val req = server.takeRequest()
        assertEquals("/v1beta/models/gemini-flash-latest:generateContent", req.path)
        assertEquals("AIza-test", req.getHeader("x-goog-api-key"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("application/json", body.getJSONObject("generationConfig").getString("responseMimeType"))
        val system = body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(system.contains("insulinooporność"))
        val userText = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(userText.contains("\"gi\""))
    }

    @Test fun photoSendsInlineImageAndHistoryRoles() = runBlocking {
        server.enqueue(reply("""{"items":[],"notes":"to nie jedzenie"}"""))
        service().analyzePhoto("aGVsbG8=", "")
        val body = JSONObject(server.takeRequest().body.readUtf8())
        val parts = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals("image/jpeg", parts.getJSONObject(0).getJSONObject("inlineData").getString("mimeType"))
        assertEquals("aGVsbG8=", parts.getJSONObject(0).getJSONObject("inlineData").getString("data"))

        server.enqueue(reply("**Hej!**"))
        val answer = service().coach(UserProfile(), "brak", listOf(ChatLine(true, "a"), ChatLine(false, "b")), "c")
        assertEquals("**Hej!**", answer) // the "thought" part is skipped
        val contents = JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("contents")
        assertEquals(listOf("user", "model", "user"), (0 until contents.length()).map { contents.getJSONObject(it).getString("role") })
        assertTrue(!JSONObject(server.takeRequestOrNull()?.body?.readUtf8() ?: "{}").has("x"))
    }

    @Test fun unknownModelFallsBackToNextFlash() = runBlocking {
        server.enqueue(error(404, "NOT_FOUND", "models/gemini-9 is not found"))
        server.enqueue(reply("Działa"))
        val answer = service("gemini-9").coach(UserProfile(), "", emptyList(), "test")
        assertEquals("Działa", answer)
        assertEquals("/v1beta/models/gemini-9:generateContent", server.takeRequest().path)
        assertEquals("/v1beta/models/gemini-flash-latest:generateContent", server.takeRequest().path)
    }

    @Test fun invalidKeyAndQuotaMessages() = runBlocking {
        server.enqueue(error(400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key."))
        try { service().coach(UserProfile(), "", emptyList(), "x"); fail() } catch (e: AiException) { assertTrue(e.message!!.contains("klucz Gemini")) }
        server.enqueue(error(429, "RESOURCE_EXHAUSTED", "Quota exceeded"))
        try { service().coach(UserProfile(), "", emptyList(), "x"); fail() } catch (e: AiException) { assertTrue(e.message!!.contains("limit")) }
    }

    @Test fun blockedPromptReported() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        try { service().coach(UserProfile(), "", emptyList(), "x"); fail() } catch (e: AiException) { assertTrue(e.message!!.contains("zablokowało")) }
    }

    @Test fun listModelsFiltersTextModels() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"models":[{"name":"models/gemini-3.8-flash","supportedGenerationMethods":["generateContent"]},
                {"name":"models/gemini-3.8-flash-tts","supportedGenerationMethods":["generateContent"]},
                {"name":"models/text-embedding-005","supportedGenerationMethods":["embedContent"]},
                {"name":"models/gemini-3.5-flash-lite","supportedGenerationMethods":["generateContent","countTokens"]}]}""",
            ),
        )
        val models = GeminiClient("k", "gemini-flash-latest", server.url("/").toString()).listModels()
        assertEquals(listOf("gemini-3.5-flash-lite", "gemini-3.8-flash"), models)
    }
}

private fun MockWebServer.takeRequestOrNull() = takeRequest(50, java.util.concurrent.TimeUnit.MILLISECONDS)

class GlycemicTest {
    private fun entry(name: String, carbs: Double, gi: Int, meal: String = "LUNCH") =
        FoodEntry(epochDay = 1, meal = meal, name = name, grams = 100.0, kcal = 100.0, protein = 0.0, carbs = carbs, fat = 0.0, gi = gi)

    @Test fun levelsAndLoad() {
        assertEquals(GiLevel.NONE, Glycemic.level(0))
        assertEquals(GiLevel.LOW, Glycemic.level(55))
        assertEquals(GiLevel.MEDIUM, Glycemic.level(60))
        assertEquals(GiLevel.HIGH, Glycemic.level(78))
        assertEquals(GiLevel.UNKNOWN, Glycemic.level(-1))
        assertEquals(21.9, Glycemic.load(73, 30.0), 0.01) // white rice portion
        assertEquals("wysoki", Glycemic.loadLabel(21.9))
    }

    @Test fun catalogHasGiForEveryProduct() {
        val missing = FoodCatalog.foods.filter { it.gi == null }.map { it.id }
        assertTrue("missing GI: $missing", missing.isEmpty())
        assertEquals(51, FoodCatalog.byId("banana")!!.gi)
    }

    @Test fun dailyLoadAndHighGiItems() {
        val day = Glycemic.day(
            listOf(entry("Ryż", 40.0, 73), entry("Kurczak", 0.0, 0), entry("Jabłko", 25.0, 36, "SNACK"), entry("Sok z AI", 20.0, -1)),
            insulinResistance = true,
        )
        assertEquals(29.2 + 9.0, day.load, 0.01)
        assertEquals(80, day.target)
        assertEquals(listOf("Ryż"), day.highGiItems.map { it.name })
        assertEquals(20.0, day.unknownCarbs, 0.01)
        assertTrue(Glycemic.swapFor("Ryż biały gotowany")!!.contains("gryczana"))
    }

    @Test fun parserCarriesGi() {
        val r = LocalFoodParser.parse("150 g ryżu i jabłko")
        assertEquals(73, r.items.first { it.name.startsWith("Ryż") }.gi)
        assertEquals(36, r.items.first { it.name == "Jabłko" }.gi)
    }

    @Test fun searchAndParserIgnoreDiacritics() {
        assertEquals("Ryż biały gotowany", FoodCatalog.search("ryz").first().name)
        assertTrue(FoodCatalog.search("maslo").any { it.name == "Masło" })
        val r = LocalFoodParser.parse("kromka chleba z maslem, kawa z mlekiem")
        assertTrue(r.items.map { it.name }.toString(), r.items.any { it.name == "Masło" })
    }

    @Test fun lowGiDietAndRecipes() {
        val p = UserProfile(weightKg = 90.0, diet = DietType.LOW_GI, insulinResistance = true)
        val m = Nutrition.macroTargets(p, 2000)
        assertTrue("carbs ${m.carbsG}", m.carbsG in 120..190)
        assertTrue(RecipeCatalog.recipes.count { RecipeCatalog.isLowGi(it) } >= 15)
        assertTrue(WeightLossPlanner.plan(p).tips.any { it.contains("Insulinooporność") })
    }

    @Test fun legacyProfileStaysOnClaude() {
        // provider is decided in ProfileStore; here just check the active key logic
        val p = UserProfile(apiKey = "sk-ant", aiProvider = com.stopfat.app.data.AiProvider.CLAUDE)
        assertTrue(p.hasAi)
        assertTrue(!p.copy(aiProvider = com.stopfat.app.data.AiProvider.GEMINI).hasAi)
    }
}
