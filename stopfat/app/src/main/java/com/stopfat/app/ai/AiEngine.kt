package com.stopfat.app.ai

import com.stopfat.app.data.ChatLine

enum class Effort { LOW, MEDIUM, HIGH }

/** Common interface of the AI providers (Google Gemini, Anthropic Claude). */
interface AiEngine {
    /** Human readable provider name shown in the UI ("Gemini", "Claude"). */
    val providerName: String

    /**
     * Single request. When [jsonSchema] is given the answer must be a JSON object following it
     * (providers that cannot enforce a schema get it as an instruction and JSON mode).
     */
    suspend fun ask(
        system: String,
        prompt: String,
        effort: Effort = Effort.LOW,
        jsonSchema: Map<String, Any>? = null,
        imageJpegBase64: String? = null,
        history: List<ChatLine> = emptyList(),
        maxTokens: Long = 16000L,
    ): String
}
