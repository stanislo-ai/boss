package com.stanislo.aura.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "aura_settings")

/** Wszystkie ustawienia uzytkownika trzymane lokalnie na urzadzeniu. */
data class AuraSettings(
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val language: String = "pl-PL",
    val speakReplies: Boolean = true,
    val handsFree: Boolean = false,
    val autoSendVoice: Boolean = true,
    val preferOfflineSpeech: Boolean = false,
    val deepThinking: Boolean = false,
    val temperature: Float = 0.7f,
    val userName: String = "",
    val persona: String = "",
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"

        /** Modele dostepne w bezplatnym planie Gemini API. */
        val AVAILABLE_MODELS = listOf(
            "gemini-2.5-flash" to "Domyslny - najlepszy balans jakosci i limitow",
            "gemini-2.5-flash-lite" to "Najszybszy, najwyzsze limity zapytan",
            "gemini-2.0-flash" to "Starszy, bardzo stabilny",
        )

        val LANGUAGES = listOf(
            "pl-PL" to "Polski",
            "en-US" to "English (US)",
            "en-GB" to "English (UK)",
            "de-DE" to "Deutsch",
            "es-ES" to "Espanol",
            "uk-UA" to "Ukrainska",
        )
    }
}

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<AuraSettings> = store.data.map { p ->
        AuraSettings(
            apiKey = p[KEY_API] ?: "",
            model = p[KEY_MODEL] ?: AuraSettings.DEFAULT_MODEL,
            language = p[KEY_LANGUAGE] ?: "pl-PL",
            speakReplies = p[KEY_SPEAK] ?: true,
            handsFree = p[KEY_HANDS_FREE] ?: false,
            autoSendVoice = p[KEY_AUTO_SEND] ?: true,
            preferOfflineSpeech = p[KEY_OFFLINE] ?: false,
            deepThinking = p[KEY_DEEP_THINKING] ?: false,
            temperature = p[KEY_TEMPERATURE] ?: 0.7f,
            userName = p[KEY_USER_NAME] ?: "",
            persona = p[KEY_PERSONA] ?: "",
        )
    }

    suspend fun setApiKey(value: String) = put(KEY_API, value.trim())
    suspend fun setModel(value: String) = put(KEY_MODEL, value)
    suspend fun setLanguage(value: String) = put(KEY_LANGUAGE, value)
    suspend fun setSpeakReplies(value: Boolean) = put(KEY_SPEAK, value)
    suspend fun setHandsFree(value: Boolean) = put(KEY_HANDS_FREE, value)
    suspend fun setAutoSendVoice(value: Boolean) = put(KEY_AUTO_SEND, value)
    suspend fun setPreferOfflineSpeech(value: Boolean) = put(KEY_OFFLINE, value)
    suspend fun setDeepThinking(value: Boolean) = put(KEY_DEEP_THINKING, value)
    suspend fun setTemperature(value: Float) = put(KEY_TEMPERATURE, value)
    suspend fun setUserName(value: String) = put(KEY_USER_NAME, value)
    suspend fun setPersona(value: String) = put(KEY_PERSONA, value)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    private companion object {
        val KEY_API = stringPreferencesKey("api_key")
        val KEY_MODEL = stringPreferencesKey("model")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_SPEAK = booleanPreferencesKey("speak_replies")
        val KEY_HANDS_FREE = booleanPreferencesKey("hands_free")
        val KEY_AUTO_SEND = booleanPreferencesKey("auto_send_voice")
        val KEY_OFFLINE = booleanPreferencesKey("prefer_offline_speech")
        val KEY_DEEP_THINKING = booleanPreferencesKey("deep_thinking")
        val KEY_TEMPERATURE = floatPreferencesKey("temperature")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_PERSONA = stringPreferencesKey("persona")
    }
}
