package pl.boss.drive.radio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import pl.boss.drive.addon.fuel.FuelActivity
import pl.boss.drive.addon.podcasts.Library
import pl.boss.drive.addon.podcasts.PodcastsActivity
import pl.boss.drive.addon.service.ServiceActivity
import pl.boss.drive.addon.trip.TripActivity
import pl.boss.drive.addon.webradio.RadioActivity
import pl.boss.drive.core.addon.Addon
import pl.boss.drive.core.addon.Addons

/**
 * Optional features built into Boss Drive. "Getting" one in the store adds it to the app grid;
 * its code is only loaded into memory when it's opened. YouTube Music is the one exception:
 * it's Google's own app, installed from Google Play, which Boss Drive controls.
 */
object AddonStore {
    private val screens: Map<String, Class<out Activity>> = mapOf(
        "pl.boss.drive.addon.trip" to TripActivity::class.java,
        "pl.boss.drive.addon.fuel" to FuelActivity::class.java,
        "pl.boss.drive.addon.podcasts" to PodcastsActivity::class.java,
        "pl.boss.drive.addon.webradio" to RadioActivity::class.java,
        "pl.boss.drive.addon.service" to ServiceActivity::class.java,
        "pl.boss.drive.addon.parking" to pl.boss.drive.addon.parking.ParkingActivity::class.java,
        "pl.boss.drive.addon.recorder" to pl.boss.drive.addon.recorder.RecorderActivity::class.java,
        "pl.boss.drive.addon.tripcost" to pl.boss.drive.addon.tripcost.TripCostActivity::class.java,
        "pl.boss.drive.addon.pairs" to pl.boss.drive.addon.pairs.PairsActivity::class.java,
        "pl.boss.drive.addon.tictactoe" to pl.boss.drive.addon.tictactoe.TicTacToeActivity::class.java,
    )

    enum class State { GET, OPEN }

    private fun enabled(): MutableSet<String> = HashSet(Hub.prefs.getStringSet("addons", emptySet()) ?: emptySet())

    fun isInstalled(c: Context, a: Addon): Boolean =
        if (a.external) try { c.packageManager.getPackageInfo(a.pkg, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
        else a.pkg in enabled()

    fun installed(c: Context) = Addons.ALL.filter { isInstalled(c, it) }

    fun state(c: Context, a: Addon) = if (isInstalled(c, a)) State.OPEN else State.GET

    /** "Get" pressed in the store. */
    fun get(a: Activity, addon: Addon) {
        if (addon.external) { openPlayStore(a, addon.pkg); return }
        Hub.prefs.edit().putStringSet("addons", enabled().apply { add(addon.pkg) }).apply()
        if (addon.pkg == "pl.boss.drive.addon.podcasts") { Library.init(a); Library.schedule(a) }
        AppMedia.publishSources()
        Store.emit(Topic.STORE)
        MainActivity.toast("Dodano: ${addon.name}")
    }

    fun remove(a: Activity, addon: Addon) {
        if (addon.external) {
            try { a.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${addon.pkg}"))) } catch (_: Throwable) {}
            return
        }
        Hub.prefs.edit().putStringSet("addons", enabled().apply { remove(addon.pkg) }).apply()
        if (addon.pkg == "pl.boss.drive.addon.podcasts") Library.unschedule(a)
        AppMedia.publishSources()
        Store.emit(Topic.STORE)
    }

    fun open(a: Context, pkg: String, dark: Boolean) {
        val cls = screens[pkg]
        val i = if (cls != null) Intent(a, cls) else a.packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.putExtra("dark", dark)
        if (cls == null || a !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { a.startActivity(i) } catch (_: Throwable) {}
        if (cls != null && a is Activity) a.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun openPlayStore(a: Activity, pkg: String) {
        for (u in listOf("market://details?id=$pkg", "https://play.google.com/store/apps/details?id=$pkg")) {
            try { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Throwable) {}
        }
        MainActivity.toast("Brak Sklepu Play na radiu")
    }
}
