package pl.boss.drive.core.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.animation.PathInterpolator
import android.widget.TextView
import kotlin.math.exp

/** iOS system colours (UIKit semantic colours), light + dark variants. */
class Palette(val dark: Boolean) {
    private fun c(d: Long, l: Long) = (if (dark) d else l).toInt()

    val groupedBg = c(0xFF000000, 0xFFF2F2F7)
    val bg = c(0xFF000000, 0xFFFFFFFF)
    val card = c(0xFF1C1C1E, 0xFFFFFFFF)
    val card2 = c(0xFF2C2C2E, 0xFFF2F2F7)
    val card3 = c(0xFF3A3A3C, 0xFFE5E5EA)
    val cellPressed = c(0xFF3A3A3C, 0xFFD1D1D6)
    val fill = c(0x5C787880, 0x33787880)
    val fill2 = c(0x52787880, 0x29787880)
    val fill3 = c(0x3D767680, 0x1F767680)
    val fill4 = c(0x2E767680, 0x14747480)
    val label = c(0xFFFFFFFF, 0xFF000000)
    val label2 = c(0x99EBEBF5, 0x993C3C43)
    val label3 = c(0x4DEBEBF5, 0x4D3C3C43)
    val label4 = c(0x29EBEBF5, 0x2E3C3C43)
    val separator = c(0xA6545458, 0x4A3C3C43)
    val opaqueSep = c(0xFF38383A, 0xFFC6C6C8)
    val chrome = c(0xF0161618, 0xF2F9F9F9)       // tab/nav bar material
    val dock = c(0xFF0E0E10, 0xFFE9E9EE)
    val gray = c(0xFF8E8E93, 0xFF8E8E93)
    val gray2 = c(0xFF636366, 0xFFAEAEB2)
    val gray3 = c(0xFF48484A, 0xFFC7C7CC)
    val gray4 = c(0xFF3A3A3C, 0xFFD1D1D6)
    val gray5 = c(0xFF2C2C2E, 0xFFE5E5EA)
    val gray6 = c(0xFF1C1C1E, 0xFFF2F2F7)

    val blue = c(0xFF0A84FF, 0xFF007AFF)
    val green = c(0xFF30D158, 0xFF34C759)
    val red = c(0xFFFF453A, 0xFFFF3B30)
    val orange = c(0xFFFF9F0A, 0xFFFF9500)
    val yellow = c(0xFFFFD60A, 0xFFFFCC00)
    val pink = c(0xFFFF375F, 0xFFFF2D55)
    val purple = c(0xFFBF5AF2, 0xFFAF52DE)
    val teal = c(0xFF40C8E0, 0xFF30B0C7)
    val indigo = c(0xFF5E5CE6, 0xFF5856D6)
    val mint = c(0xFF63E6E2, 0xFF00C7BE)
    val cyan = c(0xFF64D2FF, 0xFF32ADE6)

    /** Switch "off" track. */
    val switchOff = c(0x29787880, 0x29787880)

    companion object {
        val DARK = Palette(true)
        val LIGHT = Palette(false)
        fun of(dark: Boolean) = if (dark) DARK else LIGHT
    }
}

/** Typography. Inter (OFL) with Inter-Display for large sizes, tracked like SF Pro. */
object Fonts {
    lateinit var regular: Typeface; private set
    lateinit var medium: Typeface; private set
    lateinit var semibold: Typeface; private set
    lateinit var bold: Typeface; private set
    lateinit var displayBold: Typeface; private set
    lateinit var displaySemibold: Typeface; private set
    private var loaded = false

    /**
     * Fonts ship once, inside Boss Drive (phone/radio). Add-on apps borrow them from the
     * Boss Drive package by name, so each add-on stays ~100 KB. Falls back to system sans.
     */
    fun init(ctx: Context) {
        if (loaded) return
        val app = ctx.applicationContext
        val sources = ArrayList<Pair<android.content.res.Resources, String>>()
        sources.add(app.resources to app.packageName)
        for (pkg in arrayOf("pl.boss.drive.radio", "pl.boss.drive.phone")) {
            if (pkg == app.packageName) continue
            try { sources.add(app.createPackageContext(pkg, 0).resources to pkg) } catch (_: Throwable) {}
        }
        fun load(name: String, fb: Int): Typeface {
            for ((r, pkg) in sources) {
                val id = r.getIdentifier(name, "font", pkg)
                if (id != 0) try { return r.getFont(id) } catch (_: Throwable) {}
            }
            return Typeface.create(Typeface.SANS_SERIF, fb)
        }
        regular = load("inter_regular", Typeface.NORMAL)
        medium = load("inter_medium", Typeface.NORMAL)
        semibold = load("inter_semibold", Typeface.BOLD)
        bold = load("inter_bold", Typeface.BOLD)
        displayBold = load("interdisplay_bold", Typeface.BOLD)
        displaySemibold = load("interdisplay_semibold", Typeface.BOLD)
        loaded = true
    }

    /** Inter's dynamic metrics formula — gives the tight SF-like look at every size. */
    fun tracking(sizePt: Float): Float = (-0.0223f + 0.185f * exp(-0.1745f * sizePt)) - 0.004f
}

enum class W { REGULAR, MEDIUM, SEMIBOLD, BOLD }

/** iOS text styles (pt sizes from the HIG). */
enum class TStyle(val size: Float, val w: W, val lineMult: Float = 1.2f) {
    LARGE_TITLE(34f, W.BOLD), TITLE1(28f, W.BOLD), TITLE2(22f, W.BOLD), TITLE3(20f, W.SEMIBOLD),
    HEADLINE(17f, W.SEMIBOLD), BODY(17f, W.REGULAR), CALLOUT(16f, W.REGULAR), SUBHEAD(15f, W.REGULAR),
    FOOTNOTE(13f, W.REGULAR), CAPTION1(12f, W.REGULAR), CAPTION2(11f, W.REGULAR)
}

fun typeface(w: W, size: Float): Typeface = if (size >= 20f) {
    when (w) { W.BOLD -> Fonts.displayBold; W.SEMIBOLD -> Fonts.displaySemibold; W.MEDIUM -> Fonts.medium; W.REGULAR -> Fonts.regular }
} else {
    when (w) { W.BOLD -> Fonts.bold; W.SEMIBOLD -> Fonts.semibold; W.MEDIUM -> Fonts.medium; W.REGULAR -> Fonts.regular }
}

/** Apply a text style. Uses dp (not sp) so the car UI never re-flows with system font scale. */
fun TextView.style(size: Float, w: W, color: Int, useSp: Boolean = false) {
    typeface = typeface(w, size)
    setTextSize(if (useSp) TypedValue.COMPLEX_UNIT_SP else TypedValue.COMPLEX_UNIT_DIP, size)
    letterSpacing = Fonts.tracking(size)
    setTextColor(color)
    includeFontPadding = false
}

fun TextView.style(s: TStyle, color: Int, useSp: Boolean = false) = style(s.size, s.w, color, useSp)

object Motion {
    /** UIKit default ease-in-out / navigation push curve / spring-ish decel. */
    val easeInOut = PathInterpolator(0.42f, 0f, 0.58f, 1f)
    val easeOut = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
    val push = PathInterpolator(0.32f, 0.72f, 0f, 1f)
    val spring = PathInterpolator(0.175f, 0.885f, 0.32f, 1.1f)
    const val FAST = 180L
    const val NORMAL = 350L
}

val Context.density get() = resources.displayMetrics.density
fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun android.view.View.dp(v: Float): Float = v * resources.displayMetrics.density
fun android.view.View.dpi(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
