package com.stopfat.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.stopfat.app.data.db.AppDatabase
import com.stopfat.app.data.db.DayStat
import com.stopfat.app.domain.StepMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.coroutines.resume

/** Reads the built-in hardware step counter (TYPE_STEP_COUNTER) and stores daily steps. */
class StepCounter(
    private val context: Context,
    private val db: AppDatabase,
    private val store: ProfileStore,
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val mutex = Mutex()
    private var liveListener: SensorEventListener? = null

    val isAvailable: Boolean get() = sensor != null

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Live updates while the app is visible. */
    fun startLive(scope: CoroutineScope) {
        if (sensor == null || !hasPermission() || liveListener != null) return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val value = event.values.firstOrNull()?.toLong() ?: return
                scope.launch { record(value) }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        liveListener = listener
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stopLive() {
        liveListener?.let { sensorManager.unregisterListener(it) }
        liveListener = null
    }

    /** One-shot read used by the background worker. */
    suspend fun readOnce(timeoutMs: Long = 5000) {
        if (sensor == null || !hasPermission()) return
        val value = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Long?> { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sensorManager.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values.firstOrNull()?.toLong())
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
            }
        } ?: return
        record(value)
    }

    private suspend fun record(counter: Long) = mutex.withLock {
        val today = LocalDate.now().toEpochDay()
        val result = StepMath.update(store.stepState(), counter, today)
        store.saveStepState(result.state)
        val dao = db.dayStats()
        val current = dao.dayOnce(today) ?: DayStat(today)
        if (current.steps != result.todaySteps) dao.upsert(current.copy(steps = result.todaySteps))
    }
}
