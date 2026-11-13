package pl.boss.drive.radio.ui

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.hairline
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack
import pl.boss.drive.radio.Hub
import pl.boss.drive.radio.MainActivity
import pl.boss.drive.radio.Message
import pl.boss.drive.radio.Store
import pl.boss.drive.radio.Topic

class MessageRow(val a: MainActivity) : FrameLayout(a) {
    val avatar = Avatar(a)
    val title: TextView = a.text("", 19f, W.SEMIBOLD, a.pal.label)
    val time: TextView = a.text("", 15f, W.REGULAR, a.pal.label2)
    val body: TextView = a.text("", 16f, W.REGULAR, a.pal.label2, lines = 2)
    val app: TextView = a.text("", 13f, W.MEDIUM, a.pal.label3)
    var onClick: (() -> Unit)? = null

    init {
        val h = a.hstack(Gravity.TOP)
        h.pad(20f, 14f, 24f, 14f)
        h.addView(avatar, a.lp(a.dpi(50f), a.dpi(50f)).apply { marginEnd = a.dpi(16f) })
        val col = a.vstack()
        val head = a.hstack()
        head.addView(title, a.lp(0, WRAP, 1f)); head.addView(time)
        col.addView(head, a.lp(MATCH, WRAP))
        col.addView(body, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(3f) })
        col.addView(app, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(4f) })
        h.addView(col, a.lp(0, WRAP, 1f))
        addView(h, LayoutParams(MATCH, WRAP))
        addView(a.hairline(a.pal.separator), LayoutParams(MATCH, maxOf(1, a.dpi(0.5f)), Gravity.BOTTOM).apply { marginStart = a.dpi(86f) })
        onTap(Press.HIGHLIGHT, a.pal.cellPressed) { onClick?.invoke() }
    }
}

class MessagesScreen(a: MainActivity) : Screen(a) {
    override val title = "Wiadomości"
    private lateinit var box: FrameLayout
    private val adapter = Adapter<Message>({ MessageRow(a) }) { v, m, _ ->
        val r = v as MessageRow
        r.title.text = m.title; r.body.text = m.text; r.time.text = relTime(m.time); r.app.text = m.appName
        r.avatar.set(m.title, null)
        r.onClick = { a.push(MessageDetail(a, m)) }
    }

    override fun build(): View {
        val col = a.vstack()
        col.setBackgroundColor(pal.bg)
        col.addView(a.largeTitle("Wiadomości"))
        box = FrameLayout(a)
        val list = a.listView(); list.adapter = adapter
        box.addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
        col.addView(box, a.lp(MATCH, 0, 1f))
        return col
    }

    private fun refresh() {
        adapter.items = synchronized(Store.messages) { Store.messages.toList() }
        if (box.childCount > 1) box.removeViewAt(1)
        if (adapter.items.isEmpty()) box.addView(a.emptyState(CR.drawable.ic_message, "Brak nowych wiadomości",
            if (Store.connected) "Wiadomości z SMS, WhatsApp, Messengera i innych aplikacji pojawią się tutaj w trakcie jazdy."
            else "Połącz telefon, aby widzieć wiadomości."))
    }

    override fun onShow() { Store.unread = 0; refresh(); on(Topic.MESSAGES) { refresh() } }
}

/** One message: read aloud, quick replies — everything one tap, nothing to type while driving. */
class MessageDetail(a: MainActivity, private val m: Message) : Screen(a) {
    override val title = m.title

    override fun build(): View {
        val sv = ScrollView(a)
        sv.setBackgroundColor(pal.groupedBg)
        val col = a.vstack()
        col.pad(28f, 20f, 28f, 28f)
        val head = a.hstack()
        val av = Avatar(a); av.set(m.title, null)
        head.addView(av, a.lp(a.dpi(56f), a.dpi(56f)).apply { marginEnd = a.dpi(16f) })
        val hc = a.vstack()
        hc.addView(a.text(m.title, 24f, W.BOLD, pal.label))
        hc.addView(a.text("${m.appName} • ${relTime(m.time)}", 15f, W.REGULAR, pal.label2))
        head.addView(hc, a.lp(0, WRAP, 1f))
        head.addView(a.pillButton("Odczytaj", CR.drawable.ic_speaker, pal.blue, 0xFFFFFFFF.toInt()) { Hub.message(m.key, "read") }, a.lp(WRAP, WRAP))
        col.addView(head)
        // incoming bubble
        val bubble = a.text(m.text, 20f, W.REGULAR, pal.label, lines = 0)
        bubble.background = RoundDrawable(if (pal.dark) 0xFF26252A.toInt() else 0xFFE9E9EB.toInt(), a.dp(20f))
        bubble.pad(18f, 12f, 18f, 12f)
        bubble.setLineSpacing(0f, 1.15f)
        col.addView(bubble, a.lp(WRAP, WRAP).apply { topMargin = a.dpi(20f); marginEnd = a.dpi(120f) })
        if (m.canReply) {
            col.addView(a.text("SZYBKA ODPOWIEDŹ", 14f, W.REGULAR, pal.label2), a.lp(WRAP, WRAP).apply { topMargin = a.dpi(28f); bottomMargin = a.dpi(10f) })
            val replies = (Hub.prefs.getString("replies", null) ?: DEFAULT_REPLIES).split('\n').filter { it.isNotBlank() }
            replies.chunked(2).forEach { pair ->
                val row = a.hstack()
                pair.forEach { r ->
                    val b = a.text(r, 18f, W.MEDIUM, 0xFFFFFFFF.toInt(), lines = 2, gravity = Gravity.CENTER)
                    b.background = RoundDrawable(pal.blue, a.dp(22f))
                    b.pad(16f, 14f, 16f, 14f)
                    b.onTap(Press.SCALE) { Hub.message(m.key, "reply", r); a.hud("Wysłano", CR.drawable.ic_check); a.back() }
                    row.addView(b, a.lp(0, WRAP, 1f).apply { setMargins(a.dpi(5f), a.dpi(5f), a.dpi(5f), a.dpi(5f)) })
                }
                if (pair.size == 1) row.addView(View(a), a.lp(0, 1, 1f))
                col.addView(row, a.lp(MATCH, WRAP))
            }
        }
        sv.addView(col)
        return sv
    }

    companion object {
        const val DEFAULT_REPLIES = "Jadę, odpiszę później\nBędę za 10 minut\nZadzwonię za chwilę\nOK 👍"
    }
}
