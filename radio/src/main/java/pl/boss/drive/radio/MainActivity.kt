package pl.boss.drive.radio

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
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.ui.BatteryView
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Motion
import pl.boss.drive.core.ui.Palette
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.SignalBars
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.flp
import pl.boss.drive.core.ui.glyph
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.iconTile
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack
import pl.boss.drive.core.util.Bg
import pl.boss.drive.radio.ui.Banner
import pl.boss.drive.radio.ui.CallOverlay
import pl.boss.drive.radio.ui.HomeScreen
import pl.boss.drive.radio.ui.IosAlert
import pl.boss.drive.radio.ui.MessagesScreen
import pl.boss.drive.radio.ui.NavStack
import pl.boss.drive.radio.ui.NowPlayingScreen
import pl.boss.drive.radio.ui.PhoneScreen
import pl.boss.drive.radio.ui.Screen
import pl.boss.drive.radio.ui.SettingsScreen
import pl.boss.drive.radio.ui.Wallpaper
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

enum class Tab { HOME, APPS, STORE, MUSIC, PHONE, MESSAGES, SETTINGS }

/**
 * CarPlay-style shell: dock on the left, one navigation stack per app, overlays for calls,
 * notification banners, alerts and HUDs. Everything is plain Views — no AppCompat, no
 * Compose — so it starts instantly and stays light on a 1 GB head unit.
 */
class MainActivity : Activity() {
    lateinit var pal: Palette; private set
    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var overlay: FrameLayout
    private val stacks = HashMap<Tab, NavStack>()
    var tab = Tab.HOME; private set
    private val dockItems = HashMap<Tab, View>()
    private val dockMarks = HashMap<Tab, View>()
    private lateinit var clock: TextView
    private lateinit var signal: SignalBars
    private lateinit var battery: BatteryView
    private lateinit var net: TextView
    private lateinit var linkGlyph: android.widget.ImageView
    private lateinit var badge: TextView
    private var call: CallOverlay? = null
    private val unsubs = ArrayList<() -> Unit>()
    private var themeDark = true

    // design canvas: everything is laid out for a 600dp-tall landscape screen, whatever the panel
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
        current = WeakReference(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        themeDark = Theme.isDark()
        pal = Palette.of(themeDark)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(pal.groupedBg))
        buildUi()
        savedInstanceState?.getString("tab")?.let { t -> Tab.values().firstOrNull { it.name == t }?.let { select(it, animate = false) } }
        handleIntent(intent)
    }

    private fun buildUi() {
        root = FrameLayout(this)
        root.setBackgroundColor(if (pal.dark) Color.BLACK else pal.groupedBg)
        val main = LinearLayout(this)
        main.addView(buildDock(), LinearLayout.LayoutParams(dpi(92f), MATCH))
        content = FrameLayout(this)
        content.addView(Wallpaper(this, pal.dark), FrameLayout.LayoutParams(MATCH, MATCH))
        main.addView(content, LinearLayout.LayoutParams(0, MATCH, 1f))
        root.addView(main, FrameLayout.LayoutParams(MATCH, MATCH))
        overlay = FrameLayout(this)
        root.addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH).apply { leftMargin = dpi(92f) })
        setContentView(root)
        select(Tab.HOME, animate = false)
    }

    // ------------------------------------------------------------------ dock

    private fun buildDock(): View {
        val d = vstack(Gravity.CENTER_HORIZONTAL)
        d.setBackgroundColor(pal.dock)
        d.pad(0f, 16f, 0f, 14f)
        clock = text("", 20f, W.SEMIBOLD, pal.label, gravity = Gravity.CENTER)
        d.addView(clock, lp(MATCH, WRAP))
        val st = hstack(Gravity.CENTER)
        signal = SignalBars(this, pal.label)
        st.addView(signal, lp(dpi(15f), dpi(10f)))
        net = text("", 10f, W.SEMIBOLD, pal.label)
        st.addView(net, lp(WRAP, WRAP).apply { marginStart = dpi(3f) })
        battery = BatteryView(this, pal.label, pal.green, pal.red)
        st.addView(battery, lp(dpi(22f), dpi(11f)).apply { marginStart = dpi(5f) })
        d.addView(st, lp(MATCH, WRAP).apply { topMargin = dpi(6f) })
        linkGlyph = glyph(CR.drawable.ic_iphone, pal.gray)
        d.addView(linkGlyph, lp(dpi(16f), dpi(16f)).apply { topMargin = dpi(5f) })

        d.addView(View(this), lp(1, 0, 1f))
        dockApp(d, Tab.MUSIC, CR.drawable.ic_music, 0xFFFA2D48.toInt())
        dockApp(d, Tab.PHONE, CR.drawable.ic_phone, 0xFF34C759.toInt())
        val msg = dockApp(d, Tab.MESSAGES, CR.drawable.ic_message, 0xFF2FD158.toInt())
        badge = text("", 13f, W.SEMIBOLD, Color.WHITE, gravity = Gravity.CENTER).apply {
            background = RoundDrawable(0xFFFF3B30.toInt(), dp(11f)); minWidth = dpi(22f); setPadding(dpi(6f), 0, dpi(6f), 0); visibility = View.GONE
        }
        (msg as FrameLayout).addView(badge, FrameLayout.LayoutParams(WRAP, dpi(22f), Gravity.TOP or Gravity.END).apply { topMargin = dpi(-2f); rightMargin = dpi(6f) })
        dockApp(d, Tab.SETTINGS, CR.drawable.ic_gear, 0xFF8E8E93.toInt())
        d.addView(View(this), lp(1, 0, 1f))

        // CarPlay "home" button: back to the dashboard
        val home = FrameLayout(this)
        home.addView(glyph(CR.drawable.ic_grid, pal.label), FrameLayout.LayoutParams(dpi(30f), dpi(30f), Gravity.CENTER))
        home.background = RoundDrawable(pal.fill3, dp(14f))
        // like CarPlay: Home toggles between the Dashboard and the app grid
        home.onTap(Press.SCALE) { if (tab == Tab.HOME) select(Tab.APPS) else select(Tab.HOME) }
        dockMarks[Tab.HOME] = home
        d.addView(home, lp(dpi(58f), dpi(58f)))
        // leave to the radio's own launcher without killing us
        val exit = glyph(CR.drawable.ic_apps, pal.label2)
        exit.pad(10f)
        exit.onTap { goToSystem() }
        d.addView(exit, lp(dpi(44f), dpi(44f)).apply { topMargin = dpi(8f) })
        return d
    }

    private fun dockApp(d: LinearLayout, t: Tab, glyphRes: Int, color: Int): View {
        val f = FrameLayout(this)
        val tile = iconTile(glyphRes, color, 60f, 0.56f)
        f.addView(tile, FrameLayout.LayoutParams(dpi(60f), dpi(60f), Gravity.CENTER))
        val mark = View(this).apply { background = RoundDrawable(pal.label, dp(2f)); alpha = 0f }
        f.addView(mark, FrameLayout.LayoutParams(dpi(4f), dpi(22f), Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dpi(3f) })
        dockMarks[t] = mark
        f.onTap(Press.SCALE) { if (tab == t) stacks[t]?.popToRoot() else select(t) }
        d.addView(f, lp(MATCH, dpi(74f)))
        dockItems[t] = f
        return f
    }

    private fun refreshDock() {
        clock.text = android.text.format.DateFormat.format("H:mm", System.currentTimeMillis())
        val s = Store.status
        val on = Store.connected
        signal.visibility = if (on) View.VISIBLE else View.GONE
        battery.visibility = if (on && s.battery >= 0) View.VISIBLE else View.GONE
        net.visibility = signal.visibility
        signal.level = s.signal; net.text = s.net
        battery.level = s.battery; battery.charging = s.charging
        linkGlyph.setImageResource(when { !on -> CR.drawable.ic_iphone; Store.transport == "wifi" -> CR.drawable.ic_wifi; else -> CR.drawable.ic_bluetooth })
        linkGlyph.imageTintList = android.content.res.ColorStateList.valueOf(if (on) pal.green else pal.gray2)
        val u = Store.unread
        badge.visibility = if (u > 0) View.VISIBLE else View.GONE
        badge.text = if (u > 9) "9+" else u.toString()
    }

    // ------------------------------------------------------------------ navigation

    fun select(t: Tab, animate: Boolean = true) {
        val old = stacks[tab]
        val st = stacks.getOrPut(t) {
            NavStack(this, rootFor(t)).also { content.addView(it, FrameLayout.LayoutParams(MATCH, MATCH)); it.visibility = View.GONE }
        }
        if (t == Tab.MESSAGES) { Store.unread = 0; refreshDock() }
        for ((k, m) in dockMarks) if (k != Tab.HOME && k != Tab.APPS) m.animate().alpha(if (k == t) 1f else 0f).setDuration(200).start()
        if (old === st && st.visibility == View.VISIBLE) return
        tab = t
        old?.setActive(false)
        st.visibility = View.VISIBLE
        st.bringToFront()
        st.setActive(true)
        if (animate) {
            st.alpha = 0f; st.scaleX = 0.94f; st.scaleY = 0.94f
            st.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(Motion.easeOut).withEndAction {
                if (tab == t) stacks.values.forEach { if (it !== st) it.visibility = View.GONE }
            }.start()
        } else stacks.values.forEach { if (it !== st) it.visibility = View.GONE }
    }

    private fun rootFor(t: Tab): Screen = when (t) {
        Tab.HOME -> HomeScreen(this)
        Tab.APPS -> pl.boss.drive.radio.ui.AppsScreen(this)
        Tab.STORE -> pl.boss.drive.radio.ui.StoreScreen(this)
        Tab.MUSIC -> NowPlayingScreen(this)
        Tab.PHONE -> PhoneScreen(this)
        Tab.MESSAGES -> MessagesScreen(this)
        Tab.SETTINGS -> SettingsScreen(this)
    }

    fun push(s: Screen) { stacks[tab]?.push(s) }

    fun openMusic() { select(Tab.MUSIC); stacks[Tab.MUSIC]?.popToRoot() }

    fun back(): Boolean {
        if (overlay.childCount > 0) {
            val top = overlay.getChildAt(overlay.childCount - 1)
            if (top is IosAlert) { top.dismiss(); return true }
        }
        if (stacks[tab]?.pop() == true) return true
        if (tab != Tab.HOME) { select(Tab.HOME); return true }
        return false
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (!back()) goToSystem() }

    fun goToSystem() {
        try { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) { moveTaskToBack(true) }
    }

    // ------------------------------------------------------------------ overlays

    fun showOverlay(v: View, lp: FrameLayout.LayoutParams = FrameLayout.LayoutParams(MATCH, MATCH)) { overlay.addView(v, lp) }
    fun removeOverlay(v: View) { overlay.removeView(v) }

    fun alert(title: String, message: String, buttons: List<Pair<String, Int>>, onPick: (Int) -> Unit) {
        IosAlert(this, title, message, buttons, onPick).show()
    }

    fun hud(textStr: String, glyphRes: Int = CR.drawable.ic_check) {
        val v = vstack(Gravity.CENTER).apply {
            background = RoundDrawable(if (pal.dark) 0xF02C2C2E.toInt() else 0xF2F2F2F7.toInt(), dp(18f))
            pad(26f, 22f, 26f, 22f)
            addView(glyph(glyphRes, pal.label), lp(dpi(46f), dpi(46f)))
            addView(text(textStr, 17f, W.SEMIBOLD, pal.label, lines = 2, gravity = Gravity.CENTER), lp(WRAP, WRAP).apply { topMargin = dpi(12f) })
            elevation = dp(16f)
        }
        showOverlay(v, flp(WRAP, WRAP, Gravity.CENTER).apply { width = WRAP })
        v.alpha = 0f; v.scaleX = 0.85f; v.scaleY = 0.85f
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(Motion.spring).start()
        v.postDelayed({ v.animate().alpha(0f).setDuration(250).withEndAction { removeOverlay(v) }.start() }, 1600)
    }

    private fun syncCall() {
        val c = Store.call
        if (c.active) {
            val o = call ?: CallOverlay(this).also { call = it; showOverlay(it); it.enter() }
            o.update()
        } else call?.let { o -> call = null; o.exit { removeOverlay(o) } }
    }

    private fun syncPairing() {
        val p = Store.pendingPhone ?: return
        alert("Połączyć z „${p.second}”?", "Do radia próbuje się podłączyć inny telefon niż zapisany. Zezwolić i zapamiętać ten telefon?",
            listOf("Odrzuć" to 0, "Połącz" to 1)) { Hub.acceptPending(it == 1) }
    }

    // ------------------------------------------------------------------ lifecycle

    private val tickRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            refreshDock()
            if (Theme.isDark() != themeDark) recreate()
        }
    }

    override fun onResume() {
        super.onResume()
        visible = true
        hideSystemBars()
        if (Theme.isDark() != themeDark) { recreate(); return }
        registerReceiver(tickRx, IntentFilter(Intent.ACTION_TIME_TICK))
        unsubs += Store.on(Topic.STATUS) { refreshDock() }
        unsubs += Store.on(Topic.LINK) { refreshDock() }
        unsubs += Store.on(Topic.MESSAGES) { refreshDock() }
        unsubs += Store.on(Topic.CALL) { syncCall() }
        unsubs += Store.on(Topic.PAIRING) { syncPairing() }
        unsubs += Store.on(Topic.THEME) { if (Theme.isDark() != themeDark) recreate() }
        refreshDock(); syncCall(); syncPairing()
        stacks[tab]?.setActive(true)
    }

    override fun onPause() {
        visible = false
        try { unregisterReceiver(tickRx) } catch (_: Throwable) {}
        unsubs.forEach { it() }; unsubs.clear()
        stacks[tab]?.setActive(false)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideSystemBars() }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (!Hub.prefs.getBoolean("fullscreen", true)) return
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putString("tab", tab.name) }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }
    private fun handleIntent(i: Intent?) { if (i?.getStringExtra("tab") == "music") openMusic() }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        stacks.values.forEach { it.setActive(false) }
        super.onDestroy()
    }

    companion object {
        @Volatile var visible = false
        private var current: WeakReference<MainActivity>? = null

        fun banner(m: Message) = Bg.ui {
            val a = current?.get() ?: return@ui
            if (!visible || Store.call.active) return@ui
            if (Hub.prefs.getBoolean("banners", true)) Banner.show(a, m)
            if (Hub.prefs.getBoolean("autoRead", false)) Hub.message(m.key, "read")
        }

        fun toast(s: String) = Bg.ui { current?.get()?.takeIf { visible }?.hud(s, CR.drawable.ic_info) }
    }
}
