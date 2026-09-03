package com.stanislo.aura.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Czytanie odpowiedzi na glos przez systemowy silnik TTS. */
class TtsManager(context: Context) {

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var ready = false
    private var pending: Pair<String, String>? = null
    private var onUtteranceFinished: (() -> Unit)? = null

    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            pending?.let { (text, language) ->
                pending = null
                speak(text, language, onUtteranceFinished)
            }
        }
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                onUtteranceFinished?.invoke()
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                onUtteranceFinished?.invoke()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                onUtteranceFinished?.invoke()
            }
        })
    }

    fun speak(text: String, language: String, onFinished: (() -> Unit)? = null) {
        val clean = sanitize(text)
        if (clean.isBlank()) {
            onFinished?.invoke()
            return
        }
        onUtteranceFinished = onFinished
        if (!ready) {
            pending = clean to language
            return
        }
        runCatching {
            tts.language = toLocale(language)
            tts.setSpeechRate(1.02f)
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        }.onFailure {
            _isSpeaking.value = false
            onFinished?.invoke()
        }
    }

    fun stop() {
        runCatching { tts.stop() }
        _isSpeaking.value = false
    }

    fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }

    private fun toLocale(tag: String): Locale = runCatching {
        Locale.forLanguageTag(tag).takeIf { it.language.isNotEmpty() } ?: Locale.getDefault()
    }.getOrDefault(Locale.getDefault())

    /** Usuwa markdown i emoji, zeby syntezator nie czytal gwiazdek i myslnikow. */
    private fun sanitize(text: String): String = text
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("[*_#`>]"), "")
        .replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1")
        .replace(Regex("^\\s*[-•]\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private companion object {
        const val UTTERANCE_ID = "aura-reply"
    }
}
