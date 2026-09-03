package com.stanislo.aura.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------- Zapytanie ----------

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val tools: List<Tool>? = null,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null,
    val safetySettings: List<SafetySetting>? = null,
)

@Serializable
data class Content(
    val role: String? = null,
    val parts: List<Part> = emptyList(),
)

@Serializable
data class Part(
    val text: String? = null,
    val functionCall: FunctionCall? = null,
    val functionResponse: FunctionResponse? = null,
    /** true dla fragmentow "rozumowania" modelu - nie pokazujemy ich uzytkownikowi. */
    val thought: Boolean? = null,
    /** Podpis rozumowania; musi wrocic do API w kolejnej turze, inaczej model traci kontekst. */
    val thoughtSignature: String? = null,
)

@Serializable
data class FunctionCall(
    val name: String = "",
    val args: JsonObject = JsonObject(emptyMap()),
    /** Modele Gemini 3 nadaja wywolaniom identyfikator; odpowiedz musi go zwrocic. */
    val id: String? = null,
)

@Serializable
data class FunctionResponse(
    val name: String,
    val response: JsonObject,
    val id: String? = null,
)

@Serializable
data class Tool(
    val functionDeclarations: List<FunctionDeclaration>,
)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: Schema? = null,
)

/** Podzbior OpenAPI 3.0 akceptowany przez Gemini Function Calling. */
@Serializable
data class Schema(
    val type: String,
    val description: String? = null,
    val properties: Map<String, Schema>? = null,
    val items: Schema? = null,
    val required: List<String>? = null,
    @SerialName("enum") val enumValues: List<String>? = null,
)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null,
    val thinkingConfig: ThinkingConfig? = null,
)

/**
 * Modele 2.5 przyjmuja liczbowy `thinkingBudget`, a rodzina Gemini 3
 * tekstowy `thinkingLevel` (minimal / low / medium / high).
 */
@Serializable
data class ThinkingConfig(
    val thinkingBudget: Int? = null,
    val thinkingLevel: String? = null,
)

@Serializable
data class SafetySetting(val category: String, val threshold: String)

// ---------- Odpowiedz ----------

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList(),
    val promptFeedback: PromptFeedback? = null,
    val usageMetadata: UsageMetadata? = null,
)

@Serializable
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
)

@Serializable
data class PromptFeedback(val blockReason: String? = null)

@Serializable
data class UsageMetadata(
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0,
    val totalTokenCount: Int = 0,
)

// ---------- Lista modeli (GET /v1beta/models) ----------

@Serializable
data class ModelListResponse(
    val models: List<RemoteModel> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class RemoteModel(
    /** Pelna nazwa zasobu, np. "models/gemini-3.6-flash". */
    val name: String = "",
    val displayName: String = "",
    val description: String = "",
    val supportedGenerationMethods: List<String> = emptyList(),
    val inputTokenLimit: Int = 0,
) {
    val id: String get() = name.removePrefix("models/")
    val supportsGenerateContent: Boolean get() = "generateContent" in supportedGenerationMethods
}

@Serializable
data class ApiErrorEnvelope(val error: ApiErrorBody? = null)

@Serializable
data class ApiErrorBody(
    val code: Int = 0,
    val message: String = "",
    val status: String = "",
)

// ---------- Pomocnicze konstruktory schematow ----------

object Sch {
    fun string(description: String, enumValues: List<String>? = null) =
        Schema(type = "STRING", description = description, enumValues = enumValues)

    fun integer(description: String) = Schema(type = "INTEGER", description = description)

    fun number(description: String) = Schema(type = "NUMBER", description = description)

    fun boolean(description: String) = Schema(type = "BOOLEAN", description = description)

    fun stringArray(description: String) =
        Schema(type = "ARRAY", description = description, items = Schema(type = "STRING"))

    fun obj(properties: Map<String, Schema>, required: List<String> = emptyList()) =
        Schema(type = "OBJECT", properties = properties, required = required.ifEmpty { null })
}
