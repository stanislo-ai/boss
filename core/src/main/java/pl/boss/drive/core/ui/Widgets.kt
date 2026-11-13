package pl.boss.drive.core.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun blend(a: Int, b: Int, t: Float): Int {
    val tt = t.coerceIn(0f, 1f)
    return Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * tt).roundToInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * tt).roundToInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * tt).roundToInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * tt).roundToInt(),
    )
}

fun withAlpha(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((Color.alpha(c) * a).roundToInt().coerceIn(0, 255) shl 24)

/** iOS UISwitch: 51x31pt, green track, white knob with soft shadow, knob stretches while pressed. */
class IosSwitch(ctx: Context, private val pal: Palette) : View(ctx) {
    var isOn = false; private set
    var onChange: ((Boolean) -> Unit)? = null
    private var pos = 0f
    private var press = 0f
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26000000 }
    private val rect = RectF()
    private var anim: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_NONE, null)
        isClickable = true
        contentDescription = "switch"
    }

    fun set(on: Boolean, animate: Boolean = true) {
        if (on == isOn && (pos == if (on) 1f else 0f)) return
        isOn = on
        anim?.cancel()
        if (!animate) { pos = if (on) 1f else 0f; invalidate(); return }
        anim = ValueAnimator.ofFloat(pos, if (on) 1f else 0f).apply {
            duration = 260; interpolator = Motion.spring
            addUpdateListener { pos = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(dpi(51f), dpi(31f))

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { animPress(1f); return true }
            MotionEvent.ACTION_UP -> {
                animPress(0f); set(!isOn); onChange?.invoke(isOn); performClick()
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }
            MotionEvent.ACTION_CANCEL -> animPress(0f)
        }
        return true
    }

    private fun animPress(to: Float) {
        ValueAnimator.ofFloat(press, to).apply {
            duration = 150; addUpdateListener { press = it.animatedValue as Float; invalidate() }; start()
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        track.color = blend(pal.switchOff, pal.green, pos)
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, h / 2, h / 2, track)
        val pad = dp(2f)
        val kh = h - pad * 2
        val kw = kh + dp(7f) * press
        val x = pad + (w - pad * 2 - kw) * pos
        rect.set(x, pad + dp(1.5f), x + kw, pad + kh + dp(1.5f))
        c.drawRoundRect(rect, kh / 2, kh / 2, shadow)
        rect.offset(0f, -dp(1.5f))
        c.drawRoundRect(rect, kh / 2, kh / 2, knob)
    }
}

/** iOS UISegmentedControl (iOS 13+ look): rounded track, raised sliding thumb. */
class Segmented(ctx: Context, private val pal: Palette, private val items: List<String>, private val sizeDp: Float = 13f) : View(ctx) {
    var selected = 0; private set
    var onSelect: ((Int) -> Unit)? = null
    private var thumbX = 0f
    private val trackP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.fill3 }
    private val thumbP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (pal.dark) 0xFF636366.toInt() else Color.WHITE }
    private val shadowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14000000 }
    private val divP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.fill; strokeWidth = dp(1f) }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.label; textSize = dp(sizeDp); textAlign = Paint.Align.CENTER; letterSpacing = Fonts.tracking(sizeDp)
    }
    private val rect = RectF()

    fun select(i: Int, animate: Boolean = true) {
        selected = i
        val seg = segW()
        if (!animate || width == 0) { thumbX = i * seg; invalidate(); return }
        ValueAnimator.ofFloat(thumbX, i * seg).apply {
            duration = 280; interpolator = Motion.push
            addUpdateListener { thumbX = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun segW() = if (items.isEmpty()) 0f else width.toFloat() / items.size

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { thumbX = selected * segW() }

    override fun onMeasure(wm: Int, hm: Int) {
        val w = MeasureSpec.getSize(wm)
        setMeasuredDimension(w, dpi(if (sizeDp > 15) 44f else 32f))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val i = (e.x / segW()).toInt().coerceIn(0, items.size - 1)
            if (i != selected) { select(i); onSelect?.invoke(i) }
            performClick()
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val r = dp(if (sizeDp > 15) 11f else 8.9f)
        rect.set(0f, 0f, w, h); c.drawRoundRect(rect, r, r, trackP)
        val seg = segW()
        for (i in 1 until items.size) {
            val x = i * seg
            if (abs(x - thumbX) < seg * 0.6f || abs(x - (thumbX + seg)) < seg * 0.6f) continue
            c.drawLine(x, h * 0.28f, x, h * 0.72f, divP)
        }
        val p = dp(2f)
        rect.set(thumbX + p, p + dp(1f), thumbX + seg - p, h - p + dp(1f)); c.drawRoundRect(rect, r - p, r - p, shadowP)
        rect.offset(0f, -dp(1f)); c.drawRoundRect(rect, r - p, r - p, thumbP)
        val fm = txt.fontMetrics
        val ty = h / 2 - (fm.ascent + fm.descent) / 2
        for ((i, s) in items.withIndex()) {
            txt.typeface = if (i == selected) Fonts.semibold else Fonts.medium
            c.drawText(s, seg * i + seg / 2, ty, txt)
        }
    }
}

/**
 * Music-app style scrubber: thin capsule that grows while dragging, elapsed/remaining
 * labels underneath. Position is in ms.
 */
class ScrubBar(ctx: Context, private val tint: Int = Color.WHITE, private val showTimes: Boolean = true) : View(ctx) {
    var duration = 0L
    var position = 0L
    var onSeek: ((Long) -> Unit)? = null
    var enabledSeek = true
    private var dragging = false
    private var dragPos = 0L
    private var grow = 0f
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(tint, 0.22f) }
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(tint, 0.78f) }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(tint, 0.55f); textSize = dp(12f); typeface = Fonts.semibold }
    private val rect = RectF()
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    fun update(pos: Long, dur: Long) { if (!dragging) { position = pos; duration = dur; invalidate() } }

    override fun onMeasure(wm: Int, hm: Int) = setMeasuredDimension(MeasureSpec.getSize(wm), dpi(if (showTimes) 38f else 18f))

    private var downX = 0f
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!enabledSeek || duration <= 0) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; dragging = true; dragPos = position; parent?.requestDisallowInterceptTouchEvent(true); animGrow(1f) }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.x - downX) > slop || dragging) {
                    dragPos = ((e.x / width).coerceIn(0f, 1f) * duration).toLong(); invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                dragPos = ((e.x / width).coerceIn(0f, 1f) * duration).toLong()
                position = dragPos; dragging = false; animGrow(0f); onSeek?.invoke(dragPos)
            }
            MotionEvent.ACTION_CANCEL -> { dragging = false; animGrow(0f) }
        }
        return true
    }

    private fun animGrow(to: Float) = ValueAnimator.ofFloat(grow, to).apply {
        duration = 200; interpolator = Motion.easeOut; addUpdateListener { grow = it.animatedValue as Float; invalidate() }; start()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val th = dp(6f) + dp(5f) * grow
        val cy = dp(9f)
        val pos = if (dragging) dragPos else position
        val f = if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f
        rect.set(0f, cy - th / 2, w, cy + th / 2)
        c.drawRoundRect(rect, th / 2, th / 2, bg)
        if (f > 0f) {
            c.save(); c.clipRect(0f, 0f, w * f, height.toFloat())
            fg.color = withAlpha(tint, 0.78f + 0.22f * grow)
            c.drawRoundRect(rect, th / 2, th / 2, fg); c.restore()
        }
        if (showTimes) {
            val y = cy + th / 2 + dp(15f)
            txt.textAlign = Paint.Align.LEFT; c.drawText(fmtTime(pos), 0f, y, txt)
            txt.textAlign = Paint.Align.RIGHT
            c.drawText(if (duration > 0) "-" + fmtTime(max(0, duration - pos)) else "--:--", w, y, txt)
        }
    }
}

fun fmtTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** UIActivityIndicatorView: 8 fading spokes. Only animates while attached + visible. */
class Spinner(ctx: Context, private val color: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; this.color = color }
    private var step = 0
    private val tick = object : Runnable { override fun run() { step = (step + 1) % 8; invalidate(); postDelayed(this, 100) } }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); post(tick) }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        val s = min(width, height) / 2f
        p.strokeWidth = s * 0.2f
        for (i in 0 until 8) {
            val a = ((i - step + 8) % 8)
            p.alpha = (255 * (1f - a / 9f)).toInt()
            c.save(); c.rotate(i * 45f, width / 2f, height / 2f)
            c.drawLine(width / 2f, height / 2f - s * 0.5f, width / 2f, height / 2f - s * 0.92f, p)
            c.restore()
        }
    }
}

/** Animated "now playing" equaliser bars (like the Music app list indicator). */
class EqBars(ctx: Context, color: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val r = RectF()
    var playing = true
        set(v) { field = v; invalidate() }
    private var t = 0f
    private val tick = object : Runnable { override fun run() { if (playing) { t += 0.16f; invalidate() }; postDelayed(this, 60) } }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); post(tick) }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        val n = 4; val gap = width * 0.12f; val bw = (width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val ph = if (playing) (0.35f + 0.65f * abs(kotlin.math.sin(t * (1.1f + i * 0.37f) + i * 1.3f))) else 0.2f
            val h = height * ph
            r.set(i * (bw + gap), height - h, i * (bw + gap) + bw, height.toFloat())
            c.drawRoundRect(r, bw / 2, bw / 2, p)
        }
    }
}

/** Phone status glyphs drawn in code (crisp at any size): signal bars and battery. */
class SignalBars(ctx: Context, private val color: Int) : View(ctx) {
    var level = 0; set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    override fun onDraw(c: Canvas) {
        val n = 4; val gap = width * 0.1f; val bw = (width - gap * (n - 1)) / n
        for (i in 0 until n) {
            p.color = if (i < level) color else withAlpha(color, 0.3f)
            val h = height * (0.4f + 0.2f * i)
            r.set(i * (bw + gap), height - h, i * (bw + gap) + bw, height.toFloat())
            c.drawRoundRect(r, bw * 0.35f, bw * 0.35f, p)
        }
    }
}

class BatteryView(ctx: Context, private val color: Int, private val green: Int, private val red: Int) : View(ctx) {
    var level = -1; set(v) { field = v; invalidate() }
    var charging = false; set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val bolt = Path()
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val capW = w * 0.07f
        val bw = w - capW - w * 0.03f
        p.style = Paint.Style.STROKE; p.strokeWidth = h * 0.09f; p.color = withAlpha(color, 0.4f)
        val sw = p.strokeWidth / 2
        r.set(sw, sw, bw - sw, h - sw); c.drawRoundRect(r, h * 0.28f, h * 0.28f, p)
        p.style = Paint.Style.FILL
        r.set(bw + w * 0.03f, h * 0.33f, w, h * 0.67f); c.drawRoundRect(r, capW, capW, p)
        if (level >= 0) {
            p.color = when { charging -> green; level <= 20 -> red; else -> color }
            val ins = h * 0.18f
            r.set(ins, ins, ins + (bw - ins * 2) * (level / 100f).coerceIn(0.04f, 1f), h - ins)
            c.drawRoundRect(r, h * 0.16f, h * 0.16f, p)
        }
        if (charging) {
            bolt.reset()
            val cx = bw / 2; val cy = h / 2
            bolt.moveTo(cx + h * 0.08f, cy - h * 0.42f); bolt.lineTo(cx - h * 0.22f, cy + h * 0.06f); bolt.lineTo(cx, cy + h * 0.06f)
            bolt.lineTo(cx - h * 0.08f, cy + h * 0.42f); bolt.lineTo(cx + h * 0.22f, cy - h * 0.06f); bolt.lineTo(cx, cy - h * 0.06f); bolt.close()
            p.color = Color.WHITE; c.drawPath(bolt, p)
        }
    }
}
