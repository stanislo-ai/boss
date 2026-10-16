package com.stopfat.app

import com.stopfat.app.ai.AiClient
import com.stopfat.app.ai.AiException
import com.stopfat.app.ai.AiService
import com.stopfat.app.data.OpenFoodFacts
import com.stopfat.app.data.UserProfile
import com.stopfat.app.data.WorkoutPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AiClientTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() = server.shutdown()

    private fun service(model: String = "claude-opus-5-5") =
        AiService(AiClient("sk-ant-test", model, server.url("/").toString()))

    private fun reply(text: String, stop: String = "end_turn") = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(
            JSONObject()
                .put("id", "msg_test")
                .put("type", "message")
                .put("role", "assistant")
                .put("model", "claude-opus-5-5")
                .put("content", org.json.JSONArray().put(JSONObject().put("type", "text").put("text", text)))
                .put("stop_reason", stop)
                .put("stop_sequence", JSONObject.NULL)
                .put("usage", JSONObject().put("input_tokens", 12).put("output_tokens", 34))
                .toString(),
        )

    @Test fun textAnalysisSendsStructuredRequestAndParsesItems() = runBlocking {
        server.enqueue(reply("""{"items":[{"name":"jajko","grams":110,"kcal":157,"protein":13.9,"carbs":0.8,"fat":10.9,"confidence":"wysoka"}],"notes":"ok"}"""))
        val r = service().analyzeText("2 jajka")
        assertEquals(1, r.items.size)
        assertEquals("Jajko", r.items[0].name)
        assertEquals(157.0, r.items[0].kcal, 0.01)
        assertEquals("ok", r.notes)

        val req = server.takeRequest()
        assertEquals("/v1/messages", req.path)
        assertEquals("sk-ant-test", req.getHeader("x-api-key"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("claude-opus-5-5", body.getString("model"))
        val oc = body.getJSONObject("output_config")
        assertEquals("low", oc.getString("effort"))
        assertEquals("json_schema", oc.getJSONObject("format").getString("type"))
        assertTrue(oc.getJSONObject("format").getJSONObject("schema").has("properties"))
        assertTrue(body.getString("system").contains("StopFat"))
        assertTrue(!body.has("thinking"))
    }

    @Test fun photoAnalysisSendsImageBlock() = runBlocking {
        server.enqueue(reply("```json\n{\"items\":[{\"name\":\"Pizza\",\"grams\":200,\"kcal\":530,\"protein\":22,\"carbs\":66,\"fat\":20,\"confidence\":\"średnia\"}],\"notes\":\"\"}\n```"))
        val r = service().analyzePhoto("aGVsbG8=", "duża porcja")
        assertEquals(530.0, r.items.single().kcal, 0.01)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        val content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        val image = content.getJSONObject(0)
        assertEquals("image", image.getString("type"))
        assertEquals("image/jpeg", image.getJSONObject("source").getString("media_type"))
        assertEquals("aGVsbG8=", image.getJSONObject("source").getString("data"))
        assertTrue(content.getJSONObject(1).getString("text").contains("duża porcja"))
        assertEquals("medium", body.getJSONObject("output_config").getString("effort"))
    }

    @Test fun badRequestRetriesWithoutStructuredOutput() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
            .setBody("""{"type":"error","error":{"type":"invalid_request_error","message":"output_config.format not supported"}}"""))
        server.enqueue(reply("""Oto wynik: {"items":[{"name":"banan","grams":120,"kcal":107,"protein":1.3,"carbs":27,"fat":0.4,"confidence":"wysoka"}],"notes":""}"""))
        val r = service().analyzeText("banan")
        assertEquals("Banan", r.items.single().name)
        server.takeRequest()
        val second = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(!second.getJSONObject("output_config").has("format"))
    }

    @Test fun unauthorizedGivesFriendlyMessage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json")
            .setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""))
        try {
            service().analyzeText("banan")
            fail("expected exception")
        } catch (e: AiException) {
            assertTrue(e.message!!, e.message!!.contains("klucz API"))
        }
    }

    @Test fun refusalIsReported() = runBlocking {
        server.enqueue(reply("", stop = "refusal"))
        try {
            service().coach(UserProfile(), "brak", emptyList(), "hej")
            fail("expected exception")
        } catch (e: AiException) {
            assertTrue(e.message!!.contains("odmówiło"))
        }
    }

    @Test fun coachSendsHistoryAndReturnsText() = runBlocking {
        server.enqueue(reply("**Świetnie!** Jedz więcej białka."))
        val answer = service("claude-sonnet-5-5").coach(
            UserProfile(name = "Ola"), "zjedzone 1200 kcal",
            listOf(com.stopfat.app.data.ChatLine(true, "Cześć"), com.stopfat.app.data.ChatLine(false, "Hej!")), "Co na kolację?",
        )
        assertEquals("**Świetnie!** Jedz więcej białka.", answer)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("claude-sonnet-5-5", body.getString("model"))
        val msgs = body.getJSONArray("messages")
        assertEquals(3, msgs.length())
        assertEquals("assistant", msgs.getJSONObject(1).getString("role"))
        assertTrue(body.getString("system").contains("Ola"))
    }

    @Test fun workoutPlanParsed() = runBlocking {
        server.enqueue(reply("""{"title":"Plan AI","summary":"3 dni","days":[{"name":"Dzień 1","focus":"nogi","exercises":[{"name":"Przysiad","sets":4,"reps":"8-10","restSec":90,"tip":"plecy proste"}],"finisher":""}],"tips":["śpij"]}"""))
        val plan = service().workoutPlan(UserProfile(), WorkoutPreferences())
        assertEquals("Plan AI", plan.title)
        assertEquals(4, plan.days.single().exercises.single().sets)
        assertEquals("AI Claude", plan.source)
    }

    @Test fun extractJsonHandlesProse() {
        assertEquals("{\"a\":1}", AiService.extractJson("Jasne! {\"a\":1} Smacznego"))
    }
}

class OpenFoodFactsTest {
    @Test fun parsesProduct() {
        val json = """{"status":1,"product":{"product_name":"Skyr naturalny","brands":"Piątnica, X","serving_quantity":150,
            "nutriments":{"energy-kcal_100g":64,"proteins_100g":12,"carbohydrates_100g":3.8,"fat_100g":0}}}"""
        val r = OpenFoodFacts.parse("5900820000000", json) as OpenFoodFacts.Lookup.Found
        assertEquals("Skyr naturalny", r.food.name)
        assertEquals(64.0, r.food.kcal, 0.01)
        assertEquals(150.0, r.food.unitGrams, 0.01)
        assertEquals("Piątnica", r.brand)
        assertEquals("5900820000000", r.food.barcode)
    }

    @Test fun kilojoulesFallback() {
        val json = """{"status":1,"product":{"product_name_pl":"Chleb","nutriments":{"energy_100g":1046}}}"""
        val r = OpenFoodFacts.parse("123456", json) as OpenFoodFacts.Lookup.Found
        assertEquals(250.0, r.food.kcal, 0.5)
    }

    @Test fun notFound() {
        assertEquals(OpenFoodFacts.Lookup.NotFound, OpenFoodFacts.parse("1", """{"status":0,"status_verbose":"product not found"}"""))
    }
}
