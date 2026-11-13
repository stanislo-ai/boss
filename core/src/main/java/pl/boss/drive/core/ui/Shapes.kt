package pl.boss.drive.core.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.min

/**
 * Apple-style "continuous corner" rounded rectangle (the squircle-ish curve used
 * by iOS for icons, cards and buttons) instead of Android's circular arcs.
 */
object Continuous {
    private const val K = 1.52866483f
    // top-right corner, (distance from right edge, distance from top) in units of radius
    private val CORNER = floatArrayOf(
        // curve 1: cp1, cp2, end
        1.08849323f, 0f, 0.86840689f, 0f, 0.66993427f, 0.06549600f,
        // line
        0.63149399f, 0.07491100f,
        // curve 2
        0.37282392f, 0.16905899f, 0.16906013f, 0.37282401f, 0.07491176f, 0.63149399f,
        // curve 3
        0f, 0.86840701f, 0f, 1.08849299f, 0f, K,
    )

    fun path(out: Path, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        out.reset()
        val w = r - l; val h = b - t
        val rad = min(radius, min(w, h) / 2f / K)
        if (rad <= 0f) { out.addRect(l, t, r, b, Path.Direction.CW); return }
        // mapping per corner: returns x,y for (a = along-edge distance, c = cross distance)
        fun tr(a: Float, c: Float) = floatArrayOf(r - a * rad, t + c * rad)
        fun br(a: Float, c: Float) = floatArrayOf(r - c * rad, b - a * rad)
        fun bl(a: Float, c: Float) = floatArrayOf(l + a * rad, b - c * rad)
        fun tl(a: Float, c: Float) = floatArrayOf(l + c * rad, t + a * rad)
        val corners = arrayOf(::tr, ::br, ::bl, ::tl)
        // start on top edge just right of the top-left corner
        out.moveTo(l + K * rad, t)
        for (m in corners) {
            val s = m(K, 0f)
            out.lineTo(s[0], s[1])
            val c = CORNER
            val p1 = m(c[0], c[1]); val p2 = m(c[2], c[3]); val p3 = m(c[4], c[5])
            out.cubicTo(p1[0], p1[1], p2[0], p2[1], p3[0], p3[1])
            val p4 = m(c[6], c[7]); out.lineTo(p4[0], p4[1])
            val q1 = m(c[8], c[9]); val q2 = m(c[10], c[11]); val q3 = m(c[12], c[13])
            out.cubicTo(q1[0], q1[1], q2[0], q2[1], q3[0], q3[1])
            val z1 = m(c[14], c[15]); val z2 = m(c[16], c[17]); val z3 = m(c[18], c[19])
            out.cubicTo(z1[0], z1[1], z2[0], z2[1], z3[0], z3[1])
        }
        out.close()
    }
}

/** Filled continuous-corner rectangle, optionally with a vertical gradient and hairline stroke. */
class RoundDrawable(
    color: Int,
    var radius: Float,
    private val gradTop: Int? = null,
    private val gradBottom: Int? = null,
    strokeColor: Int = 0,
    strokeWidth: Float = 0f,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; this.color = strokeColor; this.strokeWidth = strokeWidth
    }
    private val path = Path()
    var color: Int
        get() = paint.color
        set(v) { paint.color = v; invalidateSelf() }

    override fun onBoundsChange(b: Rect) {
        Continuous.path(path, b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), radius)
        if (gradTop != null && gradBottom != null) {
            paint.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(), gradTop, gradBottom, Shader.TileMode.CLAMP)
        }
    }

    fun setRadiusPx(r: Float) { radius = r; onBoundsChange(bounds); invalidateSelf() }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
        if (stroke.strokeWidth > 0f && stroke.color != 0) canvas.drawPath(path, stroke)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Clip a view (e.g. album art) to rounded corners — hardware accelerated outline clip. */
fun View.clipRound(radiusPx: Float) {
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
        }
    }
    clipToOutline = true
}

fun View.clipOval() {
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) { outline.setOval(0, 0, view.width, view.height) }
    }
    clipToOutline = true
}
