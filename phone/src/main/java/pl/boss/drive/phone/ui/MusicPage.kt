package pl.boss.drive.phone.ui

import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Motion
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.Segmented
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.fmtTime
import pl.boss.drive.core.ui.glyph
import pl.boss.drive.core.ui.hairline
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.style
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.tint
import pl.boss.drive.core.ui.vstack
import pl.boss.drive.core.util.Bg
import pl.boss.drive.phone.CarLink
import pl.boss.drive.phone.Ev
import pl.boss.drive.phone.Perms
import pl.boss.drive.phone.RadioTrack
import pl.boss.drive.phone.Song
import pl.boss.drive.phone.XItem

fun mb(b: Long): String = when {
    b >= 1024L * 1024 * 1024 -> "%.1f GB".format(b / 1073741824.0)
    b >= 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
    else -> "${b / 1024} KB"
}

/** Generic row for the phone lists: [check/art] title / subtitle ... trailing. */
class PRow(val a: MainActivity) : FrameLayout(a) {
    val check = ImageView(a)
    val title: TextView = a.text("", 17f, W.REGULAR, a.pal.label)
    val sub: TextView = a.text("", 13f, W.REGULAR, a.pal.label2)
    val trailing: TextView = a.text("", 15f, W.REGULAR, a.pal.label2)
    val action = ImageView(a)
    val progress = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000 }
    var onClick: (() -> Unit)? = null
    var onAction: (() -> Unit)? = null

    init {
        val col0 = a.vstack()
        val h = a.hstack()
        h.pad(16f, 9f, 16f, 9f)
        h.addView(check, a.lp(a.dpi(24f), a.dpi(24f)).apply { marginEnd = a.dpi(14f) })
        val col = a.vstack()
        col.addView(title); col.addView(sub, a.lp(WRAP, WRAP).apply { topMargin = a.dpi(2f) })
        h.addView(col, a.lp(0, WRAP, 1f))
        h.addView(trailing, a.lp(WRAP, WRAP).apply { marginStart = a.dpi(8f) })
        action.pad(8f)
        action.onTap(Press.DIM) { onAction?.invoke() }
        h.addView(action, a.lp(a.dpi(38f), a.dpi(38f)).apply { marginStart = a.dpi(4f) })
        col0.addView(h, a.lp(MATCH, WRAP))
        col0.addView(progress, a.lp(MATCH, a.dpi(3f)).apply { setMargins(a.dpi(54f), 0, a.dpi(16f), a.dpi(6f)) })
        addView(col0, LayoutParams(MATCH, WRAP))
        addView(a.hairline(a.pal.separator), LayoutParams(MATCH, maxOf(1, a.dpi(0.5f)), Gravity.BOTTOM).apply { marginStart = a.dpi(54f) })
        progress.progressDrawable = bar(a, a.pal.blue, a.pal.fill3)
        onTap(Press.HIGHLIGHT, a.pal.cellPressed) { onClick?.invoke() }
    }

    companion object {
        fun bar(a: MainActivity, fg: Int, bg: Int): android.graphics.drawable.Drawable {
            val r = a.dp(1.5f)
            val back = android.graphics.drawable.GradientDrawable().apply { setColor(bg); cornerRadius = r }
            val front = android.graphics.drawable.ClipDrawable(android.graphics.drawable.GradientDrawable().apply { setColor(fg); cornerRadius = r }, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)
            return android.graphics.drawable.LayerDrawable(arrayOf(back, front)).apply { setId(0, android.R.id.background); setId(1, android.R.id.progress) }
        }
    }
}

class ListAdapter<T>(private val make: () -> View, private val bindFn: (View, T, Int) -> Unit) : BaseAdapter() {
    var items: List<T> = emptyList(); set(v) { field = v; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(p: Int): Any? = items[p]
    override fun getItemId(p: Int) = p.toLong()
    override fun getView(p: Int, c: View?, parent: ViewGroup?): View { val v = c ?: make(); bindFn(v, items[p], p); return v }
}

class MusicPage(a: MainActivity) : Page(a) {
    private var sel = 0
    private lateinit var box: FrameLayout
    private lateinit var seg: Segmented
    private lateinit var sendBar: View
    private lateinit var sendBtn: TextView
    private var songs: List<Song> = emptyList()
    private var filtered: List<Song> = emptyList()
    private val chosen = LinkedHashSet<Long>()
    private var query = ""

    private val songAdapter = ListAdapter<Song>({ PRow(a) }) { v, s, _ ->
        val r = v as PRow
        r.title.text = s.title
        val onRadio = CarLink.transfers.isOnRadio("ms${s.id}_${s.size}")
        r.sub.text = listOf(s.artist, mb(s.size)).filter { it.isNotEmpty() }.joinToString(" • ")
        r.trailing.text = if (onRadio) "W aucie" else ""
        r.trailing.setTextColor(pal.green)
        val on = s.id in chosen
        r.check.setImageResource(if (on) CR.drawable.ic_check else 0)
        r.check.background = if (on) android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply { paint.color = pal.blue }
            else android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setStroke(a.dpi(1.5f), pal.gray3) }
        r.check.tint(Color.WHITE); r.check.pad(4f)
        r.action.visibility = View.GONE; r.progress.visibility = View.GONE
        r.onClick = { if (!chosen.remove(s.id)) chosen.add(s.id); notifyList(); updateSendBar() }
    }

    private val radioAdapter = ListAdapter<RadioTrack>({ PRow(a) }) { v, t, _ ->
        val r = v as PRow
        r.title.text = t.title; r.sub.text = listOf(t.artist, mb(t.size)).filter { it.isNotEmpty() }.joinToString(" • ")
        r.trailing.text = if (t.dur > 0) fmtTime(t.dur) else ""; r.trailing.setTextColor(pal.label2)
        r.check.setImageResource(CR.drawable.ic_music); r.check.tint(pal.pink); r.check.background = null; r.check.setPadding(0, 0, 0, 0)
        r.action.visibility = View.VISIBLE; r.action.setImageResource(CR.drawable.ic_trash); r.action.tint(pal.red)
        r.progress.visibility = View.GONE
        r.onAction = {
            Ios.alert(a, pal, "Usunąć z auta?", "„${t.title}” zostanie skasowany z radia.", listOf("Anuluj" to 0, "Usuń" to 2)) { k, _ ->
                if (k == 2) CarLink.transfers.deleteOnRadio(listOf(t.id))
            }
        }
        r.onClick = null
    }

    private val queueAdapter = ListAdapter<XItem>({ PRow(a) }) { v, x, _ ->
        val r = v as PRow
        r.title.text = x.title
        val tr = CarLink.transfers
        r.sub.text = when (x.state) {
            XItem.SENDING -> {
                val sp = tr.speed()
                val pct = if (x.size > 0) x.sent * 100 / x.size else 0
                "Wysyłanie $pct%" + (if (sp > 0) " • ${mb(sp)}/s • zostało ${fmtTime((x.size - x.sent) * 1000 / sp)}" else "")
            }
            XItem.DONE -> "W aucie ✓"
            XItem.FAILED -> "Błąd: ${x.err}"
            else -> if (CarLink.connected) "W kolejce" else "Czeka na połączenie z autem"
        }
        r.sub.setTextColor(when (x.state) { XItem.FAILED -> pal.red; XItem.DONE -> pal.green; else -> pal.label2 })
        r.trailing.text = mb(x.size); r.trailing.setTextColor(pal.label2)
        r.check.setImageResource(when (x.state) { XItem.DONE -> CR.drawable.ic_check; XItem.FAILED -> CR.drawable.ic_info; else -> CR.drawable.ic_download })
        r.check.tint(when (x.state) { XItem.DONE -> pal.green; XItem.FAILED -> pal.red; else -> pal.blue }); r.check.background = null; r.check.setPadding(0, 0, 0, 0)
        r.progress.visibility = if (x.state == XItem.SENDING) View.VISIBLE else View.GONE
        r.progress.progress = if (x.size > 0) (x.sent * 1000 / x.size).toInt() else 0
        r.action.visibility = if (x.state == XItem.DONE) View.GONE else View.VISIBLE
        r.action.setImageResource(CR.drawable.ic_xmark); r.action.tint(pal.gray)
        r.onAction = { tr.remove(x.id) }
        r.onClick = null
    }

    private lateinit var songList: ListView
    private lateinit var songsView: View
    private lateinit var radioView: LinearLayout
    private lateinit var radioHeader: LinearLayout
    private lateinit var queueView: LinearLayout

    override fun build(): View {
        val col = a.vstack()
        col.addView(a.largeTitle(pal, "Do auta", "Wybrane piosenki zapiszą się w radiu i zagrają bez internetu."))
        seg = Segmented(a, pal, listOf("Telefon", "W aucie", "Wysyłane"), 13f)
        seg.onSelect = { sel = it; showPage() }
        col.addView(seg, a.lp(MATCH, WRAP).apply { setMargins(a.dpi(16f), a.dpi(8f), a.dpi(16f), a.dpi(10f)) })
        val frame = FrameLayout(a)
        box = FrameLayout(a)
        frame.addView(box, FrameLayout.LayoutParams(MATCH, MATCH))
        // floating "send" button
        sendBtn = a.text("", 17f, W.SEMIBOLD, Color.WHITE, gravity = Gravity.CENTER)
        sendBtn.background = RoundDrawable(pal.blue, a.dp(14f))
        sendBtn.elevation = a.dp(8f)
        sendBtn.onTap(Press.SCALE) { send() }
        sendBar = sendBtn
        frame.addView(sendBtn, FrameLayout.LayoutParams(MATCH, a.dpi(52f), Gravity.BOTTOM).apply { setMargins(a.dpi(16f), 0, a.dpi(16f), a.dpi(14f)) })
        col.addView(frame, a.lp(MATCH, 0, 1f))
        buildSongs(); buildRadio(); buildQueue()
        return col
    }

    private fun buildSongs() {
        val c = a.vstack()
        val search = a.hstack()
        search.background = RoundDrawable(pal.fill3, a.dp(10f))
        search.pad(8f, 0f, 8f, 0f)
        search.addView(a.glyph(CR.drawable.ic_search, pal.gray), a.lp(a.dpi(18f), a.dpi(18f)))
        val et = EditText(a).apply {
            hint = "Szukaj"; style(17f, W.REGULAR, pal.label); setHintTextColor(pal.gray); background = null
            isSingleLine = true; setPadding(a.dpi(6f), a.dpi(8f), a.dpi(6f), a.dpi(8f))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; applyFilter() }
            })
        }
        search.addView(et, a.lp(0, WRAP, 1f))
        c.addView(search, a.lp(MATCH, a.dpi(38f)).apply { setMargins(a.dpi(16f), 0, a.dpi(16f), a.dpi(6f)) })
        val actions = a.hstack()
        actions.pad(16f, 2f, 16f, 6f)
        val all = a.text("Zaznacz widoczne", 15f, W.REGULAR, pal.blue)
        all.onTap { if (filtered.all { it.id in chosen }) filtered.forEach { chosen.remove(it.id) } else filtered.forEach { chosen.add(it.id) }; notifyList(); updateSendBar() }
        actions.addView(all, a.lp(0, WRAP, 1f))
        val notOnRadio = a.text("Tylko nowe", 15f, W.REGULAR, pal.blue)
        notOnRadio.onTap { chosen.removeAll { id -> songs.firstOrNull { it.id == id }?.let { CarLink.transfers.isOnRadio("ms${it.id}_${it.size}") } == true }; notifyList(); updateSendBar() }
        actions.addView(notOnRadio)
        c.addView(actions, a.lp(MATCH, WRAP))
        songList = ListView(a).apply {
            divider = null; selector = android.graphics.drawable.ColorDrawable(0); adapter = songAdapter
            isFastScrollEnabled = true; clipToPadding = false; setPadding(0, 0, 0, a.dpi(80f))
            setBackgroundColor(pal.card)
        }
        c.addView(songList, a.lp(MATCH, 0, 1f))
        songsView = c
    }

    private fun buildRadio() {
        radioView = a.vstack()
        radioHeader = a.vstack()
        radioView.addView(radioHeader, a.lp(MATCH, WRAP))
        val list = ListView(a).apply { divider = null; selector = android.graphics.drawable.ColorDrawable(0); adapter = radioAdapter; setBackgroundColor(pal.card) }
        radioView.addView(list, a.lp(MATCH, 0, 1f))
    }

    private fun buildQueue() {
        queueView = a.vstack()
        val bar = a.hstack()
        bar.pad(16f, 0f, 16f, 8f)
        val clear = a.text("Wyczyść gotowe", 15f, W.REGULAR, pal.blue)
        clear.onTap { CarLink.transfers.clearFinished() }
        bar.addView(clear, a.lp(0, WRAP, 1f))
        val retry = a.text("Ponów nieudane", 15f, W.REGULAR, pal.blue)
        retry.onTap { CarLink.transfers.retryFailed() }
        bar.addView(retry)
        queueView.addView(bar, a.lp(MATCH, WRAP))
        val list = ListView(a).apply { divider = null; selector = android.graphics.drawable.ColorDrawable(0); adapter = queueAdapter; setBackgroundColor(pal.card) }
        queueView.addView(list, a.lp(MATCH, 0, 1f))
    }

    private fun showPage() {
        box.removeAllViews()
        when (sel) {
            0 -> {
                if (!Perms.has(a, Perms.AUDIO)) {
                    box.addView(empty(CR.drawable.ic_music, "Dostęp do muzyki", "Pozwól aplikacji zobaczyć pliki muzyczne na telefonie.", "Zezwól") { a.requestPermissions(Perms.AUDIO, 7) })
                } else {
                    box.addView(songsView)
                    if (songs.isEmpty()) loadSongs()
                }
            }
            1 -> {
                if (!CarLink.connected) box.addView(empty(CR.drawable.ic_car, "Auto niepołączone", "Połącz się z radiem, aby zobaczyć i usuwać piosenki zapisane w aucie.", null) {})
                else { box.addView(radioView); CarLink.transfers.requestRadioLibrary(); bindRadio() }
            }
            2 -> { box.addView(queueView); bindQueue() }
        }
        updateSendBar()
    }

    private fun empty(icon: Int, title: String, sub: String, btn: String?, fn: () -> Unit): View = a.vstack(Gravity.CENTER).apply {
        pad(30f)
        addView(a.glyph(icon, pal.gray3), a.lp(a.dpi(56f), a.dpi(56f)))
        addView(a.text(title, 22f, W.BOLD, pal.label, gravity = Gravity.CENTER), a.lp(MATCH, WRAP).apply { topMargin = a.dpi(14f) })
        addView(a.text(sub, 15f, W.REGULAR, pal.label2, lines = 4, gravity = Gravity.CENTER), a.lp(MATCH, WRAP).apply { topMargin = a.dpi(6f) })
        if (btn != null) addView(a.text(btn, 17f, W.SEMIBOLD, pal.blue, gravity = Gravity.CENTER).apply { pad(16f, 10f, 16f, 10f); onTap { fn() } })
    }

    private fun loadSongs() {
        Bg.load({ CarLink.library.songs() }) { s -> songs = s; applyFilter() }
    }

    private fun applyFilter() {
        val q = query.trim().lowercase()
        filtered = if (q.isEmpty()) songs else songs.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q) }
        songAdapter.items = filtered
    }

    private fun notifyList() { songAdapter.notifyDataSetChanged() }

    private fun updateSendBar() {
        val n = chosen.size
        val show = sel == 0 && n > 0
        if (show && sendBar.visibility != View.VISIBLE) { sendBar.visibility = View.VISIBLE; sendBar.translationY = a.dp(80f); sendBar.animate().translationY(0f).setDuration(300).setInterpolator(Motion.push).start() }
        if (!show) sendBar.visibility = View.GONE
        val size = songs.filter { it.id in chosen }.sumOf { it.size }
        sendBtn.text = "Wyślij do auta: $n • ${mb(size)}"
    }

    private fun send() {
        val list = songs.filter { it.id in chosen }
        if (list.isEmpty()) return
        CarLink.transfers.enqueueSongs(list)
        chosen.clear(); notifyList(); updateSendBar()
        Ios.hud(a, pal, if (CarLink.connected) "Wysyłam ${list.size} do auta" else "Dodano ${list.size} — wyślą się po połączeniu z autem")
        if (!CarLink.running) pl.boss.drive.phone.CarService.start(a)
        seg.select(2); sel = 2; showPage()
    }

    private fun bindRadio() {
        val tr = CarLink.transfers
        radioAdapter.items = tr.radioTracks
        radioHeader.removeAllViews()
        if (!tr.radioKnown) { radioHeader.addView(a.text("Wczytuję…", 15f, W.REGULAR, pal.label2).apply { pad(16f, 4f, 16f, 10f) }); return }
        val total = tr.radioUsed + tr.radioFree
        val card = a.vstack()
        card.background = RoundDrawable(pal.card, a.dp(12f)); card.pad(14f, 12f, 14f, 12f)
        val row = a.hstack()
        row.addView(a.text("${tr.radioTracks.size} utworów w aucie", 17f, W.SEMIBOLD, pal.label), a.lp(0, WRAP, 1f))
        row.addView(a.text("${mb(tr.radioUsed)} z ${mb(total)}", 15f, W.REGULAR, pal.label2))
        card.addView(row)
        val bar = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progress = if (total > 0) (tr.radioUsed * 1000 / total).toInt().coerceAtLeast(6) else 0
            progressDrawable = PRow.bar(a, pal.pink, pal.fill3)
        }
        card.addView(bar, a.lp(MATCH, a.dpi(8f)).apply { topMargin = a.dpi(10f) })
        card.addView(a.text("Wolne: ${mb(tr.radioFree)}", 13f, W.REGULAR, pal.label2), a.lp(WRAP, WRAP).apply { topMargin = a.dpi(6f) })
        radioHeader.addView(card, a.lp(MATCH, WRAP).apply { setMargins(a.dpi(16f), 0, a.dpi(16f), a.dpi(10f)) })
    }

    private fun bindQueue() { queueAdapter.items = CarLink.transfers.snapshot().reversed() }

    override fun onShow() {
        seg.select(sel, false)
        showPage()
        on(Ev.TRANSFERS) { if (sel == 2) bindQueue(); if (sel == 0) notifyList() }
        on(Ev.RADIO_LIB) { if (sel == 1) bindRadio(); if (sel == 0) notifyList() }
        on(Ev.LINK) { if (sel == 1) showPage() }
    }
}

