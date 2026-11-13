package pl.boss.drive.addon.tictactoe

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.addon.Addon
import pl.boss.drive.core.addon.AddonActivity
import pl.boss.drive.core.addon.Addons
import pl.boss.drive.core.addon.DrivingLock
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Motion
import pl.boss.drive.core.ui.Segmented
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack

/** Noughts and crosses: vs computer (easy / medium / unbeatable minimax) or two players. Parking only. */
class TicTacToeActivity : AddonActivity() {
    override val addon: Addon get() = Addons.byPkg("pl.boss.drive.addon.tictactoe")!!
    private val prefs by lazy { getSharedPreferences("ttt", Context.MODE_PRIVATE) }
    private val cells = IntArray(9)          // 0 empty, 1 X (you / player 1), 2 O (computer / player 2)
    private var turn = 1
    private var starter = 1
    private var over = false
    private var mode = 1                     // 0 easy, 1 medium, 2 hard, 3 two players
    private lateinit var boardView: Board
    private lateinit var status: TextView
    private lateinit var score: TextView
    private val lock by lazy { DrivingLock(this) }
    private val lines = arrayOf(intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8), intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8), intArrayOf(0, 4, 8), intArrayOf(2, 4, 6))

    override fun buildContent(): View {
        mode = prefs.getInt("mode", 1)
        val root = vstack()
        val seg = Segmented(this, pal, listOf("Łatwy", "Średni", "Trudny", "We dwoje"), 15f)
        seg.select(mode, false)
        seg.onSelect = { mode = it; prefs.edit().putInt("mode", it).apply(); newGame(); renderScore() }
        root.addView(header("Kółko i krzyżyk", FrameLayout(this).apply { addView(seg, FrameLayout.LayoutParams(dpi(420f), WRAP, Gravity.CENTER_VERTICAL)) }))
        val body = hstack(Gravity.CENTER)
        boardView = Board(this)
        body.addView(boardView, lp(dpi(440f), dpi(440f)))
        val side = vstack()
        status = text("", 30f, W.BOLD, pal.label, lines = 2)
        side.addView(status)
        score = text("", 19f, W.MEDIUM, pal.label2, lines = 3)
        side.addView(score, lp(MATCH, WRAP).apply { topMargin = dpi(12f) })
        side.addView(pill("Nowa gra", CR.drawable.ic_gauge_reset, addon.color, android.graphics.Color.WHITE) { newGame() }, lp(WRAP, WRAP).apply { topMargin = dpi(26f) })
        body.addView(side, lp(dpi(330f), WRAP).apply { marginStart = dpi(50f) })
        root.addView(body, lp(MATCH, 0, 1f))
        newGame(); renderScore()
        return root
    }

    private fun newGame() {
        cells.fill(0); over = false; boardView.win = null
        starter = 3 - starter; turn = starter
        boardView.reset()
        updateStatus()
        if (mode < 3 && turn == 2) boardView.postDelayed({ aiMove() }, 500)
    }

    fun tap(i: Int) {
        if (over || cells[i] != 0) return
        if (mode < 3 && turn == 2) return
        place(i)
        if (!over && mode < 3) boardView.postDelayed({ aiMove() }, 450)
    }

    private fun place(i: Int) {
        cells[i] = turn
        boardView.drawIn(i)
        val w = winner(cells)
        when {
            w != null -> { over = true; boardView.win = w.second; record(w.first) }
            cells.none { it == 0 } -> { over = true; record(0) }
            else -> turn = 3 - turn
        }
        updateStatus()
    }

    private fun aiMove() {
        if (over || turn != 2) return
        val free = (0..8).filter { cells[it] == 0 }
        val smart = when (mode) { 0 -> 0.25; 1 -> 0.7; else -> 1.0 }
        val i = if (Math.random() < smart) best() else (immediate(2) ?: immediate(1) ?: free.random())
        place(i)
    }

    private fun immediate(p: Int): Int? = (0..8).firstOrNull { i -> cells[i] == 0 && run { cells[i] = p; val w = winner(cells)?.first == p; cells[i] = 0; w } }

    private fun best(): Int {
        var bestScore = Int.MIN_VALUE; var move = (0..8).first { cells[it] == 0 }
        for (i in 0..8) if (cells[i] == 0) {
            cells[i] = 2; val s = minimax(false, 0); cells[i] = 0
            if (s > bestScore) { bestScore = s; move = i }
        }
        return move
    }

    private fun minimax(aiTurn: Boolean, depth: Int): Int {
        val w = winner(cells)?.first
        if (w == 2) return 10 - depth
        if (w == 1) return depth - 10
        if (cells.none { it == 0 }) return 0
        var b = if (aiTurn) Int.MIN_VALUE else Int.MAX_VALUE
        for (i in 0..8) if (cells[i] == 0) {
            cells[i] = if (aiTurn) 2 else 1
            val s = minimax(!aiTurn, depth + 1)
            cells[i] = 0
            b = if (aiTurn) maxOf(b, s) else minOf(b, s)
        }
        return b
    }

    private fun winner(c: IntArray): Pair<Int, IntArray>? = lines.firstOrNull { l -> c[l[0]] != 0 && c[l[0]] == c[l[1]] && c[l[1]] == c[l[2]] }?.let { c[it[0]] to it }

    private fun key(k: String) = "m$mode$k"
    private fun record(w: Int) {
        val k = when (w) { 1 -> "x"; 2 -> "o"; else -> "d" }
        prefs.edit().putInt(key(k), prefs.getInt(key(k), 0) + 1).apply()
        renderScore()
    }

    private fun renderScore() {
        val x = prefs.getInt(key("x"), 0); val o = prefs.getInt(key("o"), 0); val d = prefs.getInt(key("d"), 0)
        score.text = if (mode == 3) "Krzyżyk: $x\nKółko: $o\nRemisy: $d" else "Ty: $x\nKomputer: $o\nRemisy: $d"
    }

    private fun updateStatus() {
        val w = winner(cells)?.first
        status.text = when {
            w == 1 -> if (mode == 3) "Wygrywa krzyżyk!" else "Wygrałeś!"
            w == 2 -> if (mode == 3) "Wygrywa kółko!" else "Wygrywa komputer"
            over -> "Remis"
            mode == 3 -> if (turn == 1) "Ruch: krzyżyk" else "Ruch: kółko"
            else -> if (turn == 1) "Twój ruch" else "Komputer myśli…"
        }
        status.setTextColor(when { w == 1 -> pal.blue; w == 2 -> pal.orange; else -> pal.label })
    }

    override fun onResume() { super.onResume(); lock.start() }
    override fun onPause() { lock.stop(); super.onPause() }

    /** Board with animated strokes: X in blue, O in orange, winning line highlighted. */
    inner class Board(ctx: Context) : View(ctx) {
        var win: IntArray? = null
            set(v) { field = v; invalidate() }
        private val progress = FloatArray(9)
        private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = pal.fill }
        private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val hl = Paint(Paint.ANTI_ALIAS_FLAG)

        fun reset() { progress.fill(0f); invalidate() }

        fun drawIn(i: Int) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 260; interpolator = Motion.easeOut
                addUpdateListener { progress[i] = it.animatedValue as Float; invalidate() }; start()
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_UP) {
                val c = (e.x / (width / 3f)).toInt().coerceIn(0, 2); val r = (e.y / (height / 3f)).toInt().coerceIn(0, 2)
                tap(r * 3 + c)
            }
            return true
        }

        override fun onDraw(cv: Canvas) {
            val s = width / 3f
            win?.forEach { i ->
                hl.color = if (cells[i] == 1) (pal.blue and 0x00FFFFFF) or 0x33000000 else (pal.orange and 0x00FFFFFF) or 0x33000000
                cv.drawRoundRect((i % 3) * s + dp(6f), (i / 3) * s + dp(6f), (i % 3 + 1) * s - dp(6f), (i / 3 + 1) * s - dp(6f), dp(18f), dp(18f), hl)
            }
            grid.strokeWidth = dp(5f)
            for (k in 1..2) {
                cv.drawLine(k * s, dp(10f), k * s, height - dp(10f), grid)
                cv.drawLine(dp(10f), k * s, width - dp(10f), k * s, grid)
            }
            pen.strokeWidth = s * 0.11f
            for (i in 0..8) {
                val cx = (i % 3) * s + s / 2; val cy = (i / 3) * s + s / 2; val r = s * 0.28f; val p = progress[i]
                when (cells[i]) {
                    1 -> {
                        pen.color = pal.blue
                        val a = (p * 2).coerceAtMost(1f); val b = ((p - 0.5f) * 2).coerceIn(0f, 1f)
                        cv.drawLine(cx - r, cy - r, cx - r + 2 * r * a, cy - r + 2 * r * a, pen)
                        if (b > 0) cv.drawLine(cx + r, cy - r, cx + r - 2 * r * b, cy - r + 2 * r * b, pen)
                    }
                    2 -> { pen.color = pal.orange; cv.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, 360f * p, false, pen) }
                }
            }
        }
    }
}
