package pl.boss.drive.core.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

// ---------- layout params helpers ----------
const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
fun Context.flp(w: Int, h: Int, gravity: Int = Gravity.NO_GRAVITY) = FrameLayout.LayoutParams(w, h, gravity)

fun View.margins(l: Float = 0f, t: Float = 0f, r: Float = 0f, b: Float = 0f): View {
    (layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(dpi(l), dpi(t), dpi(r), dpi(b))
    return this
}

fun View.pad(l: Float, t: Float = l, r: Float = l, b: Float = t): View { setPadding(dpi(l), dpi(t), dpi(r), dpi(b)); return this }

fun Context.vstack(gravity: Int = Gravity.NO_GRAVITY, block: LinearLayout.() -> Unit = {}) =
    LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; this.gravity = gravity; block() }

fun Context.hstack(gravity: Int = Gravity.CENTER_VERTICAL, block: LinearLayout.() -> Unit = {}) =
    LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; this.gravity = gravity; block() }

fun Context.frame(block: FrameLayout.() -> Unit = {}) = FrameLayout(this).apply(block)

fun Context.text(
    s: CharSequence = "", style: TStyle = TStyle.BODY, color: Int = Color.WHITE,
    lines: Int = 1, gravity: Int = Gravity.NO_GRAVITY, useSp: Boolean = false,
) = TextView(this).apply {
    text = s
    style(style, color, useSp)
    if (lines > 0) { maxLines = lines; ellipsize = TextUtils.TruncateAt.END }
    if (lines == 1) isSingleLine = true
    this.gravity = gravity
}

fun Context.text(s: CharSequence, size: Float, w: W, color: Int, lines: Int = 1, gravity: Int = Gravity.NO_GRAVITY, useSp: Boolean = false) =
    TextView(this).apply {
        text = s; style(size, w, color, useSp)
        if (lines > 0) { maxLines = lines; ellipsize = TextUtils.TruncateAt.END }
        if (lines == 1) isSingleLine = true
        this.gravity = gravity
    }

fun Context.glyph(res: Int, tint: Int) = ImageView(this).apply {
    setImageResource(res)
    imageTintList = ColorStateList.valueOf(tint)
    scaleType = ImageView.ScaleType.FIT_CENTER
}

fun ImageView.tint(c: Int) { imageTintList = ColorStateList.valueOf(c) }

fun Context.spacer(weight: Float = 1f) = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, weight) }

fun Context.space(hDp: Float, wDp: Float = 0f) = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(dpi(wDp), dpi(hDp))
}

fun View.bg(color: Int, radiusDp: Float = 0f): View {
    background = if (radiusDp <= 0f) android.graphics.drawable.ColorDrawable(color) else RoundDrawable(color, dp(radiusDp))
    return this
}

/**
 * App-icon tile: continuous-corner square with a top-to-bottom gradient and a white glyph,
 * the way iOS settings rows and the CarPlay dock show icons.
 */
fun Context.iconTile(glyph: Int, color: Int, sizeDp: Float, glyphFrac: Float = 0.62f, radiusFrac: Float = 0.2237f, glyphTint: Int = Color.WHITE): FrameLayout {
    val top = blend(color, Color.WHITE, 0.18f)
    val f = FrameLayout(this)
    f.background = RoundDrawable(color, dp(sizeDp * radiusFrac), top, color)
    val g = glyph(glyph, glyphTint)
    val gs = dpi(sizeDp * glyphFrac)
    f.addView(g, FrameLayout.LayoutParams(gs, gs, Gravity.CENTER))
    f.layoutParams = ViewGroup.LayoutParams(dpi(sizeDp), dpi(sizeDp))
    return f
}

/** Circle button (CarPlay call buttons, Now-Playing controls). */
fun Context.circleButton(glyph: Int, fill: Int, tint: Int, sizeDp: Float, glyphFrac: Float = 0.46f): FrameLayout {
    val f = FrameLayout(this)
    f.background = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply { paint.color = fill }
    val g = glyph(glyph, tint)
    val gs = dpi(sizeDp * glyphFrac)
    f.addView(g, FrameLayout.LayoutParams(gs, gs, Gravity.CENTER))
    f.layoutParams = ViewGroup.LayoutParams(dpi(sizeDp), dpi(sizeDp))
    return f
}

enum class Press { DIM, SCALE, HIGHLIGHT, NONE }

/**
 * iOS touch feedback: text buttons dim to ~35% instantly and fade back,
 * big buttons shrink slightly, table cells get a grey highlight.
 */
@SuppressLint("ClickableViewAccessibility")
fun View.onTap(mode: Press = Press.DIM, highlight: Int = 0, action: (View) -> Unit) {
    isClickable = true
    setOnClickListener { action(it) }
    var hl: View? = null
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> when (mode) {
                Press.DIM -> { v.animate().cancel(); v.alpha = 0.35f }
                Press.SCALE -> v.animate().scaleX(0.92f).scaleY(0.92f).alpha(0.8f).setDuration(110).setInterpolator(Motion.easeOut).start()
                Press.HIGHLIGHT -> {
                    if (v is FrameLayout && hl == null) {
                        val nv = View(v.context).apply { setBackgroundColor(highlight); alpha = 0f }
                        v.addView(nv, 0, FrameLayout.LayoutParams(MATCH, MATCH))
                        hl = nv
                    }
                    val h = hl
                    if (h != null) { h.animate().cancel(); h.alpha = 1f } else v.setBackgroundColor(highlight)
                }
                Press.NONE -> {}
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> when (mode) {
                Press.DIM -> v.animate().alpha(1f).setDuration(220).setInterpolator(Motion.easeOut).start()
                Press.SCALE -> v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260).setInterpolator(Motion.spring).start()
                Press.HIGHLIGHT -> { val h = hl; if (h != null) h.animate().alpha(0f).setDuration(300).start() else v.setBackgroundColor(Color.TRANSPARENT) }
                Press.NONE -> {}
            }
        }
        false
    }
}

/** Hairline separator. */
fun Context.hairline(color: Int, insetStartDp: Float = 0f) = View(this).apply {
    setBackgroundColor(color)
    layoutParams = LinearLayout.LayoutParams(MATCH, maxOf(1, (resources.displayMetrics.density * 0.5f).toInt())).apply {
        marginStart = dpi(insetStartDp)
    }
}

/** Grey section header in grouped lists (iOS 15+: sentence case, footnote, secondary label). */
fun Context.sectionHeader(pal: Palette, s: String, useSp: Boolean = false) =
    text(s.uppercase(), TStyle.FOOTNOTE, pal.label2, useSp = useSp).apply { pad(16f, 22f, 16f, 7f) }

fun Context.sectionFooter(pal: Palette, s: String, useSp: Boolean = false) =
    text(s, TStyle.FOOTNOTE, pal.label2, lines = 0, useSp = useSp).apply { pad(16f, 7f, 16f, 4f) }

/** Grouped "inset" section container — rounded 10pt card holding rows. */
fun Context.section(pal: Palette): LinearLayout = vstack().apply {
    background = RoundDrawable(pal.card, dp(10f))
    clipRound(dp(10f))
    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { marginStart = dpi(16f); marginEnd = dpi(16f) }
}

/**
 * Standard table row: [icon] title / subtitle ......... value [accessory]
 * Returns the row; separators are added by [addRows].
 */
class Row(ctx: Context, pal: Palette, title: String, subtitle: String? = null, value: String? = null,
          icon: Int = 0, iconColor: Int = 0, val accessory: View? = null, chevron: Boolean = false,
          useSp: Boolean = false, titleColor: Int = pal.label, minHeightDp: Float = 44f) : FrameLayout(ctx) {
    val titleView: TextView
    val subtitleView: TextView?
    val valueView: TextView?
    val hasIcon = icon != 0

    init {
        minimumHeight = dpi(minHeightDp)
        val h = ctx.hstack()
        h.pad(if (hasIcon) 16f else 20f, 0f, 16f, 0f)
        if (hasIcon) {
            val t = ctx.iconTile(icon, iconColor, 29f, 0.66f)
            h.addView(t, LinearLayout.LayoutParams(dpi(29f), dpi(29f)).apply { marginEnd = dpi(15f) })
        }
        val col = ctx.vstack(Gravity.CENTER_VERTICAL)
        titleView = ctx.text(title, TStyle.BODY, titleColor, useSp = useSp)
        col.addView(titleView)
        subtitleView = subtitle?.let { ctx.text(it, TStyle.FOOTNOTE, pal.label2, lines = 2, useSp = useSp).also { s -> col.addView(s, ctx.lp(WRAP, WRAP).apply { topMargin = dpi(2f) }) } }
        col.pad(0f, 11f, 0f, 11f)
        h.addView(col, ctx.lp(0, WRAP, 1f))
        valueView = value?.let { ctx.text(it, TStyle.BODY, pal.label2, useSp = useSp).also { v -> h.addView(v, ctx.lp(WRAP, WRAP).apply { marginStart = dpi(8f) }) } }
        accessory?.let { h.addView(it, ctx.lp(WRAP, WRAP).apply { marginStart = dpi(10f) }) }
        if (chevron) h.addView(ctx.glyph(pl.boss.drive.core.R.drawable.ic_chevron_right, pal.gray3), ctx.lp(dpi(13f), dpi(13f)).apply { marginStart = dpi(8f) })
        addView(h, LayoutParams(MATCH, MATCH))
    }

    fun setValue(v: String) { valueView?.text = v }
}

/** Add rows to a section with inset hairlines between them. */
fun LinearLayout.addRows(pal: Palette, rows: List<View>) {
    rows.forEachIndexed { i, r ->
        if (i > 0) {
            val inset = if (rows[i] is Row && (rows[i] as Row).hasIcon) 60f else 20f
            addView(context.hairline(pal.separator, inset))
        }
        addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
    }
}
