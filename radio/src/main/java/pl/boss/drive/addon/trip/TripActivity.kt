package pl.boss.drive.addon.trip

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.addon.Addon
import pl.boss.drive.core.addon.AddonActivity
import pl.boss.drive.core.addon.Addons
import pl.boss.drive.core.ui.Fonts
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Motion
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.fmtTime
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.typeface
import pl.boss.drive.core.ui.vstack
import pl.boss.drive.core.ui.withAlpha
import kotlin.math.roundToInt

/** Trip computer: GPS speedometer + distance, moving time, averages, altitude, heading. */
class TripActivity : AddonActivity(), LocationListener {
    override val addon: Addon get() = Addons.byPkg("pl.boss.drive.addon.trip")!!
    private val prefs by lazy { getSharedPreferences("trip", Context.MODE_PRIVATE) }
    private lateinit var gauge: Gauge
    private lateinit var gps: TextView
    private val values = HashMap<String, TextView>()

    private var distM = 0.0
    private var movingMs = 0L
    private var maxKmh = 0f
    private var startedAt = 0L
    private var last: Location? = null
    private var lastFixWall = 0L
    private var fixes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        load()
        super.onCreate(savedInstanceState)
    }

    override fun buildContent(): View {
        val root = vstack()
        root.addView(header("Trasa", pill("Nowa trasa", CR.drawable.ic_gauge_reset, pal.fill3, pal.blue) { reset() }))
        val body = hstack(Gravity.CENTER_VERTICAL)
        body.pad(20f, 0f, 24f, 20f)
        val left = FrameLayout(this)
        gauge = Gauge(this)
        left.addView(gauge, FrameLayout.LayoutParams(MATCH, MATCH))
        gps = text("", 15f, W.MEDIUM, pal.label2, gravity = Gravity.CENTER)
        left.addView(gps, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { bottomMargin = (dp(6f)).toInt() })
        body.addView(left, lp(0, MATCH, 1.05f))
        val grid = vstack()
        val rows = listOf(listOf("dist" to "Dystans", "time" to "Czas jazdy"), listOf("avg" to "Średnia", "max" to "Maksymalna"), listOf("alt" to "Wysokość", "dir" to "Kierunek"))
        rows.forEachIndexed { ri, row ->
            val h = hstack()
            row.forEachIndexed { ci, (key, cap) ->
                val (card, value) = stat(cap)
                values[key] = value
                h.addView(card, lp(0, MATCH, 1f).apply { if (ci == 0) marginEnd = (dp(12f)).toInt() })
            }
            grid.addView(h, lp(MATCH, 0, 1f).apply { if (ri > 0) topMargin = (dp(12f)).toInt() })
        }
        body.addView(grid, lp(0, MATCH, 1f).apply { marginStart = (dp(20f)).toInt() })
        root.addView(body, lp(MATCH, 0, 1f))
        return root
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else startGps()
        render(null)
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) { if (g.firstOrNull() == PackageManager.PERMISSION_GRANTED) startGps() }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        val lm = getSystemService(LocationManager::class.java)
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
            gps.text = "Szukam sygnału GPS…"
        } catch (e: Throwable) { gps.text = "GPS niedostępny" }
    }

    override fun onPause() {
        try { getSystemService(LocationManager::class.java).removeUpdates(this) } catch (_: Throwable) {}
        save()
        super.onPause()
    }

    override fun onLocationChanged(l: Location) {
        val prev = last
        val kmh = if (l.hasSpeed()) l.speed * 3.6f else if (prev != null) {
            val dt = (l.time - prev.time) / 1000f
            if (dt > 0) prev.distanceTo(l) / dt * 3.6f else 0f
        } else 0f
        if (prev != null && l.accuracy < 35f && kmh > 2.5f) {
            val d = prev.distanceTo(l)
            val dt = l.time - prev.time
            if (d < 500 && dt in 1..30_000) { distM += d; movingMs += dt }
        }
        if (l.accuracy < 35f && kmh < 300) maxKmh = maxOf(maxKmh, kmh)
        last = l
        lastFixWall = System.currentTimeMillis()
        render(l, kmh)
        if (++fixes % 15 == 0) save()
    }

    @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    override fun onProviderEnabled(p: String) {}
    override fun onProviderDisabled(p: String) { gps.text = "Włącz lokalizację GPS w radiu" }

    private fun render(l: Location?, kmh: Float = 0f) {
        if (l != null) {
            gauge.setSpeed(kmh)
            gps.text = "GPS • dokładność ${l.accuracy.roundToInt()} m"
            values["alt"]?.text = if (l.hasAltitude()) "${l.altitude.roundToInt()} m" else "—"
            values["dir"]?.text = if (l.hasBearing() && kmh > 3) compass(l.bearing) else "—"
        }
        values["dist"]?.text = if (distM < 1000) "${distM.roundToInt()} m" else "%.1f km".format(distM / 1000)
        values["time"]?.text = fmtTime(movingMs)
        values["avg"]?.text = if (movingMs > 30_000) "${(distM / (movingMs / 1000.0) * 3.6).roundToInt()} km/h" else "—"
        values["max"]?.text = "${maxKmh.roundToInt()} km/h"
    }

    private fun compass(b: Float): String {
        val names = arrayOf("Pn", "Pn-Wsch", "Wsch", "Pd-Wsch", "Pd", "Pd-Zach", "Zach", "Pn-Zach")
        return names[(((b + 22.5f) % 360) / 45).toInt().coerceIn(0, 7)]
    }

    private fun reset() { distM = 0.0; movingMs = 0; maxKmh = 0f; startedAt = System.currentTimeMillis(); save(); render(last, 0f) }

    private fun load() {
        val lastSeen = prefs.getLong("seen", 0)
        if (System.currentTimeMillis() - lastSeen > 6 * 3_600_000L) { startedAt = System.currentTimeMillis(); return }  // new trip after a long stop
        distM = prefs.getFloat("dist", 0f).toDouble(); movingMs = prefs.getLong("moving", 0); maxKmh = prefs.getFloat("max", 0f)
        startedAt = prefs.getLong("start", System.currentTimeMillis())
    }

    private fun save() {
        prefs.edit().putFloat("dist", distM.toFloat()).putLong("moving", movingMs).putFloat("max", maxKmh)
            .putLong("start", startedAt).putLong("seen", System.currentTimeMillis()).apply()
    }
}

/** 270° speed ring with a colour sweep, big rounded number in the middle. */
class Gauge(private val a: AddonActivity) : View(a) {
    private var shown = 0f
    private var anim: ValueAnimator? = null
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = a.pal.fill3 }
    private val prog = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(a.pal.label, 0.35f) }
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = a.pal.label; textAlign = Paint.Align.CENTER; typeface = typeface(W.BOLD, 40f) }
    private val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = a.pal.label2; textAlign = Paint.Align.CENTER; typeface = Fonts.semibold }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = a.pal.label3; textAlign = Paint.Align.CENTER; typeface = Fonts.medium }
    private val rect = RectF()
    private val max = 200f

    fun setSpeed(kmh: Float) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(shown, kmh.coerceIn(0f, max)).apply {
            duration = 900; interpolator = Motion.easeOut
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val cx = w / 2f; val cy = h / 2f
        prog.shader = SweepGradient(cx, cy, intArrayOf(a.pal.green, a.pal.green, a.pal.yellow, a.pal.orange, a.pal.red, a.pal.green),
            floatArrayOf(0f, 0.37f, 0.55f, 0.68f, 0.8f, 1f)).also {
            val m = android.graphics.Matrix(); m.setRotate(0f, cx, cy); it.setLocalMatrix(m)
        }
    }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height) * 0.86f
        val sw = s * 0.075f
        val cx = width / 2f; val cy = height / 2f
        rect.set(cx - s / 2 + sw, cy - s / 2 + sw, cx + s / 2 - sw, cy + s / 2 - sw)
        track.strokeWidth = sw; prog.strokeWidth = sw
        c.drawArc(rect, 135f, 270f, false, track)
        if (shown > 0.5f) c.drawArc(rect, 135f, 270f * (shown / max), false, prog)
        // ticks every 20 km/h
        val r = rect.width() / 2
        for (i in 0..10) {
            val ang = Math.toRadians(135.0 + 27.0 * i)
            val r1 = r - sw * 1.1f; val r2 = r - sw * (if (i % 2 == 0) 1.7f else 1.45f)
            tick.strokeWidth = a.dp(2f)
            c.drawLine(cx + (r1 * Math.cos(ang)).toFloat(), cy + (r1 * Math.sin(ang)).toFloat(),
                cx + (r2 * Math.cos(ang)).toFloat(), cy + (r2 * Math.sin(ang)).toFloat(), tick)
            if (i % 2 == 0) {
                small.textSize = s * 0.045f
                val r3 = r - sw * 2.5f
                c.drawText("${i * 20}", cx + (r3 * Math.cos(ang)).toFloat(), cy + (r3 * Math.sin(ang)).toFloat() + small.textSize / 3, small)
            }
        }
        num.textSize = s * 0.30f
        num.letterSpacing = -0.04f
        c.drawText(shown.roundToInt().toString(), cx, cy + num.textSize * 0.33f, num)
        unit.textSize = s * 0.06f
        c.drawText("km/h", cx, cy + num.textSize * 0.33f + unit.textSize * 1.6f, unit)
    }
}

