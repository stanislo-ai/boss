package pl.boss.drive.radio.ui

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.Segmented
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.circleButton
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.glyph
import pl.boss.drive.core.ui.hairline
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack
import pl.boss.drive.radio.Contact
import pl.boss.drive.radio.Hub
import pl.boss.drive.radio.MainActivity
import pl.boss.drive.radio.Recent
import pl.boss.drive.radio.Store
import pl.boss.drive.radio.Topic

/** Row with an avatar — recents and contacts. */
class ContactRow(val a: MainActivity) : FrameLayout(a) {
    val avatar = Avatar(a)
    val title: TextView = a.text("", 19f, W.MEDIUM, a.pal.label)
    val sub: TextView = a.text("", 15f, W.REGULAR, a.pal.label2)
    val trailing: TextView = a.text("", 16f, W.REGULAR, a.pal.label2)
    val icon: ImageView = a.glyph(CR.drawable.ic_phone, a.pal.gray)
    var onClick: (() -> Unit)? = null

    init {
        val h = a.hstack()
        h.pad(20f, 0f, 24f, 0f)
        h.addView(avatar, a.lp(a.dpi(46f), a.dpi(46f)).apply { marginEnd = a.dpi(16f) })
        val col = a.vstack(Gravity.CENTER_VERTICAL)
        col.addView(title); col.addView(sub, a.lp(WRAP, WRAP).apply { topMargin = a.dpi(2f) })
        h.addView(col, a.lp(0, WRAP, 1f))
        h.addView(trailing, a.lp(WRAP, WRAP).apply { marginStart = a.dpi(12f) })
        h.addView(icon, a.lp(a.dpi(22f), a.dpi(22f)).apply { marginStart = a.dpi(16f) })
        addView(h, LayoutParams(MATCH, a.dpi(70f)))
        addView(a.hairline(a.pal.separator), LayoutParams(MATCH, maxOf(1, a.dpi(0.5f)), Gravity.BOTTOM).apply { marginStart = a.dpi(82f) })
        onTap(Press.HIGHLIGHT, a.pal.cellPressed) { onClick?.invoke() }
    }
}

class PhoneScreen(a: MainActivity) : Screen(a) {
    override val title = "Telefon"
    private var sel = 0
    private lateinit var box: FrameLayout
    private val pages = arrayOfNulls<View>(4)

    private val recentsAdapter = Adapter<Recent>({ ContactRow(a) }) { v, r, _ ->
        val row = v as ContactRow
        row.title.text = r.name.ifEmpty { r.number }
        row.title.setTextColor(if (r.type == 3 || r.type == 5) a.pal.red else a.pal.label)
        row.sub.text = when (r.type) { 1 -> "Przychodzące"; 2 -> "Wychodzące"; 3 -> "Nieodebrane"; 5 -> "Odrzucone"; else -> "Połączenie" }
        row.trailing.text = relTime(r.time)
        row.avatar.set(r.name, null)
        row.onClick = { dial(r.name.ifEmpty { r.number }, r.number) }
    }
    private val contactsAdapter = Adapter<Contact>({ ContactRow(a) }) { v, c, _ ->
        val row = v as ContactRow
        row.title.text = c.name; row.title.setTextColor(a.pal.label)
        row.sub.text = listOf(c.label, c.number).filter { it.isNotEmpty() }.joinToString(" • ")
        row.trailing.text = ""
        row.avatar.set(c.name, Store.photos.get(c.photo))
        row.onClick = { dial(c.name, c.number) }
    }

    override fun build(): View {
        val col = a.vstack()
        val seg = Segmented(a, pal, listOf("Ulubione", "Ostatnie", "Kontakty", "Klawiatura"), 16f)
        seg.onSelect = { sel = it; showPage() }
        col.addView(a.largeTitle("Telefon"))
        col.addView(seg, a.lp(MATCH, WRAP).apply { setMargins(a.dpi(24f), 0, a.dpi(24f), a.dpi(10f)) })
        box = FrameLayout(a)
        col.addView(box, a.lp(MATCH, 0, 1f))
        col.setBackgroundColor(pal.bg)
        return col
    }

    private fun dial(name: String, number: String) {
        if (number.isEmpty()) return
        Hub.call("dial", number)
        a.hud("Dzwonię: $name", CR.drawable.ic_phone)
    }

    private fun showPage() {
        box.removeAllViews()
        if (!Store.connected && sel != 3) {
            box.addView(a.emptyState(CR.drawable.ic_iphone, "Telefon niepołączony", "Połącz telefon z aplikacją Boss Drive, żeby widzieć kontakty i historię połączeń."))
            return
        }
        val v = pages[sel] ?: when (sel) {
            0 -> buildFavs()
            1 -> a.listView().apply { adapter = recentsAdapter }
            2 -> a.listView().apply { adapter = contactsAdapter; isFastScrollEnabled = true }
            else -> buildKeypad()
        }.also { pages[sel] = it }
        if (sel == 0) fillFavs(v as ScrollView)
        (v.parent as? FrameLayout)?.removeView(v)
        box.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun buildFavs(): ScrollView = ScrollView(a).apply { isFillViewport = true }

    private fun fillFavs(sv: ScrollView) {
        sv.removeAllViews()
        val favs = Store.favorites
        if (favs.isEmpty()) { sv.addView(a.emptyState(CR.drawable.ic_star, "Brak ulubionych", "Oznacz kontakty gwiazdką w telefonie — pojawią się tutaj jako duże przyciski.")); return }
        val col = a.vstack()
        col.pad(16f, 6f, 16f, 20f)
        favs.chunked(5).forEach { chunk ->
            val row = a.hstack(Gravity.TOP)
            chunk.forEach { f ->
                val cell = a.vstack(Gravity.CENTER_HORIZONTAL)
                cell.pad(4f, 10f, 4f, 10f)
                val av = Avatar(a); av.set(f.name, Store.photos.get(f.photo))
                cell.addView(av, a.lp(a.dpi(96f), a.dpi(96f)))
                cell.addView(a.text(f.name, 17f, W.MEDIUM, pal.label, gravity = Gravity.CENTER), a.lp(MATCH, WRAP).apply { topMargin = a.dpi(9f) })
                cell.addView(a.text(f.label.ifEmpty { "komórka" }, 14f, W.REGULAR, pal.label2, gravity = Gravity.CENTER), a.lp(MATCH, WRAP))
                cell.onTap(Press.SCALE) { dial(f.name, f.number) }
                row.addView(cell, a.lp(0, WRAP, 1f))
            }
            repeat(5 - chunk.size) { row.addView(View(a), a.lp(0, 1, 1f)) }
            col.addView(row, a.lp(MATCH, WRAP))
        }
        sv.addView(col)
    }

    private var number = ""
    private lateinit var numberView: TextView

    private fun buildKeypad(): View {
        val root = a.hstack(Gravity.CENTER)
        val left = a.vstack(Gravity.CENTER)
        numberView = a.text("", 38f, W.MEDIUM, pal.label, gravity = Gravity.CENTER)
        left.addView(numberView, a.lp(a.dpi(330f), WRAP))
        val del = a.text("Usuń", 18f, W.REGULAR, pal.blue, gravity = Gravity.CENTER)
        del.pad(16f, 10f, 16f, 10f)
        del.onTap { if (number.isNotEmpty()) { number = number.dropLast(1); numberView.text = number } }
        del.setOnLongClickListener { number = ""; numberView.text = ""; true }
        left.addView(del, a.lp(WRAP, WRAP).apply { topMargin = a.dpi(10f) })
        val call = a.circleButton(CR.drawable.ic_phone, 0xFF34C759.toInt(), Color.WHITE, 86f, 0.44f)
        call.onTap(Press.SCALE) { if (number.isNotEmpty()) dial(number, number) }
        left.addView(call, a.lp(a.dpi(86f), a.dpi(86f)).apply { topMargin = a.dpi(20f) })
        root.addView(left, a.lp(WRAP, WRAP).apply { marginEnd = a.dpi(50f) })

        val keys = listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to "")
        val grid = a.vstack()
        keys.chunked(3).forEach { rowKeys ->
            val r = a.hstack()
            rowKeys.forEach { (d, l) ->
                val k = a.vstack(Gravity.CENTER)
                k.background = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply { paint.color = pal.fill3 }
                k.addView(a.text(d, 32f, W.REGULAR, pal.label, gravity = Gravity.CENTER))
                if (l.isNotEmpty()) k.addView(a.text(l, 10f, W.SEMIBOLD, pal.label, gravity = Gravity.CENTER).apply { letterSpacing = 0.18f })
                k.onTap(Press.SCALE) { if (number.length < 20) { number += d; numberView.text = number } }
                if (d == "0") k.setOnLongClickListener { number += "+"; numberView.text = number; true }
                r.addView(k, a.lp(a.dpi(84f), a.dpi(84f)).apply { setMargins(a.dpi(10f), a.dpi(6f), a.dpi(10f), a.dpi(6f)) })
            }
            grid.addView(r)
        }
        root.addView(grid)
        return root
    }

    override fun onShow() {
        recentsAdapter.items = Store.recents
        contactsAdapter.items = Store.contacts
        showPage()
        on(Topic.LINK) { pages.fill(null); showPage() }
        on(Topic.CONTACTS) { contactsAdapter.items = Store.contacts; if (sel == 0) showPage() }
        on(Topic.RECENTS) { recentsAdapter.items = Store.recents }
    }
}

