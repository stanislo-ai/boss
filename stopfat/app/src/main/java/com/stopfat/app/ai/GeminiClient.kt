package com.stopfat.app.ai

import com.stopfat.app.data.ChatLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Google Gemini provider (REST `generateContent`). Works with a free Google AI Studio key.
 * When the chosen model is not available for the key, it automatically falls back to other Flash models.
 */
class GeminiClient(
    private val apiKey: String,
    private val model: String,
    baseUrl: String? = null,
    private val http: OkHttpClient = defaultHttp,
) : AiEngine {

    override val providerName: String = "Gemini"

    private val base = (baseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE).trim().trimEnd('/')

    class HttpError(val code: Int, val status: String, override val message: String) : IOException(message)

    override suspend fun ask(
        system: String,
        prompt: String,
        effort: Effort,
        jsonSchema: Map<String, Any>?,
        imageJpegBase64: String?,
        history: List<ChatLine>,
        maxTokens: Long,
    ): String = withContext(Dispatchers.IO) {
        val body = buildBody(system, prompt, effort, jsonSchema, imageJpegBase64, history, maxTokens)
        val models = (listOf(model) + FALLBACK_MODELS).distinct()
        var lastError: Exception? = null
        for ((i, m) in models.withIndex()) {
            try {
                return@withContext callWithRetry(m, body)
            } catch (e: HttpError) {
                lastError = e
                // Model unknown / not enabled for this key → try the next Flash model.
                val modelProblem = e.code == 404 || (e.code == 400 && e.message.contains("model", ignoreCase = true) && !e.message.contains("API key", true))
                if (!modelProblem || i == models.lastIndex) throw translate(e)
            } catch (e: AiException) {
                throw e
            } catch (e: Exception) {
                throw translate(e)
            }
        }
        throw translate(lastError ?: AiException("Brak odpowiedzi od Gemini."))
    }

    private suspend fun callWithRetry(model: String, body: String): String {
        var attempt = 0
        while (true) {
            try {
                return call(model, body)
            } catch (e: HttpError) {
                val retryable = e.code == 500 || e.code == 503 || e.code == 504
                if (!retryable || attempt >= 2) throw e
            } catch (e: IOException) {
                if (e is HttpError || attempt >= 1) throw e
            }
            attempt++
            delay(1500L * attempt)
        }
    }

    private fun call(model: String, body: String): String {
        val request = Request.Builder()
            .url("$base/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val err = runCatching { JSONObject(text).getJSONObject("error") }.getOrNull()
                throw HttpError(resp.code, err?.optString("status").orEmpty(), err?.optString("message")?.ifBlank { null } ?: "HTTP ${resp.code}")
            }
            return parseResponse(text)
        }
    }

    companion object {
        const val DEFAULT_BASE = "https://generativelanguage.googleapis.com"
        val FALLBACK_MODELS = listOf("gemini-flash-latest", "gemini-3.8-flash", "gemini-3.5-flash-lite")
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private val defaultHttp by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(150, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }

        fun buildBody(
            system: String,
            prompt: String,
            effort: Effort,
            jsonSchema: Map<String, Any>?,
            imageJpegBase64: String?,
            history: List<ChatLine>,
            maxTokens: Long,
        ): String {
            val contents = JSONArray()
            for (line in history) {
                contents.put(
                    JSONObject()
                        .put("role", if (line.fromUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", line.text))),
                )
            }
            val parts = JSONArray()
            if (imageJpegBase64 != null) {
                parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", imageJpegBase64)))
            }
            val fullPrompt = if (jsonSchema != null) {
                "$prompt\n\nOdpowiedz wyłącznie poprawnym obiektem JSON zgodnym z tym schematem JSON Schema:\n${JSONObject(jsonSchema)}"
            } else prompt
            parts.put(JSONObject().put("text", fullPrompt))
            contents.put(JSONObject().put("role", "user").put("parts", parts))

            val config = JSONObject()
                .put("temperature", if (jsonSchema != null) 0.3 else 0.7)
                .put("maxOutputTokens", maxTokens.coerceIn(2048, 32768))
            if (jsonSchema != null) config.put("responseMimeType", "application/json")

            return JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", contents)
                .put("generationConfig", config)
                .toString()
        }

        fun parseResponse(text: String): String {
            val root = try { JSONObject(text) } catch (e: Exception) { throw AiException("Nieprawidłowa odpowiedź Gemini.", e) }
            root.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotBlank() }?.let {
                throw AiException("Gemini zablokowało to zapytanie ($it). Spróbuj sformułować je inaczej.")
            }
            val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
                ?: throw AiException("Gemini nie zwróciło odpowiedzi.")
            val finish = candidate.optString("finishReason")
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (p.optBoolean("thought", false)) continue
                sb.append(p.optString("text"))
            }
            val out = sb.toString().trim()
            if (out.isEmpty()) {
                throw AiException(
                    when (finish) {
                        "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII" -> "Gemini odmówiło odpowiedzi ze względów bezpieczeństwa."
                        "MAX_TOKENS" -> "Odpowiedź Gemini była zbyt długa – spróbuj ponownie."
                        else -> "Gemini zwróciło pustą odpowiedź."
                    },
                )
            }
            return out
        }

        fun translate(e: Throwable): Exception {
            if (e is AiException) return e
            if (e !is HttpError) android.util.Log.w("StopFat", "Gemini request failed", e)
            return when {
                e is HttpError && (e.code == 401 || e.code == 403 || e.message.contains("API key", true)) ->
                    AiException("Nieprawidłowy klucz Gemini. Utwórz darmowy klucz w Google AI Studio i wklej go w Ustawieniach.", e)
                e is HttpError && e.code == 429 ->
                    AiException("Wykorzystano darmowy limit zapytań Gemini. Odczekaj chwilę (limit odnawia się co minutę i co dobę) lub wybierz model Flash-Lite.", e)
                e is HttpError && e.code == 404 -> AiException("Wybrany model Gemini jest niedostępny dla tego klucza. Wybierz inny model w Ustawieniach.", e)
                e is HttpError && e.code >= 500 -> AiException("Serwery Gemini są chwilowo przeciążone. Spróbuj za moment.", e)
                e is HttpError -> AiException("Błąd Gemini (${e.code}): ${e.message.take(160)}", e)
                e is IOException -> AiException("Brak połączenia z internetem lub serwer AI nie odpowiada.", e)
                else -> AiException("Nie udało się połączyć z Gemini: ${e.message?.take(160) ?: e.javaClass.simpleName}", e)
            }
        }
    }

    /** Lists Gemini text models available for this key (for the model picker in Settings). */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$base/v1beta/models?pageSize=200").header("x-goog-api-key", apiKey).build()
        try {
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val err = runCatching { JSONObject(text).getJSONObject("error") }.getOrNull()
                    throw HttpError(resp.code, err?.optString("status").orEmpty(), err?.optString("message") ?: "HTTP ${resp.code}")
                }
                val arr = JSONObject(text).optJSONArray("models") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    val m = arr.getJSONObject(i)
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    val canGenerate = methods == null || (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                    val name = m.optString("name").removePrefix("models/")
                    name.takeIf {
                        canGenerate && it.startsWith("gemini") &&
                            listOf("tts", "image", "live", "embedding", "transcribe", "audio", "translate").none { bad -> it.contains(bad) }
                    }
                }.sorted()
            }
        } catch (e: Exception) {
            throw translate(e)
        }
    }
}
