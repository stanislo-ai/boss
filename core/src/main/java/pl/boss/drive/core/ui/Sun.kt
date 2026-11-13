package pl.boss.drive.core.ui

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Sunrise/sunset (NOAA approximation) — drives the automatic light/dark theme. */
object Sun {
    fun isNight(lat: Double = 52.0, lon: Double = 19.0, now: Long = System.currentTimeMillis()): Boolean {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = now }
        val n = c.get(Calendar.DAY_OF_YEAR)
        val g = 2 * Math.PI / 365.0 * (n - 1)
        val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
            0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val latR = Math.toRadians(lat)
        val x = cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)
        if (x > 1) return true
        if (x < -1) return false
        val ha = Math.toDegrees(acos(x))
        val rise = 720 - 4 * (lon + ha) - eq
        val set = 720 - 4 * (lon - ha) - eq
        val m = (c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)).toDouble()
        val r = ((rise + 10) + 1440) % 1440
        val s = ((set + 20) + 1440) % 1440
        return if (r < s) m < r || m >= s else m >= s && m < r
    }
}
