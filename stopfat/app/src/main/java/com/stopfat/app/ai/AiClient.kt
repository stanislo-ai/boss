package com.stopfat.app.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import com.stopfat.app.data.ChatLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Thin wrapper around the official Anthropic Java SDK used by every AI feature in StopFat. */
class AiClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String? = null,
) {
    private val client: AnthropicClient by lazy {
        val builder = AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofSeconds(180))
            .maxRetries(2)
        if (!baseUrl.isNullOrBlank()) builder.baseUrl(baseUrl.trim().trimEnd('/'))
        builder.build()
    }

    enum class Effort { LOW, MEDIUM, HIGH }

    /** Single text request. When [jsonSchema] is given, the answer is constrained to that JSON schema. */
    suspend fun ask(
        system: String,
        prompt: String,
        effort: Effort = Effort.LOW,
        jsonSchema: Map<String, Any>? = null,
        imageJpegBase64: String? = null,
        history: List<ChatLine> = emptyList(),
        maxTokens: Long = 16000L,
    ): String = withContext(Dispatchers.IO) {
        try {
            send(system, prompt, effort, jsonSchema, imageJpegBase64, history, maxTokens)
        } catch (e: BadRequestException) {
            // Some models / accounts may not accept structured outputs or the fallback beta –
            // retry once with a plain request, the prompt itself still asks for JSON.
            if (jsonSchema != null || supportsFallbacks()) {
                try {
                    send(system, prompt, effort, null, imageJpegBase64, history, maxTokens, plain = true)
                } catch (e2: Exception) {
                    throw translate(e2)
                }
            } else {
                throw translate(e)
            }
        } catch (e: Exception) {
            throw translate(e)
        }
    }

    private fun send(
        system: String,
        prompt: String,
        effort: Effort,
        jsonSchema: Map<String, Any>?,
        imageJpegBase64: String?,
        history: List<ChatLine>,
        maxTokens: Long,
        plain: Boolean = false,
    ): String {
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(maxTokens)
            .system(system)

        for (line in history) {
            if (line.fromUser) builder.addUserMessage(line.text) else builder.addAssistantMessage(line.text)
        }

        if (imageJpegBase64 != null) {
            val image = ImageBlockParam.builder()
                .source(
                    Base64ImageSource.builder()
                        .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                        .data(imageJpegBase64)
                        .build(),
                )
                .build()
            builder.addUserMessageOfBlockParams(
                listOf(
                    ContentBlockParam.ofImage(image),
                    ContentBlockParam.ofText(TextBlockParam.builder().text(prompt).build()),
                ),
            )
        } else {
            builder.addUserMessage(prompt)
        }

        val output = OutputConfig.builder().effort(
            when (effort) {
                Effort.LOW -> OutputConfig.Effort.LOW
                Effort.MEDIUM -> OutputConfig.Effort.MEDIUM
                Effort.HIGH -> OutputConfig.Effort.HIGH
            },
        )
        if (jsonSchema != null && !plain) {
            val schema = JsonOutputFormat.Schema.builder()
            jsonSchema.forEach { (k, v) -> schema.putAdditionalProperty(k, JsonValue.from(v)) }
            output.format(JsonOutputFormat.builder().schema(schema.build()).build())
        }
        builder.outputConfig(output.build())

        if (!plain && supportsFallbacks()) {
            // Server-side refusal fallback: if a safety classifier declines, the API retries on a fallback model.
            builder.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }

        val message: Message = client.messages().create(builder.build())
        val stop = message.stopReason().map { it.toString() }.orElse("")
        val text = message.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }
            .joinToString("\n").trim()
        if (stop == "refusal") {
            throw AiException("AI odmówiło odpowiedzi na to zapytanie. Spróbuj sformułować je inaczej.")
        }
        if (text.isEmpty()) {
            throw AiException(if (stop == "max_tokens") "Odpowiedź AI była zbyt długa – spróbuj ponownie." else "AI zwróciło pustą odpowiedź.")
        }
        return text
    }

    private fun supportsFallbacks(): Boolean =
        baseUrl.isNullOrBlank() && (model == "claude-opus-5-5" || model == "claude-sonnet-5-5")

    private fun translate(e: Throwable): Exception = when (e) {
        is AiException -> e
        is UnauthorizedException -> AiException("Nieprawidłowy klucz API. Sprawdź go w Ustawieniach.", e)
        is PermissionDeniedException -> AiException("Brak uprawnień dla tego klucza API lub modelu.", e)
        is RateLimitException -> AiException("Przekroczono limit zapytań AI. Odczekaj chwilę i spróbuj ponownie.", e)
        is InternalServerException -> AiException("Serwer AI jest chwilowo przeciążony. Spróbuj za moment.", e)
        is BadRequestException -> AiException("Błędne zapytanie do AI: ${e.message?.take(160)}", e)
        is AnthropicServiceException -> AiException("Błąd usługi AI (${e.statusCode()}).", e)
        is AnthropicIoException -> AiException("Brak połączenia z internetem lub serwer AI nie odpowiada.", e)
        else -> AiException("Nie udało się połączyć z AI: ${e.message?.take(160) ?: e.javaClass.simpleName}", e)
    }
}
