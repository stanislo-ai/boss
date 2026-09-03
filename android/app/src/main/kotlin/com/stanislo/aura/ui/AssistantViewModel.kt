package com.stanislo.aura.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stanislo.aura.ai.AuraTools
import com.stanislo.aura.ai.Content
import com.stanislo.aura.ai.FunctionResponse
import com.stanislo.aura.ai.GeminiClient
import com.stanislo.aura.ai.GeminiException
import com.stanislo.aura.ai.GenerateContentRequest
import com.stanislo.aura.ai.GenerationConfig
import com.stanislo.aura.ai.Part
import com.stanislo.aura.ai.SystemPrompt
import com.stanislo.aura.ai.ThinkingConfig
import com.stanislo.aura.data.AuraSettings
import com.stanislo.aura.data.ChatMessage
import com.stanislo.aura.data.LocalStore
import com.stanislo.aura.data.SettingsRepository
import com.stanislo.aura.speech.SpeechManager
import com.stanislo.aura.speech.TtsManager
import com.stanislo.aura.tools.ToolRouter
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class AssistantPhase { IDLE, LISTENING, THINKING, SPEAKING }

data class AssistantUiState(
    val phase: AssistantPhase = AssistantPhase.IDLE,
    val partialTranscript: String = "",
    val amplitude: Float = 0f,
    val activeTool: String? = null,
    val banner: String? = null,
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val store = LocalStore.get(application)
    private val settingsRepository = SettingsRepository(application)
    private val router = ToolRouter(application, store)
    private val client = GeminiClient()
    private val tts = TtsManager(application)

    val speech = SpeechManager(application)

    val settings: StateFlow<AuraSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuraSettings())

    val messages: StateFlow<List<ChatMessage>> = store.history.items
    val notes = store.notes.items
    val memories = store.memories.items
    val reminders = store.reminders.items

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val _permissionRequests = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val permissionRequests = _permissionRequests.asSharedFlow()

    /** Pelna historia w formacie API - z wywolaniami narzedzi, ktorych nie pokazujemy w UI. */
    private val conversation = mutableListOf<Content>()
    private var turnJob: Job? = null
    private var historyRestored = false

    init {
        viewModelScope.launch {
            store.history.awaitReady()
            store.memories.awaitReady()
            restoreConversation()
        }
        viewModelScope.launch {
            speech.results.collect { text -> onSpeechResult(text) }
        }
        viewModelScope.launch {
            speech.errors.collect { message ->
                _uiState.value = _uiState.value.copy(phase = AssistantPhase.IDLE, banner = message)
            }
        }
        viewModelScope.launch {
            speech.amplitude.collect { value ->
                _uiState.value = _uiState.value.copy(amplitude = value)
            }
        }
        viewModelScope.launch {
            speech.partial.collect { value ->
                _uiState.value = _uiState.value.copy(partialTranscript = value)
            }
        }
    }

    // ---------- Sterowanie z UI ----------

    fun toggleListening() {
        if (_uiState.value.phase == AssistantPhase.LISTENING) {
            speech.stop()
            _uiState.value = _uiState.value.copy(phase = AssistantPhase.IDLE)
        } else {
            startListening()
        }
    }

    fun startListening() {
        tts.stop()
        turnJob?.cancel()
        val current = settings.value
        _uiState.value = _uiState.value.copy(
            phase = AssistantPhase.LISTENING,
            partialTranscript = "",
            banner = null,
        )
        speech.start(current.language, current.preferOfflineSpeech)
    }

    fun stopEverything() {
        turnJob?.cancel()
        speech.cancel()
        tts.stop()
        _uiState.value = _uiState.value.copy(
            phase = AssistantPhase.IDLE,
            activeTool = null,
            partialTranscript = "",
        )
    }

    fun dismissBanner() {
        _uiState.value = _uiState.value.copy(banner = null)
    }

    fun clearConversation() {
        viewModelScope.launch {
            store.clearHistory()
            conversation.clear()
        }
    }

    fun sendText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        speech.cancel()
        runTurn(trimmed)
    }

    private fun onSpeechResult(text: String) {
        if (settings.value.autoSendVoice) {
            runTurn(text)
        } else {
            _uiState.value = _uiState.value.copy(
                phase = AssistantPhase.IDLE,
                partialTranscript = text,
            )
        }
    }

    // ---------- Petla agenta ----------

    private fun runTurn(userText: String) {
        val current = settings.value
        if (!current.isConfigured) {
            _uiState.value = _uiState.value.copy(
                phase = AssistantPhase.IDLE,
                banner = "Dodaj klucz Gemini API w Ustawieniach, zeby zaczac rozmowe.",
            )
            return
        }

        turnJob?.cancel()
        turnJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                phase = AssistantPhase.THINKING,
                partialTranscript = "",
                banner = null,
            )

            store.appendMessage(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "user",
                    text = userText,
                    timestamp = System.currentTimeMillis(),
                ),
            )
            conversation += Content(role = "user", parts = listOf(Part(text = userText)))

            val usedTools = linkedSetOf<String>()
            var replyText = ""
            var failed = false

            try {
                var step = 0
                while (step++ < MAX_TOOL_STEPS) {
                    val response = client.generateContent(
                        apiKey = current.apiKey,
                        model = current.model,
                        request = buildRequest(current),
                    )

                    val candidate = response.candidates.firstOrNull()
                    val content = candidate?.content
                    if (content == null || content.parts.isEmpty()) {
                        replyText = response.promptFeedback?.blockReason
                            ?.let { "Model odmowil odpowiedzi (powod: $it)." }
                            ?: "Model nie zwrocil odpowiedzi. Sprobuj przeformulowac pytanie."
                        failed = true
                        break
                    }

                    conversation += content.copy(role = "model")

                    val visibleText = content.parts
                        .filter { it.thought != true }
                        .mapNotNull { it.text }
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                        .trim()
                    if (visibleText.isNotEmpty()) replyText = visibleText

                    val calls = content.parts.mapNotNull { it.functionCall }
                    if (calls.isEmpty()) break

                    val responseParts = calls.map { call ->
                        usedTools += router.displayName(call.name)
                        _uiState.value = _uiState.value.copy(activeTool = router.displayName(call.name))
                        val result = router.execute(call.name, call.args)
                        requestPermissionIfNeeded(result)
                        Part(functionResponse = FunctionResponse(name = call.name, response = result))
                    }
                    _uiState.value = _uiState.value.copy(activeTool = null)
                    conversation += Content(role = "user", parts = responseParts)
                }

                if (replyText.isBlank()) {
                    replyText = "Zadanie wykonane."
                }
            } catch (e: GeminiException) {
                replyText = e.message ?: "Nieznany blad polaczenia z Gemini."
                failed = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.value = _uiState.value.copy(phase = AssistantPhase.IDLE, activeTool = null)
                throw e
            } catch (e: Exception) {
                replyText = "Cos poszlo nie tak: ${e.message ?: e::class.simpleName}"
                failed = true
            }

            trimConversation()

            store.appendMessage(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    text = replyText,
                    timestamp = System.currentTimeMillis(),
                    tools = usedTools.toList(),
                    isError = failed,
                ),
            )
            _uiState.value = _uiState.value.copy(activeTool = null)

            if (current.speakReplies && !failed) {
                _uiState.value = _uiState.value.copy(phase = AssistantPhase.SPEAKING)
                tts.speak(replyText, current.language) {
                    viewModelScope.launch {
                        if (_uiState.value.phase == AssistantPhase.SPEAKING) {
                            _uiState.value = _uiState.value.copy(phase = AssistantPhase.IDLE)
                            if (settings.value.handsFree) startListening()
                        }
                    }
                }
            } else {
                _uiState.value = _uiState.value.copy(phase = AssistantPhase.IDLE)
            }
        }
    }

    private fun buildRequest(current: AuraSettings) = GenerateContentRequest(
        contents = conversation.toList(),
        tools = listOf(AuraTools.asTool),
        systemInstruction = SystemPrompt.build(current, memories.value),
        generationConfig = GenerationConfig(
            temperature = current.temperature,
            maxOutputTokens = 2048,
            thinkingConfig = if (current.model.startsWith("gemini-2.5")) {
                // 0 = brak "myslenia" (najszybsze odpowiedzi), -1 = model decyduje sam.
                ThinkingConfig(if (current.deepThinking) -1 else 0)
            } else {
                null
            },
        ),
    )

    private suspend fun requestPermissionIfNeeded(result: JsonObject) {
        val permission = (result["missing_permission"] as? JsonPrimitive)?.content ?: return
        _permissionRequests.emit(permission)
    }

    private fun trimConversation() {
        if (conversation.size <= MAX_CONVERSATION_ENTRIES) return
        // Nie zaczynaj historii od odpowiedzi narzedzia - API tego nie przyjmie.
        var dropped = conversation.size - MAX_CONVERSATION_ENTRIES
        while (dropped < conversation.size &&
            conversation[dropped].parts.any { it.functionResponse != null }
        ) {
            dropped++
        }
        repeat(dropped) { conversation.removeAt(0) }
    }

    private suspend fun restoreConversation() {
        if (historyRestored) return
        historyRestored = true
        store.history.items.value
            .filter { it.text.isNotBlank() && !it.isError }
            .takeLast(RESTORED_MESSAGES)
            .forEach { message ->
                conversation += Content(
                    role = if (message.role == "user") "user" else "model",
                    parts = listOf(Part(text = message.text)),
                )
            }
        // Historia musi zaczynac sie od uzytkownika.
        while (conversation.isNotEmpty() && conversation.first().role != "user") {
            conversation.removeAt(0)
        }
    }

    // ---------- Ustawienia ----------

    fun updateApiKey(value: String) = viewModelScope.launch { settingsRepository.setApiKey(value) }
    fun updateModel(value: String) = viewModelScope.launch { settingsRepository.setModel(value) }
    fun updateLanguage(value: String) = viewModelScope.launch { settingsRepository.setLanguage(value) }
    fun updateSpeakReplies(value: Boolean) = viewModelScope.launch {
        if (!value) tts.stop()
        settingsRepository.setSpeakReplies(value)
    }

    fun updateHandsFree(value: Boolean) = viewModelScope.launch { settingsRepository.setHandsFree(value) }
    fun updateAutoSend(value: Boolean) = viewModelScope.launch { settingsRepository.setAutoSendVoice(value) }
    fun updateOfflineSpeech(value: Boolean) = viewModelScope.launch { settingsRepository.setPreferOfflineSpeech(value) }
    fun updateDeepThinking(value: Boolean) = viewModelScope.launch { settingsRepository.setDeepThinking(value) }
    fun updateTemperature(value: Float) = viewModelScope.launch { settingsRepository.setTemperature(value) }
    fun updateUserName(value: String) = viewModelScope.launch { settingsRepository.setUserName(value) }
    fun updatePersona(value: String) = viewModelScope.launch { settingsRepository.setPersona(value) }

    fun deleteNote(id: String) = viewModelScope.launch {
        store.notes.update { list -> list.filterNot { it.id == id } }
    }

    fun deleteMemory(id: String) = viewModelScope.launch {
        store.memories.update { list -> list.filterNot { it.id == id } }
    }

    override fun onCleared() {
        super.onCleared()
        speech.destroy()
        tts.shutdown()
    }

    private companion object {
        const val MAX_TOOL_STEPS = 6
        const val MAX_CONVERSATION_ENTRIES = 40
        const val RESTORED_MESSAGES = 16
    }
}
