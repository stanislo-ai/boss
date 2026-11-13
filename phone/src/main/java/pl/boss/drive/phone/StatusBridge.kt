package pl.boss.drive.phone

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.telephony.TelephonyManager
import org.json.JSONObject
import pl.boss.drive.core.net.Proto
import pl.boss.drive.core.net.pkt
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Battery / signal for the dock, weather for the dashboard, and the parking spot. */
class StatusBridge(private val ctx: Context) {
    private val h = CarLink.handler
    private var started = false
    private var bat = -1
    private var charging = false
    private var lastWeatherAt = 0L

    private val batRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val nb = if (lvl >= 0) lvl * 100 / scale else -1
            val nc = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            if (nb != bat || nc != charging) { bat = nb; charging = nc; h.post { sendStatus() } }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            sendStatus()
            if (System.currentTimeMillis() - lastWeatherAt > 30 * 60_000) fetchWeather()
            h.postDelayed(this, 30_000)
        }
    }

    /** Is the phone's media audio actually going to a Bluetooth device (the car)? */
    @Volatile var a2dp = true; private set
    @Volatile var a2dpName = ""; private set
    private val am by lazy { ctx.getSystemService(android.media.AudioManager::class.java) }
    private val devCb = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(d: Array<out android.media.AudioDeviceInfo>?) { h.post { checkA2dp(); sendStatus() } }
        override fun onAudioDevicesRemoved(d: Array<out android.media.AudioDeviceInfo>?) { h.post { checkA2dp(); sendStatus() } }
    }

    private fun checkA2dp() {
        val dev = try { am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } } catch (_: Throwable) { null }
        val now = dev != null
        a2dpName = dev?.productName?.toString() ?: ""
        if (now != a2dp) { a2dp = now; Events.emit(Ev.LINK) }
    }

    fun start() {
        if (started) return
        started = true
        checkA2dp()
        try { am.registerAudioDeviceCallback(devCb, h) } catch (_: Throwable) {}
        registerSystem(ctx, batRx, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        h.post(tick)
    }

    fun stop() {
        if (!started) return
        started = false
        try { ctx.unregisterReceiver(batRx) } catch (_: Throwable) {}
        try { am.unregisterAudioDeviceCallback(devCb) } catch (_: Throwable) {}
        h.removeCallbacks(tick)
    }

    fun pushAll() = h.post { sendStatus(); lastWeatherAt = 0; fetchWeather() }

    @SuppressLint("MissingPermission")
    private fun sendStatus() {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        var sig = 0; var net = ""; var carrier = ""
        try { sig = tm.signalStrength?.level ?: 0 } catch (_: Throwable) {}
        try { carrier = tm.networkOperatorName ?: "" } catch (_: Throwable) {}
        try {
            if (Perms.has(ctx, arrayOf(android.Manifest.permission.READ_PHONE_STATE))) {
                net = when (tm.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                    TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSDPA,
                    TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                    TelephonyManager.NETWORK_TYPE_EDGE -> "E"
                    TelephonyManager.NETWORK_TYPE_GPRS -> "G"
                    else -> ""
                }
            }
        } catch (_: Throwable) {}
        CarLink.send(pkt(Proto.STATUS, "bat" to bat, "chg" to charging, "sig" to sig, "net" to net, "carrier" to carrier, "a2dp" to a2dp, "a2dpName" to a2dpName))
    }

    @SuppressLint("MissingPermission")
    fun lastLocation(): Location? {
        if (!Perms.has(ctx, Perms.LOCATION)) return null
        val lm = ctx.getSystemService(LocationManager::class.java)
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { try { lm.getLastKnownLocation(it) } catch (_: Throwable) { null } }
            .maxByOrNull { it.time }
    }

    private fun fetchWeather() {
        val loc = lastLocation()
        val lat: Double; val lon: Double
        if (loc != null) {
            lat = loc.latitude; lon = loc.longitude
            CarLink.prefs.edit().putFloat("wLat", lat.toFloat()).putFloat("wLon", lon.toFloat()).apply()
        } else {
            lat = CarLink.prefs.getFloat("wLat", 0f).toDouble(); lon = CarLink.prefs.getFloat("wLon", 0f).toDouble()
            if (lat == 0.0 && lon == 0.0) return
        }
        lastWeatherAt = System.currentTimeMillis()
        Thread({
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f".format(Locale.US, lat, lon) +
                    "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1")
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val js = JSONObject(c.inputStream.bufferedReader().readText())
                val cur = js.getJSONObject("current")
                val daily = js.getJSONObject("daily")
                var place = CarLink.prefs.getString("wPlace", "") ?: ""
                try {
                    @Suppress("DEPRECATION")
                    Geocoder(ctx, Locale("pl")).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { a ->
                        place = a.locality ?: a.subAdminArea ?: place
                        CarLink.prefs.edit().putString("wPlace", place).apply()
                    }
                } catch (_: Throwable) {}
                CarLink.send(pkt(Proto.WEATHER,
                    "temp" to Math.round(cur.getDouble("temperature_2m")).toInt(), "code" to cur.getInt("weather_code"),
                    "day" to (cur.optInt("is_day", 1) == 1),
                    "hi" to Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
                    "lo" to Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
                    "place" to place, "lat" to lat, "lon" to lon))
            } catch (_: Throwable) { lastWeatherAt = System.currentTimeMillis() - 25 * 60_000 }
        }, "weather").start()
    }

    /** Remember where the car was left (called when the car's Bluetooth disconnects). */
    fun saveParking() {
        if (!CarLink.prefs.getBoolean("parking", true)) return
        val l = lastLocation() ?: return
        if (System.currentTimeMillis() - l.time > 15 * 60_000) return
        CarLink.prefs.edit().putFloat("pLat", l.latitude.toFloat()).putFloat("pLon", l.longitude.toFloat()).putLong("pTime", System.currentTimeMillis()).apply()
    }
}
