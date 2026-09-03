package com.stanislo.aura

import com.stanislo.aura.ai.AuraTools
import com.stanislo.aura.ai.Content
import com.stanislo.aura.ai.FunctionResponse
import com.stanislo.aura.ai.GenerateContentRequest
import com.stanislo.aura.ai.GenerateContentResponse
import com.stanislo.aura.ai.GenerationConfig
import com.stanislo.aura.ai.Part
import com.stanislo.aura.ai.ThinkingConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprawdza, ze zapytania wysylane do Gemini maja dokladnie taki ksztalt,
 * jakiego oczekuje endpoint v1beta:generateContent, oraz ze potrafimy
 * odczytac odpowiedz zawierajaca wywolanie funkcji.
 */
class GeminiWireFormatTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        isLenient = true
    }

    @Test
    fun `zapytanie zawiera wymagane pola i pomija puste`() {
        val request = GenerateContentRequest(
            contents = listOf(Content(role = "user", parts = listOf(Part(text = "Jaka pogoda?")))),
            tools = listOf(AuraTools.asTool),
            systemInstruction = Content(role = "user", parts = listOf(Part(text = "Jestes Aura."))),
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                maxOutputTokens = 2048,
                thinkingConfig = ThinkingConfig(0),
            ),
        )

        val encoded = json.encodeToString(GenerateContentRequest.serializer(), request).let {
            json.parseToJsonElement(it).jsonObject
        }

        val firstPart = encoded["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject
        assertEquals("Jaka pogoda?", firstPart["text"]!!.jsonPrimitive.content)
        // Pola null nie moga trafic do JSON-a - API odrzuca np. "text": null.
        assertNull(firstPart["functionCall"])
        assertNull(firstPart["functionResponse"])

        assertEquals(0, encoded["generationConfig"]!!.jsonObject["thinkingConfig"]!!
            .jsonObject["thinkingBudget"]!!.jsonPrimitive.content.toInt())
        assertNotNull(encoded["systemInstruction"])
    }

    @Test
    fun `deklaracje narzedzi spelniaja wymagania Function Calling`() {
        val encoded = json.encodeToString(GenerateContentRequest.serializer(),
            GenerateContentRequest(contents = emptyList(), tools = listOf(AuraTools.asTool)))
        val declarations = json.parseToJsonElement(encoded).jsonObject["tools"]!!
            .jsonArray[0].jsonObject["functionDeclarations"]!!.jsonArray

        assertTrue("oczekiwano co najmniej 30 narzedzi", declarations.size >= 30)

        val allowedTypes = setOf("STRING", "NUMBER", "INTEGER", "BOOLEAN", "ARRAY", "OBJECT")
        val names = mutableSetOf<String>()

        declarations.forEach { element ->
            val declaration = element.jsonObject
            val name = declaration["name"]!!.jsonPrimitive.content
            assertTrue("nazwa \"$name\" jest niezgodna z wymogami API",
                name.matches(Regex("[a-zA-Z][a-zA-Z0-9_]{0,63}")))
            assertTrue("duplikat nazwy narzedzia: $name", names.add(name))
            assertTrue("brak opisu dla $name",
                declaration["description"]!!.jsonPrimitive.content.length > 10)

            val parameters = declaration["parameters"]?.jsonObject ?: return@forEach
            assertEquals("parametry $name musza byc typu OBJECT",
                "OBJECT", parameters["type"]!!.jsonPrimitive.content)
            val properties = parameters["properties"]!!.jsonObject
            assertTrue("$name ma pusta liste parametrow", properties.isNotEmpty())
            properties.forEach { (property, schema) ->
                val type = schema.jsonObject["type"]!!.jsonPrimitive.content
                assertTrue("nieznany typ $type w $name.$property", type in allowedTypes)
            }
            // Kazde wymagane pole musi istniec wsrod zadeklarowanych wlasciwosci.
            parameters["required"]?.jsonArray?.forEach { required ->
                assertTrue("$name wymaga nieistniejacego pola $required",
                    properties.containsKey(required.jsonPrimitive.content))
            }
        }
    }

    @Test
    fun `odpowiedz z wywolaniem funkcji jest poprawnie odczytywana`() {
        val raw = """
            {
              "candidates": [{
                "content": {
                  "role": "model",
                  "parts": [
                    {"text": "Dodaje wydarzenie."},
                    {"functionCall": {
                        "name": "create_calendar_event",
                        "args": {"title": "Dentysta", "start_time": "2026-09-04T15:30:00", "reminder_minutes": 30}
                    }}
                  ]
                },
                "finishReason": "STOP"
              }],
              "usageMetadata": {"promptTokenCount": 120, "totalTokenCount": 180},
              "modelVersion": "gemini-2.5-flash"
            }
        """.trimIndent()

        val response = json.decodeFromString(GenerateContentResponse.serializer(), raw)
        val parts = response.candidates.single().content!!.parts
        assertEquals("Dodaje wydarzenie.", parts[0].text)

        val call = parts[1].functionCall!!
        assertEquals("create_calendar_event", call.name)
        assertEquals("Dentysta", call.args["title"]!!.jsonPrimitive.content)
        assertEquals(30, call.args["reminder_minutes"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `odpowiedz narzedzia wraca do modelu w roli user`() {
        val turn = Content(
            role = "user",
            parts = listOf(
                Part(
                    functionResponse = FunctionResponse(
                        name = "create_calendar_event",
                        response = JsonObject(mapOf("status" to JsonPrimitive("ok"))),
                    ),
                ),
            ),
        )
        val encoded = json.parseToJsonElement(json.encodeToString(Content.serializer(), turn)).jsonObject

        assertEquals("user", encoded["role"]!!.jsonPrimitive.content)
        val functionResponse = encoded["parts"]!!.jsonArray[0].jsonObject["functionResponse"]!!.jsonObject
        assertEquals("create_calendar_event", functionResponse["name"]!!.jsonPrimitive.content)
        assertEquals("ok", functionResponse["response"]!!.jsonObject["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `nieznane pola odpowiedzi nie wysadzaja parsera`() {
        val raw = """
            {"candidates":[{"content":{"role":"model","parts":[
              {"thought":true,"thoughtSignature":"abc"},
              {"text":"Gotowe.","cosNowego":123}
            ]},"finishReason":"STOP","safetyRatings":[]}]}
        """.trimIndent()

        val response = json.decodeFromString(GenerateContentResponse.serializer(), raw)
        val parts = response.candidates.single().content!!.parts
        assertEquals(true, parts[0].thought)
        assertEquals("abc", parts[0].thoughtSignature)
        assertEquals("Gotowe.", parts[1].text)
    }
}
