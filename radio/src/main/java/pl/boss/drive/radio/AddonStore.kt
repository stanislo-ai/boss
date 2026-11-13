package pl.boss.drive.radio

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import pl.boss.drive.core.addon.Addon
import pl.boss.drive.core.addon.Addons
import pl.boss.drive.core.net.Packet
import pl.boss.drive.core.net.Proto
import pl.boss.drive.core.net.forEachObj
import pl.boss.drive.core.net.pkt
import pl.boss.drive.core.util.Bg
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Optional apps. Our add-ons travel from the phone over the existing link (no internet needed)
 * and are installed with the system installer; YouTube Music comes from Google Play.
 */
object AddonStore {
    /** pkg -> (size, version) of add-on APKs the connected phone can send. */
    val available = ConcurrentHashMap<String, Pair<Long, Int>>()
    /** pkg -> download progress 0..100, or -1 while the system installer is busy. */
    val progress = ConcurrentHashMap<String, Int>()
    private val files = ConcurrentHashMap<String, RandomAccessFile>()
    var lastError = ""

    fun installedVersion(c: Context, pkg: String): Int = try {
        @Suppress("DEPRECATION") c.packageManager.getPackageInfo(pkg, 0).versionCode
    } catch (_: PackageManager.NameNotFoundException) { 0 }

    enum class State { GET, DOWNLOADING, INSTALLING, OPEN, UPDATE, UNAVAILABLE }

    fun state(c: Context, a: Addon): State {
        val p = progress[a.pkg]
        if (p != null) return if (p < 0) State.INSTALLING else State.DOWNLOADING
        val v = installedVersion(c, a.pkg)
        if (a.external) return if (v > 0) State.OPEN else State.GET
        val av = available[a.pkg]
        return when {
            v > 0 && av != null && av.second > v -> State.UPDATE
            v > 0 -> State.OPEN
            av != null -> State.GET
            else -> State.UNAVAILABLE
        }
    }

    fun onCatalog(p: Packet) {
        available.clear()
        p.arr("items").forEachObj { available[it.optString("pkg")] = it.optLong("size") to it.optInt("ver", 1) }
        Store.emit(Topic.STORE)
    }

    /** "Get" pressed in the store. */
    fun get(a: Activity, addon: Addon, onNeedsPermission: () -> Unit) {
        if (addon.external) { openPlayStore(a, addon.pkg); return }
        if (!a.packageManager.canRequestPackageInstalls()) { onNeedsPermission(); return }
        if (!Store.connected) { MainActivity.toast("Połącz telefon — dodatki pobierają się z niego"); return }
        progress[addon.pkg] = 0
        Store.emit(Topic.STORE)
        Hub.send(pkt(Proto.APP_GET, "pkg" to addon.pkg))
    }

    fun openInstallSettings(a: Activity) {
        try { a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName))) }
        catch (_: Throwable) { a.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
    }

    private fun apk(pkg: String) = File(Hub.app.cacheDir, "$pkg.apk")

    fun onChunk(p: Packet) {
        val pkg = p.s("pkg"); val body = p.body ?: return
        val raf = files.getOrPut(pkg) { RandomAccessFile(apk(pkg).also { it.delete() }, "rw") }
        raf.seek(p.l("off")); raf.write(body)
        val size = available[pkg]?.first ?: 0
        if (size > 0) { progress[pkg] = ((raf.length() * 100) / size).toInt().coerceAtMost(99); Store.emit(Topic.STORE) }
    }

    fun onEnd(p: Packet) {
        val pkg = p.s("pkg")
        files.remove(pkg)?.close()
        val f = apk(pkg)
        if (!f.exists() || f.length() != p.l("size")) { progress.remove(pkg); lastError = "Przerwane pobieranie"; Store.emit(Topic.STORE); return }
        progress[pkg] = -1
        Store.emit(Topic.STORE)
        Bg.run { install(pkg, f) }
    }

    private fun install(pkg: String, f: File) {
        try {
            val pi = Hub.app.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply { setAppPackageName(pkg) }
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                s.openWrite("base.apk", 0, f.length()).use { out -> f.inputStream().use { it.copyTo(out) }; s.fsync(out) }
                val intent = Intent(Hub.app, InstallReceiver::class.java).putExtra("pkg", pkg)
                s.commit(PendingIntent.getBroadcast(Hub.app, id, intent, PendingIntent.FLAG_UPDATE_CURRENT).intentSender)
            }
        } catch (e: Throwable) {
            progress.remove(pkg); lastError = e.message ?: "Błąd instalacji"; Store.emit(Topic.STORE)
            MainActivity.toast("Nie udało się zainstalować")
        }
    }

    fun installFinished(pkg: String, ok: Boolean, msg: String?) {
        progress.remove(pkg)
        apk(pkg).delete()
        if (ok) { MainActivity.toast("Zainstalowano: ${Addons.byPkg(pkg)?.name ?: pkg}"); AppMedia.publishSources() }
        else if (msg != null) MainActivity.toast("Instalacja przerwana")
        Store.emit(Topic.STORE)
    }

    fun open(a: Activity, pkg: String, dark: Boolean) {
        val i = a.packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.putExtra("dark", dark).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try { a.startActivity(i) } catch (_: Throwable) {}
    }

    fun uninstall(a: Activity, pkg: String) {
        try { a.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))) } catch (_: Throwable) {}
    }

    private fun openPlayStore(a: Activity, pkg: String) {
        for (u in listOf("market://details?id=$pkg", "https://play.google.com/store/apps/details?id=$pkg")) {
            try { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Throwable) {}
        }
        MainActivity.toast("Brak Sklepu Play na radiu")
    }
}

/** Result from the system package installer. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val pkg = i.getStringExtra("pkg") ?: return
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { c.startActivity(confirm) } catch (_: Throwable) {}
            }
            PackageInstaller.STATUS_SUCCESS -> AddonStore.installFinished(pkg, true, null)
            else -> AddonStore.installFinished(pkg, false, i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
