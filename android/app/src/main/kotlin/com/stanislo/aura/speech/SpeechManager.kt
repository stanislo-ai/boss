package com.stanislo.aura.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Rozpoznawanie mowy oparte o systemowy [SpeechRecognizer].
 * Na Pixelu dziala rowniez offline, wiec nie wymaga zadnych dodatkowych kluczy.
 *
 * Wszystkie metody nalezy wolac z watku glownego.
 */
class SpeechManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    private val _results = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val results: SharedFlow<String> = _results.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(language: String, preferOffline: Boolean) {
        if (_isListening.value) return
        if (!isAvailable) {
            _errors.tryEmit("Na tym urzadzeniu nie ma modulu rozpoznawania mowy.")
            return
        }

        val engine = recognizer ?: createRecognizer().also { recognizer = it }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            // Krotsza cisza konczaca wypowiedz = zywsza rozmowa.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        }

        _partial.value = ""
        _isListening.value = true
        try {
            engine.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Nie udalo sie uruchomic nasluchu", e)
            _isListening.value = false
            _errors.tryEmit("Nie udalo sie uruchomic mikrofonu.")
        }
    }

    fun stop() {
        if (!_isListening.value) return
        runCatching { recognizer?.stopListening() }
        _isListening.value = false
        _amplitude.value = 0f
    }

    fun cancel() {
        runCatching { recognizer?.cancel() }
        _isListening.value = false
        _amplitude.value = 0f
        _partial.value = ""
    }

    fun destroy() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        _isListening.value = false
    }

    private fun createRecognizer(): SpeechRecognizer =
        SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _amplitude.value = 0f
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                    // rmsdB praktycznie mieszcza sie w -2..12 dB.
                    _amplitude.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    _amplitude.value = 0f
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    _amplitude.value = 0f
                    val partialText = _partial.value
                    // Jesli mamy juz sensowny tekst czesciowy, potraktuj go jako wynik.
                    if (error == SpeechRecognizer.ERROR_NO_MATCH && partialText.isNotBlank()) {
                        _results.tryEmit(partialText)
                        _partial.value = ""
                        return
                    }
                    messageFor(error)?.let { _errors.tryEmit(it) }
                    _partial.value = ""
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    _amplitude.value = 0f
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    _partial.value = ""
                    if (text.isNotEmpty()) _results.tryEmit(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { _partial.value = it }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

    private fun messageFor(error: Int): String? = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        -> null // cisza to nie blad - nie zasmiecamy rozmowy

        SpeechRecognizer.ERROR_AUDIO -> "Problem z mikrofonem."
        SpeechRecognizer.ERROR_CLIENT -> null
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Brak zgody na uzycie mikrofonu."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        -> "Rozpoznawanie mowy wymaga internetu albo pobrania pakietu jezykowego offline."

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Modul mowy jest zajety, sprobuj ponownie."
        SpeechRecognizer.ERROR_SERVER -> "Serwer rozpoznawania mowy zwrocil blad."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        -> "Wybrany jezyk nie jest zainstalowany. Pobierz go w ustawieniach Google."

        else -> "Rozpoznawanie mowy nie powiodlo sie."
    }

    private companion object {
        const val TAG = "SpeechManager"
    }
}
