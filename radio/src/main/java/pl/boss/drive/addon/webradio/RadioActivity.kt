package pl.boss.drive.addon.webradio

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import org.json.JSONArray
import org.json.JSONObject
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.addon.ARow
import pl.boss.drive.core.addon.Addon
import pl.boss.drive.core.addon.AddonActivity
import pl.boss.drive.core.addon.Addons
import pl.boss.drive.core.addon.Audio
import pl.boss.drive.core.addon.MiniPlayer
import pl.boss.drive.core.addon.Net
import pl.boss.drive.core.addon.RemoteImages
import pl.boss.drive.core.addon.SimpleAdapter
import pl.boss.drive.core.ui.MATCH
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
import java.io.File
import java.net.URLEncoder

data class Station(val id: String, val name: String, val url: String, val icon: String, val tags: String, val bitrate: Int) {
    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("url", url).put("icon", icon).put("tags", tags).put("br", bitrate)
    companion object {
        fun of(o: JSONObject) = Station(o.optString("id"), o.optString("name"), o.optString("url"), o.optString("icon"), o.optString("tags"), o.optInt("br"))
    }
}

/** Polish internet radio via the open radio-browser.info directory (community-run, no key). */
class RadioActivity : AddonActivity() {
    override val addon: Addon get() = Addons.byPkg("pl.boss.drive.addon.webradio")!!
    private val prefs by lazy { getSharedPreferences("radio", Context.MODE_PRIVATE) }
    private var tab = 0
    private lateinit var box: FrameLayout
    private var popular: List<Station> = emptyList()
    private var results: List<Station> = emptyList()
    private var favs = ArrayList<Station>()
    private var loading = false
    private var current: List<Station> = emptyList()
    private val adapter = SimpleAdapter<Station>({ ARow(this) }) { v, s, _ -> bind(v as ARow, s) }
    private var listV: ListView? = null
    private var unsub: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Audio.init(this)
        Audio.onSkip = { dir -> skip(dir) }
        loadFavs(); loadCache()
        super.onCreate(savedInstanceState)
        tab = if (favs.isEmpty()) 1 else 0
        fetchPopular()
    }

    override fun buildContent(): View {
        val root = FrameLayout(this)
        val col = vstack()
        col.addView(header("Radio online"))
        val seg = Segmented(this, pal, listOf("Ulubione", "Popularne", "Szukaj"), 16f)
        seg.select(if (favs.isEmpty()) 1 else 0, false)
        seg.onSelect = { tab = it; render() }
        col.addView(seg, lp(MATCH, WRAP).apply { setMargins(dpi(24f), 0, dpi(24f), dpi(8f)) })
        box = FrameLayout(this)
        col.addView(box, lp(MATCH, 0, 1f))
        root.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(MiniPlayer(this), MiniPlayer.lpBottom(this))
        return root
    }

    private fun list(): ListView = listV ?: listView().apply { adapter = this@RadioActivity.adapter; setPadding(0, 0, 0, dpi(110f)) }.also { listV = it }

    private fun render() {
        box.removeAllViews()
        when (tab) {
            0 -> show(favs, empty(CR.drawable.ic_star, "Brak ulubionych", "Dotknij gwiazdki przy stacji, aby dodać ją tutaj."))
            1 -> if (loading && popular.isEmpty()) box.addView(spinner()) else show(popular, empty(CR.drawable.ic_wifi, "Brak internetu", "Połącz radio z hotspotem telefonu, aby wczytać listę stacji."))
            2 -> { val c = vstack(); c.addView(searchBar(), lp(MATCH, dpi(52f)).apply { setMargins(dpi(24f), dpi(4f), dpi(24f), dpi(8f)) })
                val f = FrameLayout(this)
                if (loading) f.addView(spinner()) else if (results.isNotEmpty()) { current = results; adapter.items = results; (list().parent as? FrameLayout)?.removeView(list()); f.addView(list()) }
                c.addView(f, lp(MATCH, 0, 1f)); box.addView(c) }
        }
    }

    private fun show(items: List<Station>, emptyView: View) {
        if (items.isEmpty()) { box.addView(emptyView); return }
        current = items; adapter.items = items
        (list().parent as? FrameLayout)?.removeView(list())
        box.addView(list())
    }

    private fun spinner() = vstack(Gravity.CENTER).apply { addView(Spinner(this@RadioActivity, pal.gray), lp(dpi(34f), dpi(34f))) }

    private fun searchBar(): View {
        val bar = hstack()
        bar.background = RoundDrawable(pal.fill3, dp(12f))
        bar.pad(12f, 0f, 6f, 0f)
        bar.addView(glyph(CR.drawable.ic_search, pal.gray), lp(dpi(22f), dpi(22f)))
        val et = EditText(this).apply {
            hint = "Nazwa stacji, np. RMF, Trójka"; style(19f, W.REGULAR, pal.label); setHintTextColor(pal.gray); background = null; isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH; setPadding(dpi(10f), dpi(10f), dpi(10f), dpi(10f))
        }
        val go = {
            val q = et.text.toString()
            if (q.isNotBlank()) {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(et.windowToken, 0)
                loading = true; render()
                Bg.load({ try { query("&name=" + URLEncoder.encode(q, "UTF-8")) } catch (_: Throwable) { emptyList() } }) { r -> loading = false; results = r; if (tab == 2) render() }
            }
        }
        et.setOnEditorActionListener { _, id, ev -> if (id == EditorInfo.IME_ACTION_SEARCH || ev?.keyCode == KeyEvent.KEYCODE_ENTER) { go(); true } else false }
        bar.addView(et, lp(0, WRAP, 1f))
        bar.addView(text("Szukaj", 17f, W.SEMIBOLD, pal.pink).apply { pad(12f, 10f, 12f, 10f); onTap { go() } })
        return bar
    }

    private fun bind(r: ARow, s: Station) {
        r.title.text = s.name
        r.sub.text = listOfNotNull(s.tags.split(',').take(3).joinToString(", ").ifEmpty { null }, if (s.bitrate > 0) "${s.bitrate} kbps" else null).joinToString(" • ")
        r.trailing.text = ""
        r.setArt(RemoteImages.get(this, s.icon, 96) { adapter.notifyDataSetChanged() }, CR.drawable.ic_radio, pal.pink)
        val fav = favs.any { it.id == s.id }
        r.setAction(if (fav) CR.drawable.ic_star else CR.drawable.ic_star_outline, if (fav) pal.yellow else pal.gray)
        r.title.setTextColor(if (Audio.key == s.id) pal.pink else pal.label)
        r.onAction = { toggleFav(s) }
        r.onClick = { play(s) }
    }

    private fun play(s: Station) {
        Audio.owner = "pl.boss.drive.addon.webradio"; Audio.play(s.id, s.url, s.name, s.tags.split(',').firstOrNull()?.trim().orEmpty().ifEmpty { "Radio" }, RemoteImages.get(this, s.icon, 96) {}, live = true)
        adapter.notifyDataSetChanged()
    }

    private fun skip(dir: Int) {
        val list = current.ifEmpty { favs }
        if (list.isEmpty()) return
        val i = list.indexOfFirst { it.id == Audio.key }
        play(list[((if (i < 0) 0 else i + dir) + list.size) % list.size])
    }

    private fun toggleFav(s: Station) {
        if (!favs.removeAll { it.id == s.id }) favs.add(s)
        prefs.edit().putString("favs", JSONArray().apply { favs.forEach { put(it.json()) } }.toString()).apply()
        adapter.notifyDataSetChanged()
        if (tab == 0) render()
    }

    private fun loadFavs() {
        try { val a = JSONArray(prefs.getString("favs", "[]")); for (i in 0 until a.length()) favs.add(Station.of(a.getJSONObject(i))) } catch (_: Throwable) {}
    }

    private fun cacheFile() = File(filesDir, "popular.json")
    private fun loadCache() { try { val a = JSONArray(cacheFile().readText()); popular = (0 until a.length()).map { Station.of(a.getJSONObject(it)) } } catch (_: Throwable) {} }

    private fun fetchPopular() {
        loading = true
        Bg.load({ try { query("") } catch (_: Throwable) { null } }) { r ->
            loading = false
            if (r != null && r.isNotEmpty()) {
                popular = r
                Bg.run { cacheFile().writeText(JSONArray().apply { r.forEach { put(it.json()) } }.toString()) }
            }
            if (tab == 1) render()
        }
    }

    private fun query(extra: String): List<Station> {
        var last: Throwable? = null
        for (host in listOf("de1", "nl1", "at1", "fi1")) {
            try {
                val arr = JSONArray(Net.text("https://$host.api.radio-browser.info/json/stations/search?countrycode=PL&order=clickcount&reverse=true&limit=150&hidebroken=true$extra"))
                return (0 until arr.length()).map { arr.getJSONObject(it) }.mapNotNull { o ->
                    val url = o.optString("url_resolved").ifEmpty { o.optString("url") }
                    if (url.isEmpty()) null else Station(o.optString("stationuuid"), o.optString("name").trim(), url, o.optString("favicon"), o.optString("tags"), o.optInt("bitrate"))
                }.distinctBy { it.name.lowercase() }
            } catch (e: Throwable) { last = e }
        }
        throw last ?: java.io.IOException("no mirror")
    }

    override fun onResume() { super.onResume(); unsub = Audio.listen { adapter.notifyDataSetChanged() }; render() }
    override fun onPause() { unsub?.invoke(); super.onPause() }
}
