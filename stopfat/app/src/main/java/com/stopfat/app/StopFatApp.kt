package com.stopfat.app

import android.app.Application
import android.content.Context
import com.stopfat.app.ai.AiClient
import com.stopfat.app.ai.AiService
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

    private var cachedAi: Triple<String, String, String>? = null
    private var cachedService: AiService? = null

    /** Returns an AI service for the current key/model, or null when no API key is configured. */
    suspend fun ai(): AiService? {
        val p = store.current()
        if (!p.hasAi) return null
        val base = store.baseUrl.first()
        val key = Triple(p.apiKey, p.aiModel, base)
        synchronized(this) {
            if (cachedAi != key || cachedService == null) {
                cachedService = AiService(AiClient(p.apiKey, p.aiModel, base.ifBlank { null }))
                cachedAi = key
            }
            return cachedService
        }
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
