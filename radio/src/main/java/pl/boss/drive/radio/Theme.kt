package pl.boss.drive.radio

import pl.boss.drive.core.ui.Sun

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
            Sun.isNight(lat, lon)
        }
    }
}
