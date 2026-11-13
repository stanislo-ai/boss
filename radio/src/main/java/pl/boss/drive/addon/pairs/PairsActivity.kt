package pl.boss.drive.addon.pairs

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.addon.Addon
import pl.boss.drive.core.addon.AddonActivity
import pl.boss.drive.core.addon.Addons
import pl.boss.drive.core.addon.DrivingLock
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Motion
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.Segmented
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.blend
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.fmtTime
import pl.boss.drive.core.ui.glyph
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.tint
import pl.boss.drive.core.ui.vstack

/** Memory game: flip cards, find pairs. Parking only. */
class PairsActivity : AddonActivity() {
    override val addon: Addon get() = Addons.byPkg("pl.boss.drive.addon.pairs")!!
    private val prefs by lazy { getSharedPreferences("pairs", Context.MODE_PRIVATE) }
    private val sizes = listOf(4 to 3, 4 to 4, 6 to 4)   // columns x rows
    private var level = 0
    private lateinit var board: FrameLayout
    private lateinit var info: TextView
    private val lock by lazy { DrivingLock(this) }

    private val symbols = listOf(
        CR.drawable.ic_music to 0xFFFA2D48, CR.drawable.ic_phone to 0xFF34C759, CR.drawable.ic_car to 0xFF0A84FF, CR.drawable.ic_star to 0xFFFFCC00,
        CR.drawable.ic_sun to 0xFFFF9F0A, CR.drawable.ic_moon to 0xFF5E5CE6, CR.drawable.ic_cloud to 0xFF64D2FF, CR.drawable.ic_message to 0xFF30D158,
        CR.drawable.ic_podcast to 0xFFBF5AF2, CR.drawable.ic_radio to 0xFFFF375F, CR.drawable.ic_fuel to 0xFFFF9500, CR.drawable.ic_wrench to 0xFF8E8E93,
    )

    private class Card(val sym: Int, val view: FrameLayout, val face: ImageView, var open: Boolean = false, var done: Boolean = false)
    private var cards = ArrayList<Card>()
    private var first: Card? = null
    private var busy = false
    private var moves = 0
    private var found = 0
    private var startAt = 0L
    private val tick = object : Runnable { override fun run() { updateInfo(); board.postDelayed(this, 1000) } }

    override fun buildContent(): View {
        level = prefs.getInt("level", 0)
        val root = vstack()
        val seg = Segmented(this, pal, listOf("Łatwy", "Średni", "Trudny"), 15f)
        seg.select(level, false)
        seg.onSelect = { level = it; prefs.edit().putInt("level", it).apply(); newGame() }
        val segWrap = FrameLayout(this).apply { addView(seg, FrameLayout.LayoutParams(dpi(330f), WRAP, Gravity.CENTER_VERTICAL)) }
        root.addView(header("Pary", segWrap))
        info = text("", 17f, W.MEDIUM, pal.label2)
        root.addView(info, lp(WRAP, WRAP).apply { marginStart = dpi(30f) })
        board = FrameLayout(this)
        root.addView(board, lp(MATCH, 0, 1f).apply { setMargins(dpi(24f), dpi(8f), dpi(24f), dpi(16f)) })
        board.post { newGame() }
        return root
    }

    private fun newGame() {
        board.removeAllViews(); cards.clear(); first = null; busy = false; moves = 0; found = 0; startAt = 0
        val (cols, rows) = sizes[level]
        val n = cols * rows / 2
        val deck = (symbols.shuffled().take(n).flatMap { listOf(it, it) }).shuffled()
        val gap = dpi(10f)
        val cw = (board.width - gap * (cols - 1)) / cols
        val ch = (board.height - gap * (rows - 1)) / rows
        val size = minOf(cw, ch)
        val grid = vstack(Gravity.CENTER)
        var idx = 0
        for (r in 0 until rows) {
            val row = hstack()
            for (c in 0 until cols) {
                val (res, color) = deck[idx++]
                val f = FrameLayout(this)
                f.background = back()
                val face = glyph(res, Color.WHITE)
                face.pad(size * 0.06f); face.visibility = View.INVISIBLE
                f.addView(face, FrameLayout.LayoutParams((size * 0.5f).toInt(), (size * 0.5f).toInt(), Gravity.CENTER))
                val card = Card(res, f, face)
                f.tag = color.toInt()
                f.onTap(Press.NONE) { flip(card) }
                cards.add(card)
                row.addView(f, LinearLayout.LayoutParams(size, size).apply { setMargins(gap / 2, gap / 2, gap / 2, gap / 2) })
            }
            grid.addView(row, lp(WRAP, WRAP))
        }
        board.addView(grid, FrameLayout.LayoutParams(MATCH, MATCH))
        updateInfo()
    }

    private fun back() = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(addon.color, addon.color2)).apply { cornerRadius = dp(16f) }

    private fun front(color: Int) = RoundDrawable(color, dp(16f), blend(color, Color.WHITE, 0.15f), color)

    private fun flip(c: Card) {
        if (busy || c.open || c.done) return
        if (startAt == 0L) { startAt = SystemClock.elapsedRealtime(); board.post(tick) }
        turn(c, true)
        val f = first
        if (f == null) { first = c; return }
        first = null; moves++
        if (f.sym == c.sym) {
            f.done = true; c.done = true; found++
            pulse(f.view); pulse(c.view)
            if (found == cards.size / 2) board.postDelayed({ win() }, 600)
        } else {
            busy = true
            board.postDelayed({ turn(f, false); turn(c, false); busy = false }, 800)
        }
        updateInfo()
    }

    /** iOS-ish card flip: squash to 0 width, swap face, expand. */
    private fun turn(c: Card, open: Boolean) {
        c.open = open
        c.view.animate().scaleX(0f).setDuration(120).setInterpolator(Motion.easeInOut).withEndAction {
            c.view.background = if (open) front(c.view.tag as Int) else back()
            c.face.visibility = if (open) View.VISIBLE else View.INVISIBLE
            c.view.animate().scaleX(1f).setDuration(140).setInterpolator(Motion.easeOut).start()
        }.start()
    }

    private fun pulse(v: View) { v.postDelayed({ v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).withEndAction { v.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(Motion.spring).start() }.start() }, 280) }

    private fun elapsed() = if (startAt == 0L) 0L else SystemClock.elapsedRealtime() - startAt

    private fun updateInfo() {
        val best = prefs.getInt("best$level", 0)
        info.text = "Ruchy: $moves   •   Czas: ${fmtTime(elapsed())}" + (if (best > 0) "   •   Rekord: $best ruchów" else "")
    }

    private fun win() {
        board.removeCallbacks(tick)
        val best = prefs.getInt("best$level", 0)
        val record = best == 0 || moves < best
        if (record) prefs.edit().putInt("best$level", moves).apply()
        val o = vstack(Gravity.CENTER)
        o.setBackgroundColor(if (pal.dark) 0xE6000000.toInt() else 0xE6F2F2F7.toInt()); o.isClickable = true
        o.addView(glyph(CR.drawable.ic_star, pal.yellow), lp(dpi(80f), dpi(80f)))
        o.addView(text(if (record) "Nowy rekord!" else "Brawo!", 34f, W.BOLD, pal.label, gravity = Gravity.CENTER), lp(MATCH, WRAP).apply { topMargin = dpi(12f) })
        o.addView(text("$moves ruchów • ${fmtTime(elapsed())}", 20f, W.MEDIUM, pal.label2, gravity = Gravity.CENTER), lp(MATCH, WRAP).apply { topMargin = dpi(6f) })
        o.addView(pill("Jeszcze raz", CR.drawable.ic_gauge_reset, addon.color, Color.WHITE) { newGame() }, lp(WRAP, WRAP).apply { topMargin = dpi(24f) })
        board.addView(o, FrameLayout.LayoutParams(MATCH, MATCH))
        o.alpha = 0f; o.animate().alpha(1f).setDuration(300).start()
    }

    override fun onResume() { super.onResume(); lock.start(); if (startAt > 0) board.post(tick) }
    override fun onPause() { lock.stop(); board.removeCallbacks(tick); super.onPause() }
}

