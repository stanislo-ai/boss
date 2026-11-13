package pl.boss.drive.addon.podcasts

import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.addon.ARow
import pl.boss.drive.core.addon.Addon
import pl.boss.drive.core.addon.AddonActivity
import pl.boss.drive.core.addon.Addons
import pl.boss.drive.core.addon.Audio
import pl.boss.drive.core.addon.MiniPlayer
import pl.boss.drive.core.addon.RemoteImages
import pl.boss.drive.core.addon.SimpleAdapter
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.Segmented
import pl.boss.drive.core.ui.Spinner
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.glyph
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.style
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack
import pl.boss.drive.core.util.Bg
import java.text.SimpleDateFormat
import java.util.Locale

class PodcastsActivity : AddonActivity() {
    override val addon: Addon get() = Addons.byPkg(packageName)!!
    private var tab = 0
    private lateinit var box: FrameLayout
    private lateinit var refreshBtn: View
    private var unsub: (() -> Unit)? = null
    private var results: List<Show> = emptyList()
    private var searching = false
    private var epList: ListView? = null
    private val date = SimpleDateFormat("d MMM", Locale("pl"))

    private val epAdapter = SimpleAdapter<Episode>({ ARow(this) }) { v, e, _ -> bindEpisode(v as ARow, e) }
    private val showAdapter = SimpleAdapter<Show>({ ARow(this) }) { v, s, _ -> bindShow(v as ARow, s, subscribed = true) }
    private val resultAdapter = SimpleAdapter<Show>({ ARow(this) }) { v, s, _ -> bindShow(v as ARow, s, subscribed = Library.show(s.feed) != null) }

    override fun onCreate(savedInstanceState: Bundle?) {
        Library.init(this); Audio.init(this)
        Audio.onPosition = { id, pos -> Library.position(id, pos) }
        Audio.onComplete = { id -> Library.markPlayed(id); playNext(id) }
        super.onCreate(savedInstanceState)
        Library.schedule(this)
        Bg.io { Library.refreshAll() }
    }

    override fun buildContent(): View {
        val root = FrameLayout(this)
        val col = vstack()
        refreshBtn = pill("Odśwież", CR.drawable.ic_gauge_reset, pal.fill3, pal.purple) { Bg.io { Library.refreshAll() } }
        col.addView(header("Podcasty", refreshBtn))
        val seg = Segmented(this, pal, listOf("Odcinki", "Subskrypcje", "Szukaj"), 16f)
        seg.onSelect = { tab = it; render() }
        col.addView(seg, lp(MATCH, WRAP).apply { setMargins(dpi(24f), 0, dpi(24f), dpi(8f)) })
        box = FrameLayout(this)
        col.addView(box, lp(MATCH, 0, 1f))
        root.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(MiniPlayer(this), MiniPlayer.lpBottom(this))
        return root
    }

    private fun list(adapter: android.widget.BaseAdapter): ListView = listView().apply { this.adapter = adapter; setPadding(0, 0, 0, dpi(110f)) }

    private fun render() {
        box.removeAllViews()
        refreshBtn.alpha = if (Library.refreshing) 0.4f else 1f
        when (tab) {
            0 -> {
                val eps = Library.list()
                epAdapter.items = eps
                if (eps.isEmpty()) box.addView(if (Library.shows.isEmpty()) empty(CR.drawable.ic_podcast, "Brak subskrypcji", "Wejdź w „Szukaj” i dodaj ulubione podcasty — nowe odcinki będą się pobierać same.")
                    else empty(CR.drawable.ic_check, "Wszystko przesłuchane", "Nowe odcinki pojawią się tu automatycznie."))
                else box.addView(epList ?: list(epAdapter).also { epList = it })
            }
            1 -> { showAdapter.items = Library.shows.toList(); if (Library.shows.isEmpty()) box.addView(empty(CR.drawable.ic_podcast, "Brak subskrypcji", "Dodaj podcasty w zakładce „Szukaj”.")) else box.addView(list(showAdapter)) }
            2 -> box.addView(searchPage())
        }
    }

    private fun searchPage(): View {
        val col = vstack()
        val bar = hstack()
        bar.background = RoundDrawable(pal.fill3, dp(12f))
        bar.pad(12f, 0f, 6f, 0f)
        bar.addView(glyph(CR.drawable.ic_search, pal.gray), lp(dpi(22f), dpi(22f)))
        val et = EditText(this).apply {
            hint = "Nazwa podcastu"; style(19f, W.REGULAR, pal.label); setHintTextColor(pal.gray); background = null; isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dpi(10f), dpi(10f), dpi(10f), dpi(10f))
        }
        val go = { q: String ->
            if (q.isNotBlank()) {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(et.windowToken, 0)
                searching = true; render()
                Bg.load({ try { Library.search(q) } catch (_: Throwable) { null } }) { r -> searching = false; results = r ?: emptyList(); if (r == null) results = emptyList(); if (tab == 2) render() }
            }
        }
        et.setOnEditorActionListener { _, id, ev -> if (id == EditorInfo.IME_ACTION_SEARCH || ev?.keyCode == KeyEvent.KEYCODE_ENTER) { go(et.text.toString()); true } else false }
        bar.addView(et, lp(0, WRAP, 1f))
        bar.addView(text("Szukaj", 17f, W.SEMIBOLD, pal.purple).apply { pad(12f, 10f, 12f, 10f); onTap { go(et.text.toString()) } })
        col.addView(bar, lp(MATCH, dpi(52f)).apply { setMargins(dpi(24f), dpi(4f), dpi(24f), dpi(10f)) })
        if (searching) { col.addView(vstack(Gravity.CENTER).apply { addView(Spinner(this@PodcastsActivity, pal.gray), lp(dpi(34f), dpi(34f))) }, lp(MATCH, 0, 1f)); return col }
        if (results.isEmpty()) {
            col.addView(text("Popularne w Polsce", 15f, W.SEMIBOLD, pal.label2), lp(WRAP, WRAP).apply { marginStart = dpi(26f); bottomMargin = dpi(8f) })
            val chips = listOf("Raport o stanie świata", "Dział Zagraniczny", "Kryminatorium", "Imponderabilia", "Puls Biznesu", "Mała Wielka Firma", "Historia", "Motoryzacja")
            chips.chunked(4).forEach { row ->
                val h = hstack()
                row.forEach { c ->
                    h.addView(text(c, 16f, W.MEDIUM, pal.label, gravity = Gravity.CENTER).apply {
                        background = RoundDrawable(pal.card, dp(22f)); pad(14f, 12f, 14f, 12f); onTap(Press.SCALE) { et.setText(c); go(c) }
                    }, lp(0, WRAP, 1f).apply { setMargins(dpi(5f), dpi(5f), dpi(5f), dpi(5f)) })
                }
                col.addView(h, lp(MATCH, WRAP).apply { setMargins(dpi(19f), 0, dpi(19f), 0) })
            }
        } else {
            resultAdapter.items = results
            col.addView(list(resultAdapter), lp(MATCH, 0, 1f))
        }
        return col
    }

    private fun bindEpisode(r: ARow, e: Episode) {
        val s = Library.show(e.feed)
        r.title.text = e.title
        val left = if (e.pos > 0 && e.dur > 0) "zostało ${(e.dur - e.pos) / 60000} min" else if (e.dur > 0) "${e.dur / 60000} min" else ""
        r.sub.text = listOfNotNull(s?.title, if (e.date > 0) date.format(e.date) else null, left.ifEmpty { null }).joinToString(" • ")
        r.setArt(RemoteImages.get(this, s?.art ?: "") { epAdapter.notifyDataSetChanged() }, CR.drawable.ic_podcast, pal.purple)
        val pct = Library.downloading[e.id]
        val playingThis = Audio.key == e.id
        r.trailing.text = if (pct != null) "$pct%" else ""
        r.trailing.setTextColor(pal.purple)
        when {
            pct != null -> r.setAction(0, 0)
            e.downloaded -> r.setAction(CR.drawable.ic_check, pal.green)
            else -> r.setAction(CR.drawable.ic_download, pal.purple)
        }
        r.title.setTextColor(if (playingThis) pal.purple else pal.label)
        r.onAction = { if (!e.downloaded) Library.download(e) }
        r.onClick = { play(e) }
    }

    private fun bindShow(r: ARow, s: Show, subscribed: Boolean) {
        r.title.text = s.title; r.sub.text = s.author; r.trailing.text = ""
        r.setArt(RemoteImages.get(this, s.art) { showAdapter.notifyDataSetChanged(); resultAdapter.notifyDataSetChanged() }, CR.drawable.ic_podcast, pal.purple)
        if (tab == 1) {
            r.setAction(CR.drawable.ic_trash, pal.red)
            r.onAction = { confirm("Usunąć subskrypcję?", "„${s.title}” i pobrane odcinki znikną z radia.", "Usuń") { Library.unsubscribe(s) } }
            r.onClick = null
        } else {
            r.setAction(if (subscribed) CR.drawable.ic_check else CR.drawable.ic_plus, if (subscribed) pal.green else pal.purple)
            r.onAction = { if (!subscribed) { Library.subscribe(s); resultAdapter.notifyDataSetChanged() } }
            r.onClick = r.onAction
        }
    }

    private fun play(e: Episode) {
        val s = Library.show(e.feed)
        val art = RemoteImages.get(this, s?.art ?: "") {}
        Audio.play(e.id, if (e.downloaded) e.file else e.url, e.title, s?.title ?: "", art, e.pos)
    }

    private fun playNext(finishedId: String) {
        val next = Library.list().firstOrNull { it.id != finishedId && it.downloaded } ?: return
        Bg.main.postDelayed({ play(next) }, 1500)
    }

    override fun onResume() {
        super.onResume()
        unsub = Library.listen {
            refreshBtn.alpha = if (Library.refreshing) 0.4f else 1f
            val eps = Library.list()
            if (tab == 0 && eps.isNotEmpty() && box.getChildAt(0) === epList) epAdapter.items = eps
            else if (tab != 2) render()
        }
        render()
    }

    override fun onPause() { unsub?.invoke(); super.onPause() }

}
