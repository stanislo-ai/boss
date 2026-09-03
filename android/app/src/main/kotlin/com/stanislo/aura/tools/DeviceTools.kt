package com.stanislo.aura.tools

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.getSystemService
import kotlinx.serialization.json.JsonObject

/** Stan i sterowanie telefonem: bateria, siec, latarka, glosnosc, multimedia, ustawienia. */
class DeviceTools(private val context: Context) {

    private val audioManager: AudioManager? get() = context.getSystemService()

    fun status(): JsonObject {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null

        val stat = StatFs(Environment.getDataDirectory().path)
        val freeGb = stat.availableBytes / 1_000_000_000.0
        val totalGb = stat.totalBytes / 1_000_000_000.0

        val audio = audioManager
        val volumes = if (audio == null) emptyMap() else mapOf(
            "media" to percentOf(audio, AudioManager.STREAM_MUSIC),
            "ring" to percentOf(audio, AudioManager.STREAM_RING),
            "alarm" to percentOf(audio, AudioManager.STREAM_ALARM),
            "notification" to percentOf(audio, AudioManager.STREAM_NOTIFICATION),
        )

        return toolResult(
            "status" to "ok",
            "battery_percent" to batteryPercent,
            "charging" to plugged,
            "network" to networkDescription(),
            "storage_free_gb" to String.format(java.util.Locale.US, "%.1f", freeGb),
            "storage_total_gb" to String.format(java.util.Locale.US, "%.1f", totalGb),
            "ringer_mode" to when (audio?.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "silent"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                AudioManager.RINGER_MODE_NORMAL -> "normal"
                else -> "unknown"
            },
            "volumes_percent" to volumes,
            "device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            "android_version" to android.os.Build.VERSION.RELEASE,
        )
    }

    fun setFlashlight(args: JsonObject): JsonObject {
        val on = args.bool("on") ?: return toolError("Nie podano, czy wlaczyc czy wylaczyc latarke.")
        val manager = context.getSystemService<CameraManager>()
            ?: return toolError("Aparat jest niedostepny.")
        return try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return toolError("To urzadzenie nie ma diody latarki.")
            manager.setTorchMode(cameraId, on)
            toolOk(if (on) "Latarka wlaczona." else "Latarka wylaczona.")
        } catch (e: Exception) {
            toolError("Nie udalo sie przelaczyc latarki: ${e.message}")
        }
    }

    fun setVolume(args: JsonObject): JsonObject {
        val percent = args.int("percent")?.coerceIn(0, 100)
            ?: return toolError("Brak docelowego poziomu glosnosci.")
        val audio = audioManager ?: return toolError("System dzwieku jest niedostepny.")
        val stream = when (args.str("stream")) {
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            "call" -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }
        return try {
            val max = audio.getStreamMaxVolume(stream)
            audio.setStreamVolume(stream, Math.round(max * percent / 100f), 0)
            toolOk("Ustawilem glosnosc na $percent%.")
        } catch (e: SecurityException) {
            toolError(
                "System nie pozwolil zmienic glosnosci - najprawdopodobniej wlaczony jest tryb " +
                    "Nie przeszkadzac i aplikacja potrzebuje na to osobnej zgody.",
            )
        } catch (e: Exception) {
            toolError("Nie udalo sie zmienic glosnosci: ${e.message}")
        }
    }

    fun setRingerMode(args: JsonObject): JsonObject {
        val audio = audioManager ?: return toolError("System dzwieku jest niedostepny.")
        val mode = when (args.str("mode")) {
            "silent" -> AudioManager.RINGER_MODE_SILENT
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            "normal" -> AudioManager.RINGER_MODE_NORMAL
            else -> return toolError("Nieznany tryb dzwieku.")
        }
        val policyManager = context.getSystemService<NotificationManager>()
        if (mode != AudioManager.RINGER_MODE_NORMAL &&
            policyManager?.isNotificationPolicyAccessGranted == false
        ) {
            context.launchActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return toolError(
                "Do wyciszania telefonu Android wymaga osobnej zgody. Otworzylem odpowiedni ekran " +
                    "ustawien - uzytkownik musi wlaczyc tam dostep dla Aury.",
            )
        }
        return try {
            audio.ringerMode = mode
            toolOk("Zmienilem tryb dzwieku.")
        } catch (e: SecurityException) {
            toolError("System odmowil zmiany trybu dzwieku.")
        }
    }

    fun mediaControl(args: JsonObject): JsonObject {
        val keyCode = when (args.str("action")) {
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            "play_pause", null -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            else -> return toolError("Nieznana akcja odtwarzacza.")
        }
        val audio = audioManager ?: return toolError("System dzwieku jest niedostepny.")
        return try {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            toolOk("Wyslalem polecenie do odtwarzacza.")
        } catch (e: Exception) {
            toolError("Nie udalo sie sterowac odtwarzaczem: ${e.message}")
        }
    }

    fun openSettings(args: JsonObject): JsonObject {
        val section = args.str("section") ?: "main"
        val action = when (section) {
            "wifi" -> Settings.Panel.ACTION_WIFI
            "internet" -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "date" -> Settings.ACTION_DATE_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "hotspot" -> Settings.ACTION_WIRELESS_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "notifications" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return if (context.launchActivity(Intent(action))) {
            toolOk("Otworzylem ustawienia: $section.")
        } else if (context.launchActivity(Intent(Settings.ACTION_SETTINGS))) {
            toolOk("Nie znalazlem tego konkretnego ekranu, wiec otworzylem glowne Ustawienia.")
        } else {
            toolError("Nie udalo sie otworzyc Ustawien.")
        }
    }

    // ---------- srodki pomocnicze ----------

    private fun percentOf(audio: AudioManager, stream: Int): Int {
        val max = audio.getStreamMaxVolume(stream).takeIf { it > 0 } ?: return 0
        return audio.getStreamVolume(stream) * 100 / max
    }

    private fun networkDescription(): String {
        val manager = context.getSystemService<ConnectivityManager>() ?: return "nieznana"
        val network = manager.activeNetwork ?: return "brak polaczenia"
        val caps = manager.getNetworkCapabilities(network) ?: return "brak polaczenia"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "dane komorkowe"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "inne polaczenie"
        }
    }
}
