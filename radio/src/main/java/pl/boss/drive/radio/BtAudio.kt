package pl.boss.drive.radio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import pl.boss.drive.core.util.Bg

/** State of the head unit's standard Android Bluetooth (used for the hotspot-free link). */
object RadioBt {
    /** "listening" = phones can connect over Bluetooth; "off"; "none" = no standard BT stack; "error". */
    @Volatile var state = "unknown"

    @SuppressLint("MissingPermission")
    fun name(): String = try { BluetoothAdapter.getDefaultAdapter()?.name ?: "" } catch (_: Throwable) { "" }

    @SuppressLint("MissingPermission")
    fun enable(): Boolean = try { BluetoothAdapter.getDefaultAdapter()?.enable() == true } catch (_: Throwable) { false }

    fun label(): String = when (state) {
        "listening" -> "Gotowy — telefon łączy się bez hotspotu"
        "off" -> "Wyłączony — dotknij, aby włączyć"
        "none" -> "Radio ma osobny moduł Bluetooth — połączenie tylko przez hotspot"
        "error" -> "Bluetooth nie odpowiada — spróbuj ponownie po restarcie radia"
        else -> "Sprawdzam…"
    }
}

/**
 * Many Chinese head units only route Bluetooth audio (A2DP) to the speakers while their own
 * "BT Music" app is the active source. Boss Drive can flip that source for you: it opens the
 * vendor app for a moment and comes straight back.
 */
object BtAudio {
    private val pattern = Regex("(^|[._ ])(bt|a2dp)|bluetooth|blue ?tooth|btmusic|bt ?music|muzyka bt", RegexOption.IGNORE_CASE)
    private var switchedThisLink = false

    data class App(val pkg: String, val label: String)

    /** Launchable apps that look like the unit's Bluetooth / BT-music app. */
    fun candidates(c: Context): List<App> {
        val pm = c.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0).mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg.startsWith("pl.boss.drive")) return@mapNotNull null
            val label = ri.loadLabel(pm).toString()
            if (pattern.containsMatchIn(pkg) || pattern.containsMatchIn(label)) App(pkg, label) else null
        }.distinctBy { it.pkg }
    }

    fun chosen(c: Context): App? {
        val pkg = Hub.prefs.getString("btApp", null) ?: return null
        return try { val pm = c.packageManager; App(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()) }
        catch (_: PackageManager.NameNotFoundException) { null }
    }

    fun choose(pkg: String?) { Hub.prefs.edit().apply { if (pkg == null) remove("btApp") else putString("btApp", pkg) }.apply() }

    /** Switch the unit's audio source to Bluetooth music and return to Boss Drive. */
    fun switchNow(c: Context): Boolean {
        val app = chosen(c) ?: candidates(c).firstOrNull()?.also { choose(it.pkg) } ?: return false
        val i = c.packageManager.getLaunchIntentForPackage(app.pkg) ?: return false
        val wasVisible = MainActivity.visible
        try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) { return false }
        if (wasVisible) Bg.main.postDelayed({
            try { c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) } catch (_: Throwable) {}
        }, 1600)
        return true
    }

    fun onLinkChanged() { switchedThisLink = false }

    /** Phone music started: flip the source once per connection if the user enabled it. */
    fun onPhonePlaying(c: Context) {
        if (switchedThisLink || !Hub.prefs.getBoolean("btAuto", false)) return
        switchedThisLink = true
        switchNow(c)
    }
}
