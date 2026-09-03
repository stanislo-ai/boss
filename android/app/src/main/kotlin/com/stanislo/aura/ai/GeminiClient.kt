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

class GeminiException(
    message: String,
    val recoverable: Boolean = true,
    /** Kod HTTP, jesli blad przyszedl z serwera. 404 oznacza nieznany model. */
    val httpCode: Int = 0,
) : Exception(message)

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
        requireKey(apiKey)

        val body = json.encodeToString(GenerateContentRequest.serializer(), request)
            .toRequestBody(JSON_MEDIA_TYPE)
        val raw = call(
            Request.Builder()
                .url("$BASE_URL/models/$model:generateContent")
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build(),
        )

        try {
            json.decodeFromString(GenerateContentResponse.serializer(), raw)
        } catch (e: Exception) {
            throw GeminiException("Nie udalo sie odczytac odpowiedzi modelu: ${e.message}")
        }
    }

    /**
     * Pobiera liste modeli dostepnych dla tego klucza. Dzieki temu aplikacja nie
     * dezaktualizuje sie, gdy Google wycofa albo doda model.
     */
    suspend fun listModels(apiKey: String): List<RemoteModel> = withContext(Dispatchers.IO) {
        requireKey(apiKey)

        val collected = mutableListOf<RemoteModel>()
        var pageToken: String? = null
        var page = 0

        do {
            val url = buildString {
                append("$BASE_URL/models?pageSize=200")
                pageToken?.let { append("&pageToken=$it") }
            }
            val raw = call(
                Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", apiKey)
                    .get()
                    .build(),
            )
            val response = try {
                json.decodeFromString(ModelListResponse.serializer(), raw)
            } catch (e: Exception) {
                throw GeminiException("Nie udalo sie odczytac listy modeli: ${e.message}")
            }
            collected += response.models
            pageToken = response.nextPageToken?.takeIf { it.isNotBlank() }
        } while (pageToken != null && ++page < MAX_MODEL_PAGES)

        collected.filter { it.supportsGenerateContent }
    }

    // ---------- srodki pomocnicze ----------

    private fun requireKey(apiKey: String) {
        if (apiKey.isBlank()) {
            throw GeminiException("Brak klucza API. Dodaj go w Ustawieniach.", recoverable = false)
        }
    }

    private fun call(request: Request): String = try {
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw mapError(response.code, text)
            text
        }
    } catch (e: GeminiException) {
        throw e
    } catch (e: IOException) {
        throw GeminiException("Brak polaczenia z internetem lub przekroczono czas oczekiwania.")
    }

    private fun mapError(code: Int, body: String): GeminiException {
        val apiMessage = runCatching {
            json.decodeFromString(ApiErrorEnvelope.serializer(), body).error?.message
        }.getOrNull()?.takeIf { it.isNotBlank() }

        val friendly = when (code) {
            400 -> "Zapytanie odrzucone (400). Najczesciej oznacza to niepoprawny klucz API."
            403 -> "Brak dostepu (403). Klucz API jest nieaktywny lub Gemini API nie jest wlaczone dla tego projektu."
            404 -> "Wybrany model nie jest juz dostepny (404)."
            429 -> "Przekroczono bezplatny limit zapytan (429). Odczekaj chwile lub przelacz sie na model Flash-Lite."
            in 500..599 -> "Serwer Google ma chwilowy problem ($code). Sprobuj ponownie za moment."
            else -> "Blad API ($code)."
        }
        return GeminiException(
            message = if (apiMessage != null) "$friendly\n\nSzczegoly: $apiMessage" else friendly,
            recoverable = code != 400 && code != 403,
            httpCode = code,
        )
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val MAX_MODEL_PAGES = 10
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
