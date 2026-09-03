package com.stanislo.aura.ai

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GeminiException(message: String, val recoverable: Boolean = true) : Exception(message)

/**
 * Cienki klient REST dla Gemini API (endpoint `generativelanguage.googleapis.com`),
 * ten sam, ktory obsluguje bezplatny klucz z Google AI Studio.
 */
class GeminiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        isLenient = true
    }

    suspend fun generateContent(
        apiKey: String,
        model: String,
        request: GenerateContentRequest,
    ): GenerateContentResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw GeminiException("Brak klucza API. Dodaj go w Ustawieniach.", recoverable = false)
        }

        val url = "$BASE_URL/models/$model:generateContent"
        val body = json.encodeToString(GenerateContentRequest.serializer(), request)
            .toRequestBody(JSON_MEDIA_TYPE)

        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val raw = try {
            http.newCall(httpRequest).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw mapError(response.code, text)
                text
            }
        } catch (e: GeminiException) {
            throw e
        } catch (e: IOException) {
            throw GeminiException("Brak polaczenia z internetem lub przekroczono czas oczekiwania.")
        }

        try {
            json.decodeFromString(GenerateContentResponse.serializer(), raw)
        } catch (e: Exception) {
            throw GeminiException("Nie udalo sie odczytac odpowiedzi modelu: ${e.message}")
        }
    }

    private fun mapError(code: Int, body: String): GeminiException {
        val apiMessage = runCatching {
            json.decodeFromString(ApiErrorEnvelope.serializer(), body).error?.message
        }.getOrNull()?.takeIf { it.isNotBlank() }

        val friendly = when (code) {
            400 -> "Zapytanie odrzucone (400). Najczesciej oznacza to niepoprawny klucz API."
            403 -> "Brak dostepu (403). Klucz API jest nieaktywny lub Gemini API nie jest wlaczone dla tego projektu."
            404 -> "Nie znaleziono modelu (404). Wybierz inny model w Ustawieniach."
            429 -> "Przekroczono bezplatny limit zapytan (429). Odczekaj chwile lub przelacz sie na model Flash-Lite."
            in 500..599 -> "Serwer Google ma chwilowy problem ($code). Sprobuj ponownie za moment."
            else -> "Blad API ($code)."
        }
        val recoverable = code != 400 && code != 403
        return GeminiException(
            if (apiMessage != null) "$friendly\n\nSzczegoly: $apiMessage" else friendly,
            recoverable,
        )
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
