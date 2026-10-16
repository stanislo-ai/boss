package com.stopfat.app

import android.app.Application
import android.content.Context
import com.stopfat.app.ai.AiClient
import com.stopfat.app.ai.AiEngine
import com.stopfat.app.ai.AiService
import com.stopfat.app.ai.GeminiClient
import com.stopfat.app.data.AiProvider
import com.stopfat.app.data.DEFAULT_GEMINI_MODEL
import com.stopfat.app.data.OpenFoodFacts
import com.stopfat.app.data.ProfileStore
import com.stopfat.app.data.StepCounter
import com.stopfat.app.data.db.AppDatabase
import com.stopfat.app.work.Notifications
import com.stopfat.app.work.WorkScheduler
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    val db: AppDatabase = AppDatabase.create(context)
    val store = ProfileStore(context)
    val stepCounter = StepCounter(context, db, store)
    val openFoodFacts = OpenFoodFacts()

    private var cachedKey: List<Any>? = null
    private var cachedService: AiService? = null

    /** Returns an AI service for the selected provider/key/model, or null when no API key is configured. */
    suspend fun ai(): AiService? {
        val p = store.current()
        if (!p.hasAi) return null
        val base = store.baseUrl.first().ifBlank { null }
        val key = listOf(p.aiProvider, p.activeKey, p.aiModel, p.geminiModel, base ?: "", p.insulinResistance)
        synchronized(this) {
            if (cachedKey != key || cachedService == null) {
                val engine: AiEngine = when (p.aiProvider) {
                    AiProvider.GEMINI -> GeminiClient(p.geminiKey, p.geminiModel, base)
                    AiProvider.CLAUDE -> AiClient(p.apiKey, p.aiModel, base)
                }
                cachedService = AiService(engine, p.insulinResistance)
                cachedKey = key
            }
            return cachedService
        }
    }

    /** Gemini model list for the given key (Settings → "Pobierz listę modeli"). */
    suspend fun geminiModels(key: String): List<String> {
        val base = store.baseUrl.first().ifBlank { null }
        return GeminiClient(key, DEFAULT_GEMINI_MODEL, base).listModels()
    }
}

class StopFatApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannel(this)
        runCatching { WorkScheduler.schedule(this) }
    }
}
