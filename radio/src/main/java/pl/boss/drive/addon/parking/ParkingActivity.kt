package pl.boss.drive.addon.parking

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.addon.Addon
import pl.boss.drive.core.addon.AddonActivity
import pl.boss.drive.core.addon.Addons
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.typeface
import pl.boss.drive.core.ui.vstack
import java.text.SimpleDateFormat
import java.util.Locale

/** Parking meter: pick the paid time, see the countdown, get warned before it runs out. */
class ParkingActivity : AddonActivity() {
    override val addon: Addon get() = Addons.byPkg("pl.boss.drive.addon.parking")!!
    private val prefs by lazy { getSharedPreferences("parking", Context.MODE_PRIVATE) }
    private lateinit var box: FrameLayout
    private var ring: Ring? = null
    private var until: TextView? = null
    private val tick = object : Runnable { override fun run() { update(); box.postDelayed(this, 1000) } }

    override fun buildContent(): View {
        val root = vstack()
        root.addView(header("Parkomat"))
        box = FrameLayout(this)
        root.addView(box, lp(MATCH, 0, 1f))
        render()
        return root
    }

    private fun endAt() = prefs.getLong("end", 0)
    private fun startAt() = prefs.getLong("start", 0)

    private fun render() {
        box.removeAllViews(); ring = null; until = null
        if (endAt() <= System.currentTimeMillis() - 60 * 60_000L) prefs.edit().remove("end").apply()
        if (endAt() == 0L) box.addView(picker()) else box.addView(running())
    }

    private fun picker(): View {
        val v = vstack(Gravity.CENTER)
        v.addView(text("Na ile opłaciłeś postój?", 24f, W.BOLD, pal.label, gravity = Gravity.CENTER))
        val opts = listOf(15, 30, 60, 90, 120, 180)
        opts.chunked(3).forEach { row ->
            val h = hstack()
            row.forEach { m ->
                val label = if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60},5 h"
                h.addView(text(label, 30f, W.BOLD, pal.label, gravity = Gravity.CENTER).apply {
                    background = RoundDrawable(pal.card, dp(20f)); onTap(Press.SCALE) { start(m) }
                }, lp(dpi(200f), dpi(110f)).apply { setMargins(dpi(8f), dpi(8f), dpi(8f), dpi(8f)) })
            }
            v.addView(h, lp(WRAP, WRAP))
        }
        v.addView(text("Ostrzeżenie przyjdzie 10 i 5 minut przed końcem.", 16f, W.REGULAR, pal.label2, gravity = Gravity.CENTER), lp(MATCH, WRAP).apply { topMargin = dpi(14f) })
        return v
    }

    private fun running(): View {
        val h = hstack(Gravity.CENTER)
        val r = Ring(this)
        ring = r
        h.addView(r, lp(dpi(380f), dpi(380f)))
        val col = vstack()
        val u = text("", 22f, W.SEMIBOLD, pal.label2)
        until = u
        col.addView(u)
        val add = hstack()
        add.addView(pill("+15 min", CR.drawable.ic_plus, pal.fill3, pal.teal) { extend(15) }, lp(WRAP, WRAP).apply { marginEnd = dpi(10f) })
        add.addView(pill("+30 min", CR.drawable.ic_plus, pal.fill3, pal.teal) { extend(30) })
        col.addView(add, lp(WRAP, WRAP).apply { topMargin = dpi(24f) })
        col.addView(pill("Zakończ postój", CR.drawable.ic_xmark, pal.fill3, pal.red) {
            confirm("Zakończyć postój?", "Odliczanie i ostrzeżenia zostaną wyłączone.", "Zakończ") { stop() }
        }, lp(WRAP, WRAP).apply { topMargin = dpi(12f) })
        h.addView(col, lp(WRAP, WRAP).apply { marginStart = dpi(50f) })
        update()
        return h
    }

    private fun update() {
        val r = ring ?: return
        val left = endAt() - System.currentTimeMillis()
        val total = (endAt() - startAt()).coerceAtLeast(1)
        r.frac = (left.toFloat() / total).coerceIn(0f, 1f)
        r.color = when { left <= 0 -> pal.red; left < 10 * 60_000 -> pal.orange; else -> pal.teal }
        r.big = if (left <= 0) "-" + fmt(-left) else fmt(left)
        r.small = if (left <= 0) "czas minął" else "zostało"
        r.invalidate()
        until?.text = "Opłacone do " + SimpleDateFormat("HH:mm", Locale("pl")).format(endAt())
    }

    private fun fmt(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

    private fun start(min: Int) {
        val now = System.currentTimeMillis()
        prefs.edit().putLong("start", now).putLong("end", now + min * 60_000L).apply()
        ParkingAlarm.schedule(this, now + min * 60_000L)
        render()
    }

    private fun extend(min: Int) {
        val end = maxOf(endAt(), System.currentTimeMillis()) + min * 60_000L
        prefs.edit().putLong("end", end).apply()
        ParkingAlarm.schedule(this, end); update()
    }

    private fun stop() { prefs.edit().remove("end").apply(); ParkingAlarm.cancel(this); render() }

    override fun onResume() { super.onResume(); render(); box.post(tick) }
    override fun onPause() { box.removeCallbacks(tick); super.onPause() }
}

class Ring(private val a: AddonActivity) : View(a) {
    var frac = 1f
    var color = Color.CYAN
    var big = ""
    var small = ""
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val r = RectF()
    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat(); val sw = s * 0.075f
        r.set(sw, sw, s - sw, s - sw)
        p.strokeWidth = sw; p.color = a.pal.fill3; c.drawArc(r, 0f, 360f, false, p)
        p.color = color; c.drawArc(r, -90f, 360f * frac, false, p)
        t.color = a.pal.label; t.typeface = typeface(W.BOLD, 40f); t.textSize = s * (if (big.length > 6) 0.17f else 0.21f); t.letterSpacing = -0.02f
        c.drawText(big, s / 2, s / 2 + t.textSize * 0.3f, t)
        t.color = a.pal.label2; t.typeface = typeface(W.MEDIUM, 17f); t.textSize = s * 0.06f; t.letterSpacing = 0f
        c.drawText(small, s / 2, s / 2 + s * 0.17f, t)
    }
}

/** Warnings 10 min / 5 min before and at the end, even with the screen off. */
class ParkingAlarm : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val left = i.getIntExtra("left", 0)
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("parking", "Parkomat", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, 0, Intent(c, ParkingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(77, Notification.Builder(c, "parking").setSmallIcon(CR.drawable.ic_timer)
            .setContentTitle(if (left > 0) "Postój kończy się za $left min" else "Opłacony postój minął")
            .setContentText("Parkomat • Boss Drive").setContentIntent(open).setAutoCancel(true).build())
    }

    companion object {
        private fun pi(c: Context, left: Int) = PendingIntent.getBroadcast(c, 500 + left, Intent(c, ParkingAlarm::class.java).putExtra("left", left),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun schedule(c: Context, end: Long) {
            val am = c.getSystemService(AlarmManager::class.java)
            for (left in intArrayOf(10, 5, 0)) {
                val at = end - left * 60_000L
                if (at > System.currentTimeMillis()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, left)) else am.cancel(pi(c, left))
            }
        }

        fun cancel(c: Context) { val am = c.getSystemService(AlarmManager::class.java); for (left in intArrayOf(10, 5, 0)) am.cancel(pi(c, left)) }
    }
}

