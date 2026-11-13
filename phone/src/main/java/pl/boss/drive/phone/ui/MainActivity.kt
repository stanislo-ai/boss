package pl.boss.drive.phone.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Motion
import pl.boss.drive.core.ui.Palette
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.glyph
import pl.boss.drive.core.ui.hairline
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.tint
import pl.boss.drive.core.ui.vstack
import pl.boss.drive.phone.CarLink
import pl.boss.drive.phone.CarService

/** Base for a tab page: built once, listens to events only while visible. */
abstract class Page(val a: MainActivity) {
    val pal get() = a.pal
    private var built: View? = null
    val view: View get() = built ?: build().also { built = it }
    private val subs = ArrayList<() -> Unit>()
    protected abstract fun build(): View
    protected fun on(e: pl.boss.drive.phone.Ev, f: () -> Unit) { subs += pl.boss.drive.phone.Events.on(e, f) }
    open fun onShow() {}
    fun hide() { subs.forEach { it() }; subs.clear() }
}

class MainActivity : Activity() {
    lateinit var pal: Palette; private set
    private lateinit var content: FrameLayout
    private val pages = arrayOfNulls<Page>(3)
    private val tabIcons = ArrayList<ImageView>()
    private val tabLabels = ArrayList<TextView>()
    var current = 0; private set
    private var insetTop = 0
    private var insetBottom = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = Palette.of((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
        setupWindow()
        val root = vstack()
        root.setBackgroundColor(pal.groupedBg)
        content = FrameLayout(this)
        root.addView(content, lp(MATCH, 0, 1f))
        root.addView(hairline(pal.separator))
        root.addView(buildTabBar(), lp(MATCH, WRAP))
        setContentView(root)
        root.setOnApplyWindowInsetsListener { v, ins ->
            @Suppress("DEPRECATION")
            insetTop = ins.systemWindowInsetTop
            @Suppress("DEPRECATION")
            insetBottom = ins.systemWindowInsetBottom
            v.setPadding(0, 0, 0, 0)
            tabBar.setPadding(0, dpi(6f), 0, insetBottom + dpi(2f))
            content.setPadding(0, insetTop, 0, 0)
            ins
        }
        select(intent?.getIntExtra("tab", 0) ?: 0)
        // opening the app in the car = start looking for the radio right away
        CarService.start(this)
    }

    @Suppress("DEPRECATION")
    private fun setupWindow() {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = pal.chrome
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (!pal.dark) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }

    private lateinit var tabBar: LinearLayout

    private fun buildTabBar(): View {
        tabBar = LinearLayout(this)
        tabBar.setBackgroundColor(pal.chrome)
        val items = listOf("Auto" to CR.drawable.ic_car, "Do auta" to CR.drawable.ic_music, "Ustawienia" to CR.drawable.ic_gear)
        items.forEachIndexed { i, (label, icon) ->
            val col = vstack(Gravity.CENTER_HORIZONTAL)
            val g = glyph(icon, pal.gray)
            col.addView(g, lp(dpi(27f), dpi(27f)))
            val t = text(label, 10.5f, W.MEDIUM, pal.gray, gravity = Gravity.CENTER)
            col.addView(t, lp(WRAP, WRAP).apply { topMargin = dpi(2f) })
            col.onTap(pl.boss.drive.core.ui.Press.NONE) { select(i) }
            tabIcons += g; tabLabels += t
            tabBar.addView(col, lp(0, WRAP, 1f))
        }
        return tabBar
    }

    fun select(i: Int) {
        val old = pages[current]
        if (old != null && current != i) old.hide()
        current = i
        val p = pages[i] ?: when (i) { 0 -> AutoPage(this); 1 -> MusicPage(this); else -> SettingsPage(this) }.also { pages[i] = it }
        content.removeAllViews()
        content.addView(p.view, FrameLayout.LayoutParams(MATCH, MATCH))
        p.view.alpha = 0.6f; p.view.animate().alpha(1f).setDuration(160).setInterpolator(Motion.easeOut).start()
        tabIcons.forEachIndexed { k, g -> g.tint(if (k == i) pal.blue else pal.gray) }
        tabLabels.forEachIndexed { k, t -> t.setTextColor(if (k == i) pal.blue else pal.gray) }
        p.hide(); p.onShow()
    }

    override fun onResume() {
        super.onResume()
        pages[current]?.let { it.hide(); it.onShow() }
    }

    override fun onPause() { pages[current]?.hide(); super.onPause() }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); onResume() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        CarLink.init(this)
        if (CarLink.running) { CarLink.media.refresh(); CarLink.calls.pushAll() }
        pages[current]?.let { it.hide(); it.onShow() }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); if (intent.hasExtra("tab")) select(intent.getIntExtra("tab", 0)) }

}
