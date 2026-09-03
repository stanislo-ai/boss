package com.stanislo.aura

import android.app.Application
import com.stanislo.aura.system.ensureReminderChannel

class AuraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureReminderChannel()
    }
}
