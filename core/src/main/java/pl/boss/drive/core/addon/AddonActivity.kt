package pl.boss.drive.core.addon

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import pl.boss.drive.core.R
import pl.boss.drive.core.ui.Fonts
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Palette
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.Sun
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.clipRound
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.glyph
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.iconTile
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack
import kotlin.math.max
import kotlin.math.min

/**
 * Base for Boss Drive add-on apps: same 600dp design canvas, same CarPlay dock on the left
 * (clock, the app's icon, Home back to Boss Drive), same theme as the head unit.
 */
abstract class AddonActivity : Activity() {
    lateinit var pal: Palette; private set
    protected lateinit var content: FrameLayout; private set
    private lateinit var clock: TextView
    abstract val addon: Addon

    override fun attachBaseContext(base: Context) {
        val dm = base.resources.displayMetrics
        val minPx = min(dm.widthPixels, dm.heightPixels).coerceAtLeast(320)
        val cfg = Configuration(base.resources.configuration)
        cfg.densityDpi = max(100, (minPx / 600f * 160f).toInt())
        cfg.fontScale = 1f
        super.attachBaseContext(base.createConfigurationContext(cfg))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fonts.init(this)
        val prefs = getSharedPreferences("addon_ui", MODE_PRIVATE)
        if (intent?.hasExtra("dark") == true) prefs.edit().putBoolean("dark", intent.getBooleanExtra("dark", true)).putLong("darkAt", System.currentTimeMillis()).apply()
        val fresh = System.currentTimeMillis() - prefs.getLong("darkAt", 0) < 3 * 3_600_000
        pal = Palette.of(if (fresh) prefs.getBoolean("dark", true) else Sun.isNight())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(pal.bg))
        val root = LinearLayout(this)
        root.addView(buildDock(), LinearLayout.LayoutParams(dpi(92f), MATCH))
        content = FrameLayout(this)
        content.setBackgroundColor(pal.bg)
        root.addView(content, LinearLayout.LayoutParams(0, MATCH, 1f))
        setContentView(root)
        content.addView(buildContent(), FrameLayout.LayoutParams(MATCH, MATCH))
    }

    abstract fun buildContent(): View

    fun contentFrame(): FrameLayout = content

    private fun buildDock(): View {
        val d = vstack(Gravity.CENTER_HORIZONTAL)
        d.setBackgroundColor(pal.dock)
        d.pad(0f, 16f, 0f, 14f)
        clock = text("", 20f, W.SEMIBOLD, pal.label, gravity = Gravity.CENTER)
        d.addView(clock, lp(MATCH, WRAP))
        d.addView(View(this), lp(1, 0, 1f))
        val tile = iconTile(addon.glyph, addon.color, 60f, 0.56f)
        d.addView(tile, lp(dpi(60f), dpi(60f)))
        d.addView(View(this), lp(1, 0, 1f))
        val home = FrameLayout(this)
        home.addView(glyph(R.drawable.ic_grid, pal.label), FrameLayout.LayoutParams(dpi(30f), dpi(30f), Gravity.CENTER))
        home.background = RoundDrawable(pal.fill3, dp(14f))
        home.onTap(Press.SCALE) { goHome() }
        d.addView(home, lp(dpi(58f), dpi(58f)))
        d.addView(View(this), lp(1, dpi(52f)))   // same Home position as in Boss Drive's dock
        return d
    }

    /** Add-ons live inside Boss Drive: Home simply returns to it. */
    fun goHome() {
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    /** Return true if the add-on handled back itself (e.g. closed a sub-page). */
    open fun onBackInside(): Boolean = false

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (!onBackInside()) goHome() }

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { clock.text = android.text.format.DateFormat.format("H:mm", System.currentTimeMillis()) }
    }

    override fun onResume() {
        super.onResume()
        clock.text = android.text.format.DateFormat.format("H:mm", System.currentTimeMillis())
        registerReceiver(tick, IntentFilter(Intent.ACTION_TIME_TICK))
        hideBars()
    }

    override fun onPause() { try { unregisterReceiver(tick) } catch (_: Throwable) {}; super.onPause() }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideBars() }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    // ------------------------------------------------------------------ shared building blocks

    fun header(title: String, trailing: View? = null): View {
        val h = hstack(Gravity.BOTTOM)
        h.pad(28f, 20f, 24f, 10f)
        h.addView(text(title, 34f, W.BOLD, pal.label), lp(0, WRAP, 1f))
        trailing?.let { h.addView(it) }
        return h
    }

    /** iOS-style alert over the content area. */
    fun confirm(title: String, msg: String, yes: String, destructive: Boolean = true, onYes: () -> Unit) {
        val dim = FrameLayout(this)
        dim.setBackgroundColor(0x66000000); dim.isClickable = true
        val cardV = vstack(Gravity.CENTER_HORIZONTAL)
        cardV.background = RoundDrawable(if (pal.dark) 0xFC2C2C2E.toInt() else 0xFCF2F2F2.toInt(), dp(16f))
        val body = vstack(Gravity.CENTER_HORIZONTAL).apply { pad(20f, 22f, 20f, 20f) }
        body.addView(text(title, 19f, W.SEMIBOLD, pal.label, lines = 2, gravity = Gravity.CENTER), lp(MATCH, WRAP))
        if (msg.isNotEmpty()) body.addView(text(msg, 15f, W.REGULAR, pal.label, lines = 5, gravity = Gravity.CENTER), lp(MATCH, WRAP).apply { topMargin = dpi(6f) })
        cardV.addView(body, lp(MATCH, WRAP))
        cardV.addView(View(this).apply { setBackgroundColor(pal.separator) }, lp(MATCH, maxOf(1, dpi(0.5f))))
        val row = hstack()
        val no = text("Anuluj", 19f, W.REGULAR, pal.blue, gravity = Gravity.CENTER)
        no.onTap { content.removeView(dim) }
        val ok = text(yes, 19f, W.SEMIBOLD, if (destructive) pal.red else pal.blue, gravity = Gravity.CENTER)
        ok.onTap { content.removeView(dim); onYes() }
        row.addView(no, lp(0, dpi(56f), 1f))
        row.addView(View(this).apply { setBackgroundColor(pal.separator) }, lp(maxOf(1, dpi(0.5f)), MATCH))
        row.addView(ok, lp(0, dpi(56f), 1f))
        cardV.addView(row, lp(MATCH, WRAP))
        dim.addView(cardV, FrameLayout.LayoutParams(dpi(340f), WRAP, Gravity.CENTER))
        content.addView(dim, FrameLayout.LayoutParams(MATCH, MATCH))
        cardV.scaleX = 1.15f; cardV.scaleY = 1.15f
        cardV.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(pl.boss.drive.core.ui.Motion.spring).start()
    }

    fun card(radius: Float = 18f): FrameLayout = FrameLayout(this).apply {
        background = RoundDrawable(pal.card, dp(radius)); clipRound(dp(radius))
    }

    fun pill(label: String, glyphRes: Int, fill: Int, fg: Int, onClick: () -> Unit): View = hstack(Gravity.CENTER).apply {
        background = RoundDrawable(fill, dp(12f))
        if (glyphRes != 0) addView(glyph(glyphRes, fg), lp(dpi(20f), dpi(20f)).apply { marginEnd = dpi(8f) })
        addView(text(label, 17f, W.SEMIBOLD, fg))
        pad(18f, 0f, 20f, 0f)
        minimumHeight = dpi(52f)
        onTap(Press.SCALE) { onClick() }
    }

    fun empty(glyphRes: Int, title: String, sub: String): View = vstack(Gravity.CENTER).apply {
        addView(glyph(glyphRes, pal.gray3), lp(dpi(64f), dpi(64f)))
        addView(text(title, 22f, W.BOLD, pal.label2, gravity = Gravity.CENTER), lp(WRAP, WRAP).apply { topMargin = dpi(16f) })
        addView(text(sub, 16f, W.REGULAR, pal.label3, lines = 3, gravity = Gravity.CENTER), lp(dpi(480f), WRAP).apply { topMargin = dpi(8f) })
    }

    fun listView(): ListView = ListView(this).apply {
        divider = null
        selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        cacheColorHint = Color.TRANSPARENT
        setPadding(0, 0, 0, dpi(24f)); clipToPadding = false
    }

    /** Stat tile: small caption + big value (+ unit). */
    fun stat(caption: String, valueSize: Float = 34f): Pair<View, TextView> {
        val c = card(16f)
        val v = vstack()
        v.pad(18f, 14f, 18f, 14f)
        v.addView(text(caption.uppercase(), 13f, W.SEMIBOLD, pal.label2))
        v.addView(View(this), lp(1, 0, 1f))   // widget style: caption on top, big value at the bottom
        val value = text("—", valueSize + 6f, W.BOLD, pal.label)
        v.addView(value, lp(MATCH, WRAP).apply { topMargin = dpi(4f) })
        c.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        return c to value
    }
}

class SimpleAdapter<T>(private val make: () -> View, private val bindFn: (View, T, Int) -> Unit) : BaseAdapter() {
    var items: List<T> = emptyList(); set(v) { field = v; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(p: Int): Any? = items[p]
    override fun getItemId(p: Int) = p.toLong()
    override fun getView(p: Int, c: View?, parent: ViewGroup?): View { val v = c ?: make(); bindFn(v, items[p], p); return v }
}

/** Big, car-friendly numeric keypad (iOS phone-keypad look) with a value display. */
class NumPad(private val a: AddonActivity, private val allowDot: Boolean = true, private val onChange: (String) -> Unit) : LinearLayout(a) {
    var value = ""
        set(v) { field = v; onChange(v) }

    init {
        orientation = VERTICAL
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (allowDot) "," else "", "0", "⌫")
        keys.chunked(3).forEach { row ->
            val r = a.hstack()
            row.forEach { k ->
                val t = a.text(k, 30f, W.REGULAR, a.pal.label, gravity = Gravity.CENTER)
                if (k.isNotEmpty()) {
                    t.background = RoundDrawable(a.pal.fill3, a.dp(14f))
                    t.onTap(Press.SCALE) { press(k) }
                }
                r.addView(t, a.lp(a.dpi(96f), a.dpi(66f)).apply { setMargins(a.dpi(5f), a.dpi(5f), a.dpi(5f), a.dpi(5f)) })
            }
            addView(r)
        }
    }

    private fun press(k: String) {
        value = when (k) {
            "⌫" -> value.dropLast(1)
            "," -> if (value.contains(',')) value else (value.ifEmpty { "0" } + ",")
            else -> if (value.length >= 9) value else if (value == "0") k else value + k
        }
    }
}

fun String.toDoubleOrNullPl(): Double? = replace(',', '.').toDoubleOrNull()
