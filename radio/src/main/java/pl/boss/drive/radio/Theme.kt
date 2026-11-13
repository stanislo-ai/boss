package pl.boss.drive.radio

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Light/dark like CarPlay: automatic by real sunrise/sunset for the car's position. */
object Theme {
    const val AUTO = 0; const val LIGHT = 1; const val DARK = 2

    fun mode() = Hub.prefs.getInt("theme", AUTO)

    fun isDark(): Boolean = when (mode()) {
        LIGHT -> false
        DARK -> true
        else -> {
            val lat = Hub.prefs.getFloat("lat", 52.0f).toDouble().takeIf { it != 0.0 } ?: 52.0
            val lon = Hub.prefs.getFloat("lon", 19.0f).toDouble().takeIf { it != 0.0 } ?: 19.0
            isNight(lat, lon, System.currentTimeMillis())
        }
    }

    /** NOAA approximation; good to a couple of minutes, plenty for UI theming. */
    fun isNight(lat: Double, lon: Double, now: Long): Boolean {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = now }
        val n = c.get(Calendar.DAY_OF_YEAR)
        val g = 2 * Math.PI / 365.0 * (n - 1)
        val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
            0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val latR = Math.toRadians(lat)
        val x = cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)
        if (x > 1) return true      // polar night
        if (x < -1) return false    // midnight sun
        val ha = Math.toDegrees(acos(x))
        val rise = 720 - 4 * (lon + ha) - eq
        val set = 720 - 4 * (lon - ha) - eq
        val mins = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        // switch to dark ~20 min after sunset, back to light ~10 min after sunrise
        val m = ((mins - 0) + 1440) % 1440
        val r = ((rise + 10) + 1440) % 1440
        val s = ((set + 20) + 1440) % 1440
        return if (r < s) m < r || m >= s else m >= s && m < r
    }
}
