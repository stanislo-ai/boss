package pl.boss.drive.radio.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Motion
import pl.boss.drive.core.ui.Palette
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.TStyle
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.blend
import pl.boss.drive.core.ui.clipRound
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.frame
import pl.boss.drive.core.ui.glyph
import pl.boss.drive.core.ui.hairline
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack
import pl.boss.drive.core.ui.withAlpha
import pl.boss.drive.core.util.Bg
import pl.boss.drive.core.util.Img
import pl.boss.drive.radio.MainActivity
import pl.boss.drive.radio.Store
import pl.boss.drive.radio.Topic

/** A screen inside a tab's navigation stack. Listeners only live while the screen is on top. */
abstract class Screen(val a: MainActivity) {
    val ctx: Context get() = a
    val pal: Palette get() = a.pal
    open val title: String = ""
    private var built: View? = null
    val view: View get() = built ?: build().also { built = it }
    private val subs = ArrayList<() -> Unit>()
    private val tickers = ArrayList<Runnable>()

    protected abstract fun build(): View

    protected fun on(vararg ts: Topic, f: () -> Unit) { for (t in ts) subs.add(Store.on(t, f)) }

    /** Run [f] every [ms] while visible. */
    protected fun every(ms: Long, f: () -> Unit) {
        val r = object : Runnable { override fun run() { f(); Bg.main.postDelayed(this, ms) } }
        tickers.add(r); Bg.main.postDelayed(r, ms)
    }

    fun show() { onShow() }
    fun hide() {
        subs.forEach { it() }; subs.clear()
        tickers.forEach { Bg.main.removeCallbacks(it) }; tickers.clear()
        onHide()
    }

    open fun onShow() {}
    open fun onHide() {}
    open fun onBack(): Boolean = false
}

/** UINavigationController-like stack with the iOS push/pop parallax animation. */
class NavStack(private val a: MainActivity, root: Screen) : FrameLayout(a) {
    private val stack = ArrayList<Pair<Screen, View>>()
    var active = false
        private set

    init {
        stack.add(root to root.view)
        addView(root.view, LayoutParams(MATCH, MATCH))
    }

    val top: Screen get() = stack.last().first
    val depth get() = stack.size

    fun setActive(on: Boolean) {
        if (on == active) return
        active = on
        if (on) top.show() else top.hide()
    }

    fun push(s: Screen) {
        val prev = stack.last()
        val wrapped = wrap(s, prev.first.title.ifEmpty { "Wstecz" })
        stack.add(s to wrapped)
        addView(wrapped, LayoutParams(MATCH, MATCH))
        if (active) { prev.first.hide(); s.show() }
        val w = width.toFloat().coerceAtLeast(1f)
        wrapped.translationX = w
        wrapped.animate().translationX(0f).setDuration(Motion.NORMAL).setInterpolator(Motion.push).setListener(null).start()
        prev.second.animate().translationX(-w * 0.3f).alpha(0.6f).setDuration(Motion.NORMAL).setInterpolator(Motion.push)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(an: Animator) { if (stack.size > 1 && stack.last().second !== prev.second) prev.second.visibility = INVISIBLE }
            }).start()
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        val cur = stack.removeAt(stack.size - 1)
        val prev = stack.last()
        if (active) { cur.first.hide(); prev.first.show() }
        prev.second.visibility = VISIBLE
        val w = width.toFloat()
        prev.second.animate().translationX(0f).alpha(1f).setDuration(300).setInterpolator(Motion.push).setListener(null).start()
        cur.second.animate().translationX(w).setDuration(300).setInterpolator(Motion.push).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(an: Animator) { removeView(cur.second) }
        }).start()
        return true
    }

    fun popToRoot() { while (stack.size > 1) { val c = stack.removeAt(stack.size - 1); if (active) c.first.hide(); removeView(c.second) }
        stack[0].second.apply { translationX = 0f; alpha = 1f; visibility = VISIBLE }; if (active) stack[0].first.show() }

    private fun wrap(s: Screen, backTitle: String): View {
        val col = a.vstack()
        col.setBackgroundColor(a.pal.groupedBg)
        col.isClickable = true
        val bar = a.frame()
        val back = a.hstack().apply {
            addView(a.glyph(CR.drawable.ic_chevron_left, a.pal.blue), a.lp(dpi(26f), dpi(26f)))
            addView(a.text(backTitle, 19f, W.REGULAR, a.pal.blue))
            pad(8f, 0f, 20f, 0f)
            onTap { a.back() }
        }
        bar.addView(back, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START or Gravity.CENTER_VERTICAL))
        bar.addView(a.text(s.title, 19f, W.SEMIBOLD, a.pal.label, gravity = Gravity.CENTER).apply { setPadding(dpi(150f), 0, dpi(150f), 0) },
            FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
        col.addView(bar, a.lp(MATCH, dpi(60f)))
        col.addView(a.hairline(a.pal.separator))
        col.addView(s.view, a.lp(MATCH, 0, 1f))
        return col
    }
}


/** Soft abstract gradient wallpaper (CarPlay-like), rendered once at low-res and upscaled. */
class Wallpaper(ctx: Context, private val dark: Boolean) : View(ctx) {
    private var bmp: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = Rect()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w == 0 || h == 0) return
        val bw = 160; val bh = (160f * h / w).toInt().coerceAtLeast(60)
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(if (dark) 0xFF05060B.toInt() else 0xFFEDEFF6.toInt())
        val blobs = if (dark) listOf(
            Triple(0.12f to 0.15f, 0.75f, 0xFF35206E.toInt()), Triple(0.88f to 0.9f, 0.7f, 0xFF0B4A6B.toInt()),
            Triple(0.62f to 0.05f, 0.5f, 0xFF5E1A47.toInt()), Triple(0.35f to 1.0f, 0.45f, 0xFF102A5C.toInt()),
        ) else listOf(
            Triple(0.1f to 0.1f, 0.8f, 0xFFB8C7F7.toInt()), Triple(0.9f to 0.85f, 0.75f, 0xFFC9ECE6.toInt()),
            Triple(0.65f to 0.0f, 0.55f, 0xFFF4D2E4.toInt()), Triple(0.3f to 1.0f, 0.5f, 0xFFD9D3F7.toInt()),
        )
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((pos, r, col) in blobs) {
            p.shader = RadialGradient(pos.first * bw, pos.second * bh, r * bw, col, withAlpha(col, 0f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), p)
        }
        Img.stackBlur(b, 6)
        bmp?.recycle(); bmp = b
    }

    override fun onDraw(canvas: Canvas) {
        val b = bmp ?: return
        dst.set(0, 0, width, height); canvas.drawBitmap(b, null, dst, paint)
    }
}

/** Card used on the dashboard: translucent material over the wallpaper. */
fun MainActivity.card(radius: Float = 18f): FrameLayout = FrameLayout(this).apply {
    background = RoundDrawable(if (pal.dark) 0xE01C1C1E.toInt() else 0xEBFFFFFF.toInt(), dp(radius))
    clipRound(dp(radius))
}

/** Generic ListView adapter that builds rows with a factory and binds them. */
class Adapter<T>(private val make: () -> View, private val bindFn: (View, T, Int) -> Unit) : BaseAdapter() {
    var items: List<T> = emptyList()
        set(v) { field = v; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(p: Int): Any? = items[p]
    override fun getItemId(p: Int) = p.toLong()
    override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
        val v = convert ?: make()
        bindFn(v, items[p], p)
        return v
    }
}

fun MainActivity.listView(): ListView = ListView(this).apply {
    divider = null
    selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
    isVerticalScrollBarEnabled = true
    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    cacheColorHint = Color.TRANSPARENT
    setPadding(0, 0, 0, dpi(24f))
    clipToPadding = false
}

/** Media row used by every music list: [art 48] title / subtitle ... trailing. */
class MediaRow(val a: MainActivity) : FrameLayout(a) {
    val art = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipRound(dp(7f)) }
    val title: TextView = a.text("", 18f, W.MEDIUM, a.pal.label)
    val sub: TextView = a.text("", 15f, W.REGULAR, a.pal.label2)
    val trailing: TextView = a.text("", 15f, W.REGULAR, a.pal.label2)
    val sep = a.hairline(a.pal.separator)
    val eq = pl.boss.drive.core.ui.EqBars(a, a.pal.pink)

    init {
        val h = a.hstack()
        h.pad(20f, 0f, 22f, 0f)
        h.addView(art, a.lp(dpi(50f), dpi(50f)).apply { marginEnd = dpi(16f) })
        val col = a.vstack(Gravity.CENTER_VERTICAL)
        col.addView(title); col.addView(sub, a.lp(WRAP, WRAP).apply { topMargin = dpi(3f) })
        h.addView(col, a.lp(0, WRAP, 1f))
        h.addView(eq, a.lp(dpi(16f), dpi(15f)).apply { marginStart = dpi(10f) })
        h.addView(trailing, a.lp(WRAP, WRAP).apply { marginStart = dpi(12f) })
        addView(h, LayoutParams(MATCH, dpi(70f)))
        addView(sep, LayoutParams(MATCH, maxOf(1, dpi(0.5f)), Gravity.BOTTOM).apply { marginStart = dpi(86f) })
        onTap(Press.HIGHLIGHT, a.pal.cellPressed) { onClick?.invoke() }
    }

    var onClick: (() -> Unit)? = null

    fun setArt(b: Bitmap?, glyphRes: Int = CR.drawable.ic_music) {
        if (b != null) { art.setImageBitmap(b); art.background = null; art.setPadding(0, 0, 0, 0); art.imageTintList = null }
        else {
            art.setImageResource(glyphRes)
            art.imageTintList = android.content.res.ColorStateList.valueOf(a.pal.gray)
            art.background = RoundDrawable(a.pal.card3, dp(7f), blend(a.pal.card3, Color.WHITE, 0.08f), a.pal.card3)
            val p = dpi(13f); art.setPadding(p, p, p, p)
        }
    }
}

/** Tiny decoded-thumbnail cache for offline track art (keyed by file path). */
object ArtCache {
    private val sized = object : LruCache<String, Bitmap>(3 * 1024 * 1024) { override fun sizeOf(k: String, v: Bitmap) = v.byteCount }
    private val loading = HashSet<String>()
    fun clear() { sized.evictAll() }
    fun get(path: String, onLoaded: () -> Unit): Bitmap? {
        if (path.isEmpty()) return null
        sized.get(path)?.let { return it }
        if (loading.add(path)) Bg.load({ Img.decodeFile(path, 128) }) { b -> loading.remove(path); if (b != null) { sized.put(path, b); onLoaded() } }
        return null
    }
}

/** Large iOS title header ("Muzyka", "Telefon"...). */
fun MainActivity.largeTitle(s: String, trailing: View? = null): View {
    val h = hstack(Gravity.BOTTOM)
    h.pad(28f, 22f, 24f, 10f)
    h.addView(text(s, 34f, W.BOLD, pal.label), lp(0, WRAP, 1f))
    trailing?.let { h.addView(it) }
    return h
}

fun MainActivity.emptyState(glyphRes: Int, title: String, sub: String): View = vstack(Gravity.CENTER).apply {
    addView(glyph(glyphRes, pal.gray3), lp(dpi(64f), dpi(64f)))
    addView(text(title, 22f, W.BOLD, pal.label2, gravity = Gravity.CENTER), lp(WRAP, WRAP).apply { topMargin = dpi(16f) })
    addView(text(sub, 16f, W.REGULAR, pal.label3, lines = 3, gravity = Gravity.CENTER), lp(dpi(460f), WRAP).apply { topMargin = dpi(8f) })
}

fun MainActivity.pillButton(label: String, glyphRes: Int, fill: Int, fg: Int, onClick: () -> Unit): View = hstack(Gravity.CENTER).apply {
    background = RoundDrawable(fill, dp(12f))
    if (glyphRes != 0) addView(glyph(glyphRes, fg), lp(dpi(20f), dpi(20f)).apply { marginEnd = dpi(8f) })
    addView(text(label, 17f, W.SEMIBOLD, fg))
    pad(18f, 0f, 20f, 0f)
    minimumHeight = dpi(50f)
    onTap(Press.SCALE) { onClick() }
}

fun relTime(ms: Long): String {
    val now = System.currentTimeMillis()
    val d = now - ms
    val cal = java.util.Calendar.getInstance()
    val today = cal.apply { timeInMillis = now; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0) }.timeInMillis
    return when {
        d < 60_000 -> "teraz"
        d < 3_600_000 -> "${d / 60_000} min temu"
        ms >= today -> android.text.format.DateFormat.format("HH:mm", ms).toString()
        ms >= today - 86_400_000 -> "wczoraj"
        ms >= today - 6 * 86_400_000 -> java.text.SimpleDateFormat("EEEE", java.util.Locale("pl")).format(ms)
        else -> java.text.SimpleDateFormat("d.MM.yy", java.util.Locale("pl")).format(ms)
    }
}

/** Initials avatar like Contacts: grey gradient circle with white letters. */
class Avatar(ctx: Context) : View(ctx) {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = pl.boss.drive.core.ui.Fonts.semibold }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var initials = ""
    private var bmp: Bitmap? = null
    private val path = android.graphics.Path()
    fun set(name: String, photo: Bitmap?) {
        initials = name.split(' ', '-').filter { it.isNotBlank() && it[0].isLetter() }.take(2).joinToString("") { it[0].uppercase() }
        bmp = photo; invalidate()
    }
    override fun onDraw(c: Canvas) {
        val r = width / 2f
        val b = bmp
        if (b != null) {
            path.reset(); path.addCircle(r, r, r, android.graphics.Path.Direction.CW)
            c.save(); c.clipPath(path); c.drawBitmap(b, null, Rect(0, 0, width, height), bmpPaint); c.restore(); return
        }
        bg.shader = android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFFA5ABB8.toInt(), 0xFF858994.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(r, r, r, bg)
        if (initials.isEmpty()) {
            // person silhouette
            bg.shader = null; bg.color = Color.WHITE
            c.drawCircle(r, r * 0.8f, r * 0.36f, bg)
            c.save(); path.reset(); path.addCircle(r, r, r, android.graphics.Path.Direction.CW); c.clipPath(path)
            c.drawOval(r * 0.3f, r * 1.3f, r * 1.7f, r * 2.4f, bg); c.restore()
        } else {
            txt.textSize = r * 0.82f
            val fm = txt.fontMetrics
            c.drawText(initials, r, r - (fm.ascent + fm.descent) / 2, txt)
        }
    }
}

fun LinearLayout.addRowsPlain(a: MainActivity, views: List<View>) { views.forEach { addView(it, a.lp(MATCH, WRAP)) } }
