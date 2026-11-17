package pl.boss.drive.phone

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import pl.boss.drive.core.net.Proto
import pl.boss.drive.core.net.pkt
import pl.boss.drive.core.util.Img

/**
 * Turn-by-turn from the phone's navigation app, shown on the head unit.
 * Navigation apps keep an ongoing notification with the next manoeuvre (arrow icon, distance,
 * street, ETA) — exactly what smartwatches mirror. No screen streaming, works over Bluetooth too.
 */
object NavBridge {
    val NAV_APPS = setOf(
        "com.google.android.apps.maps", "com.waze", "com.sygic.aura", "net.osmand", "net.osmand.plus",
        "com.here.app.maps", "com.mapfactor.navigator", "cz.seznam.mapy", "com.tomtom.gplay.navapp", "ru.yandex.yandexnavi",
        "com.nng.igo.primong.igoworld", "app.organicmaps", "com.google.android.apps.mapslite",
    )

    private data class State(val key: String, val pkg: String, val app: String, val distance: String, val instruction: String,
                             val eta: String, val iconHash: Int, val actions: List<String>)

    @Volatile private var state: State? = null
    @Volatile private var icon: ByteArray? = null
    private var actions: Array<Notification.Action> = emptyArray()

    fun isNav(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return false
        return n.category == Notification.CATEGORY_NAVIGATION || (sbn.packageName in NAV_APPS && n.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    fun onPosted(ctx: android.content.Context, sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = (ex.getCharSequence(Notification.EXTRA_TEXT) ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString()?.trim() ?: ""
        val sub = (ex.getCharSequence(Notification.EXTRA_SUB_TEXT) ?: ex.getCharSequence(Notification.EXTRA_INFO_TEXT))?.toString()?.trim() ?: ""
        if (title.isEmpty() && text.isEmpty()) return
        // Most apps put the distance in the title ("300 m") and the street/instruction in the text; some swap them.
        val (dist, instr) = if (looksLikeDistance(text) && !looksLikeDistance(title)) text to title else title to text
        val bmp = largeIcon(ctx, n)
        val bytes = bmp?.let { Img.png(it, 160) }
        val hash = bytes?.contentHashCode() ?: 0
        val app = try { val pm = ctx.packageManager; pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() } catch (_: Throwable) { sbn.packageName }
        actions = n.actions ?: emptyArray()
        val s = State(sbn.key, sbn.packageName, app, dist, instr, sub, hash, actions.map { it.title?.toString() ?: "" })
        if (s == state) return
        val iconChanged = hash != state?.iconHash
        state = s; icon = bytes
        send(withIcon = iconChanged)
    }

    fun onRemoved(sbn: StatusBarNotification) {
        if (state?.key != sbn.key) return
        state = null; icon = null; actions = emptyArray()
        CarLink.send(pkt(Proto.NAV, "active" to false))
    }

    fun push() { if (state != null) send(withIcon = true) else CarLink.send(pkt(Proto.NAV, "active" to false)) }

    private fun send(withIcon: Boolean) {
        val s = state ?: return
        CarLink.send(pkt(Proto.NAV, "active" to true, "app" to s.app, "pkg" to s.pkg, "dist" to s.distance, "text" to s.instruction,
            "eta" to s.eta, "icon" to s.iconHash, "actions" to JSONArray(s.actions), body = if (withIcon) icon else null))
    }

    /** Radio pressed a button copied from the navigation notification (e.g. "Zakończ"). */
    fun command(i: Int) {
        val a = actions.getOrNull(i) ?: return
        try { a.actionIntent?.send() } catch (_: Throwable) {}
    }

    private fun looksLikeDistance(s: String) = Regex("^\\s*[0-9]+([.,][0-9]+)?\\s*(m|km|ft|mi|yd)\\b", RegexOption.IGNORE_CASE).containsMatchIn(s)

    private fun largeIcon(ctx: android.content.Context, n: Notification): Bitmap? {
        val ic: Icon = n.getLargeIcon() ?: return null
        return try {
            val d = ic.loadDrawable(ctx) ?: return null
            val w = d.intrinsicWidth.takeIf { it > 0 } ?: 128; val h = d.intrinsicHeight.takeIf { it > 0 } ?: 128
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b -> d.setBounds(0, 0, w, h); d.draw(Canvas(b)) }
        } catch (_: Throwable) { null }
    }
}
