package pl.boss.drive.radio.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Motion
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.circleButton
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.flp
import pl.boss.drive.core.ui.fmtTime
import pl.boss.drive.core.ui.hairline
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.iconTile
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack
import pl.boss.drive.radio.Hub
import pl.boss.drive.radio.MainActivity
import pl.boss.drive.radio.Message
import pl.boss.drive.radio.Store
import pl.boss.drive.radio.Tab

/** UIAlertController (.alert): dimmed backdrop, 300pt card, stacked or side-by-side buttons. */
@SuppressLint("ViewConstructor")
class IosAlert(private val a: MainActivity, title: String, message: String, buttons: List<Pair<String, Int>>, private val onPick: (Int) -> Unit) : FrameLayout(a) {
    private val card: LinearLayout

    init {
        setBackgroundColor(0x66000000)
        isClickable = true
        card = a.vstack(Gravity.CENTER_HORIZONTAL).apply {
            background = RoundDrawable(if (a.pal.dark) 0xFC2C2C2E.toInt() else 0xFCF2F2F2.toInt(), dp(16f))
            elevation = dp(24f)
        }
        val body = a.vstack(Gravity.CENTER_HORIZONTAL).apply { pad(20f, 22f, 20f, 20f) }
        body.addView(a.text(title, 19f, W.SEMIBOLD, a.pal.label, lines = 2, gravity = Gravity.CENTER), a.lp(MATCH, WRAP))
        if (message.isNotEmpty()) body.addView(a.text(message, 15f, W.REGULAR, a.pal.label, lines = 6, gravity = Gravity.CENTER), a.lp(MATCH, WRAP).apply { topMargin = dpi(6f) })
        card.addView(body, a.lp(MATCH, WRAP))
        card.addView(a.hairline(a.pal.separator))
        val side = buttons.size == 2
        val row = if (side) a.hstack() else a.vstack()
        buttons.forEachIndexed { i, (label, kind) ->
            if (i > 0) row.addView(View(a).apply { setBackgroundColor(a.pal.separator) }, if (side) a.lp(maxOf(1, dpi(0.5f)), MATCH) else a.lp(MATCH, maxOf(1, dpi(0.5f))))
            val color = if (kind == 2) a.pal.red else a.pal.blue
            val b = a.text(label, 19f, if (kind == 1) W.SEMIBOLD else W.REGULAR, color, gravity = Gravity.CENTER)
            b.minHeight = dpi(56f)
            b.onTap(Press.HIGHLIGHT, a.pal.cellPressed) { dismiss(); onPick(kind) }
            row.addView(b, if (side) a.lp(0, dpi(56f), 1f) else a.lp(MATCH, dpi(56f)))
        }
        card.addView(row, a.lp(MATCH, WRAP))
        addView(card, FrameLayout.LayoutParams(dpi(340f), WRAP, Gravity.CENTER))
    }

    fun show() {
        a.showOverlay(this)
        alpha = 0f; animate().alpha(1f).setDuration(200).start()
        card.scaleX = 1.15f; card.scaleY = 1.15f
        card.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(Motion.spring).start()
    }

    fun dismiss() { animate().alpha(0f).setDuration(180).withEndAction { a.removeOverlay(this) }.start() }
}

/** Notification banner sliding from the top (iOS 16 style). */
object Banner {
    private var currentView: View? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show(a: MainActivity, m: Message) {
        currentView?.let { a.removeOverlay(it) }
        val pal = a.pal
        val card = a.vstack()
        card.background = RoundDrawable(if (pal.dark) 0xF72C2C2E.toInt() else 0xF7FFFFFF.toInt(), a.dp(22f))
        card.elevation = a.dp(18f)
        card.pad(16f, 14f, 16f, 14f)
        val top = a.hstack()
        top.addView(a.iconTile(CR.drawable.ic_message, 0xFF2FD158.toInt(), 40f, 0.58f), a.lp(a.dpi(40f), a.dpi(40f)).apply { marginEnd = a.dpi(14f) })
        val col = a.vstack()
        val head = a.hstack()
        head.addView(a.text(m.appName.uppercase(), 13f, W.MEDIUM, pal.label2), a.lp(0, WRAP, 1f))
        head.addView(a.text("teraz", 13f, W.REGULAR, pal.label2))
        col.addView(head)
        col.addView(a.text(m.title, 17f, W.SEMIBOLD, pal.label), a.lp(MATCH, WRAP).apply { topMargin = a.dpi(2f) })
        col.addView(a.text(m.text, 16f, W.REGULAR, pal.label, lines = 2), a.lp(MATCH, WRAP).apply { topMargin = a.dpi(1f) })
        top.addView(col, a.lp(0, WRAP, 1f))
        card.addView(top)
        val actions = a.hstack()
        fun act(label: String, glyph: Int, fn: () -> Unit) = actions.addView(
            a.pillButton(label, glyph, pal.fill3, pal.label) { fn(); hide(a) },
            a.lp(0, a.dpi(46f), 1f).apply { marginStart = a.dpi(5f); marginEnd = a.dpi(5f) })
        act("Odczytaj", CR.drawable.ic_speaker) { Hub.message(m.key, "read") }
        if (m.canReply) act("Odpowiedz", CR.drawable.ic_reply) { a.select(Tab.MESSAGES); a.push(MessageDetail(a, m)) }
        card.addView(actions, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(12f) })

        val lp = a.flp(a.dpi(560f), WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = a.dpi(12f) }
        a.showOverlay(card, lp)
        currentView = card
        card.translationY = -a.dp(220f)
        card.animate().translationY(0f).setDuration(450).setInterpolator(Motion.spring).start()
        var downY = 0f
        card.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> downY = e.rawY
                MotionEvent.ACTION_MOVE -> v.translationY = (e.rawY - downY).coerceAtMost(0f)
                MotionEvent.ACTION_UP -> if (e.rawY - downY < -a.dp(30f)) hide(a) else if (kotlin.math.abs(e.rawY - downY) < a.dp(8f)) {
                    hide(a); a.select(Tab.MESSAGES); a.push(MessageDetail(a, m))
                } else v.animate().translationY(0f).setDuration(200).start()
            }
            true
        }
        card.postDelayed({ if (currentView === card) hide(a) }, 7000)
    }

    fun hide(a: MainActivity) {
        val v = currentView ?: return
        currentView = null
        v.animate().translationY(-a.dp(240f)).setDuration(300).setInterpolator(Motion.easeInOut).withEndAction { a.removeOverlay(v) }.start()
    }
}

/** Full-screen call UI (CarPlay Phone): avatar, name, state/timer, big round actions. */
@SuppressLint("ViewConstructor")
class CallOverlay(private val a: MainActivity) : FrameLayout(a) {
    private val avatar = Avatar(a)
    private val name: TextView
    private val status: TextView
    private val actions = a.hstack(Gravity.CENTER)
    private var lastState = ""
    private val tick = object : Runnable {
        override fun run() { updateStatus(); postDelayed(this, 1000) }
    }

    init {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF1F2B3A.toInt(), 0xFF0B0E13.toInt()))
        isClickable = true
        val col = a.vstack(Gravity.CENTER_HORIZONTAL)
        col.addView(avatar, a.lp(a.dpi(124f), a.dpi(124f)))
        name = a.text("", 34f, W.BOLD, Color.WHITE, lines = 1, gravity = Gravity.CENTER)
        col.addView(name, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(18f) })
        status = a.text("", 20f, W.REGULAR, 0x99EBEBF5.toInt(), gravity = Gravity.CENTER)
        col.addView(status, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(6f) })
        col.addView(actions, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(46f) })
        addView(col, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
    }

    private fun action(glyph: Int, fill: Int, label: String, rot: Float = 0f, fn: () -> Unit): View {
        val v = a.vstack(Gravity.CENTER_HORIZONTAL)
        val b = a.circleButton(glyph, fill, Color.WHITE, 84f, 0.44f)
        b.getChildAt(0).rotation = rot
        v.addView(b)
        v.addView(a.text(label, 15f, W.MEDIUM, Color.WHITE, gravity = Gravity.CENTER), a.lp(WRAP, WRAP).apply { topMargin = a.dpi(10f) })
        b.onTap(Press.SCALE) { fn() }
        return v
    }

    fun update() {
        val c = Store.call
        val display = c.name.ifEmpty { c.number.ifEmpty { "Nieznany" } }
        name.text = display
        avatar.set(c.name, Store.photos.get(c.photo))
        if (c.state != lastState) {
            lastState = c.state
            actions.removeAllViews()
            val gap = a.dpi(48f)
            if (c.state == "ringing") {
                actions.addView(action(CR.drawable.ic_phone_down, 0xFFFF3B30.toInt(), "Odrzuć") { Hub.call("reject") }, a.lp(WRAP, WRAP).apply { marginEnd = gap })
                actions.addView(action(CR.drawable.ic_message, 0x52787880, "Prowadzę") {
                    Hub.call("rejectSms", c.number, Hub.prefs.getString("smsReply", DEFAULT_SMS) ?: DEFAULT_SMS)
                    a.hud("Wysłano SMS", CR.drawable.ic_message)
                }, a.lp(WRAP, WRAP).apply { marginEnd = gap })
                actions.addView(action(CR.drawable.ic_phone, 0xFF34C759.toInt(), "Odbierz") { Hub.call("accept") })
            } else {
                actions.addView(action(CR.drawable.ic_phone_down, 0xFFFF3B30.toInt(), "Zakończ") { Hub.call("end") })
            }
        }
        updateStatus()
    }

    private fun updateStatus() {
        val c = Store.call
        status.text = when (c.state) {
            "ringing" -> if (c.name.isNotEmpty() && c.number.isNotEmpty()) "${c.number} • połączenie przychodzące" else "Połączenie przychodzące"
            "dialing" -> "Łączenie…"
            else -> fmtTime(SystemClock.elapsedRealtime() - Store.callStartedAt)
        }
    }

    fun enter() {
        translationY = a.dp(60f); alpha = 0f
        animate().translationY(0f).alpha(1f).setDuration(380).setInterpolator(Motion.push).start()
        post(tick)
    }

    fun exit(done: () -> Unit) {
        removeCallbacks(tick)
        animate().alpha(0f).translationY(a.dp(40f)).setDuration(260).withEndAction(done).start()
    }

    companion object { const val DEFAULT_SMS = "Prowadzę samochód, oddzwonię niedługo." }
}
