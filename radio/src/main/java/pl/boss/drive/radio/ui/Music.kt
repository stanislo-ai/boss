package pl.boss.drive.radio.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Motion
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.ScrubBar
import pl.boss.drive.core.ui.Segmented
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.clipRound
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.fmtTime
import pl.boss.drive.core.ui.glyph
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.iconTile
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.tint
import pl.boss.drive.core.ui.vstack
import pl.boss.drive.core.ui.withAlpha
import pl.boss.drive.core.util.Bg
import pl.boss.drive.radio.AppMedia
import pl.boss.drive.radio.Hub
import pl.boss.drive.radio.LibItem
import pl.boss.drive.radio.MainActivity
import pl.boss.drive.radio.QueueItem
import pl.boss.drive.radio.Source
import pl.boss.drive.radio.Store
import pl.boss.drive.radio.Topic
import pl.boss.drive.radio.Track

/** Full "Now Playing": blurred artwork backdrop, big art that breathes with play/pause. */
class NowPlayingScreen(a: MainActivity) : Screen(a) {
    override val title = "Teraz gra"
    private lateinit var back: ImageView
    private lateinit var art: ImageView
    private lateinit var artWrap: FrameLayout
    private lateinit var tTitle: TextView
    private lateinit var tArtist: TextView
    private lateinit var tAlbum: TextView
    private lateinit var srcIcon: ImageView
    private lateinit var srcName: TextView
    private lateinit var play: ImageView
    private lateinit var prev: ImageView
    private lateinit var next: ImageView
    private lateinit var shuffle: ImageView
    private lateinit var repeat: ImageView
    private lateinit var scrub: ScrubBar
    private var lastArt: Any? = null
    private var lastPlaying: Boolean? = null

    override fun build(): View {
        val root = FrameLayout(a)
        root.setBackgroundColor(Color.BLACK)
        back = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(back, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(View(a).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x59000000, 0x80000000.toInt(), 0xB3000000.toInt()))
        }, FrameLayout.LayoutParams(MATCH, MATCH))

        val col = a.vstack()
        col.pad(26f, 14f, 26f, 18f)
        // top bar: source picker | queue | library
        val bar = a.hstack()
        val src = a.hstack()
        src.background = RoundDrawable(0x26FFFFFF, a.dp(20f))
        src.pad(8f, 6f, 14f, 6f)
        srcIcon = ImageView(a).apply { clipRound(a.dp(6f)) }
        src.addView(srcIcon, a.lp(a.dpi(28f), a.dpi(28f)))
        srcName = a.text("", 16f, W.SEMIBOLD, Color.WHITE)
        src.addView(srcName, a.lp(WRAP, WRAP).apply { marginStart = a.dpi(9f) })
        src.addView(a.glyph(CR.drawable.ic_chevron_down, 0xB3FFFFFF.toInt()), a.lp(a.dpi(14f), a.dpi(14f)).apply { marginStart = a.dpi(6f) })
        src.onTap(Press.SCALE) { a.push(SourcesScreen(a)) }
        bar.addView(src, a.lp(WRAP, a.dpi(44f)))
        bar.addView(View(a), a.lp(0, 1, 1f))
        fun topBtn(res: Int, fn: () -> Unit) = bar.addView(a.circleGlyph(res, 0x26FFFFFF, Color.WHITE, 48f, fn), a.lp(a.dpi(48f), a.dpi(48f)).apply { marginStart = a.dpi(12f) })
        topBtn(CR.drawable.ic_list) { if (Store.localActive) a.push(OfflineScreen(a)) else a.push(QueueScreen(a)) }
        topBtn(CR.drawable.ic_album) { a.push(LibraryScreen(a)) }
        col.addView(bar, a.lp(MATCH, WRAP))

        val mid = a.hstack()
        artWrap = FrameLayout(a)
        art = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipRound(a.dp(14f)) }
        artWrap.addView(art, FrameLayout.LayoutParams(MATCH, MATCH))
        artWrap.elevation = a.dp(24f)
        artWrap.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, a.dp(14f)) }
        }
        artWrap.onTap(Press.NONE) { Hub.cmd("toggle") }
        mid.addView(artWrap, a.lp(a.dpi(372f), a.dpi(372f)))

        val info = a.vstack()
        tTitle = a.text("", 30f, W.BOLD, Color.WHITE, lines = 2)
        tArtist = a.text("", 22f, W.REGULAR, 0xB3FFFFFF.toInt(), lines = 1)
        tAlbum = a.text("", 17f, W.REGULAR, 0x80FFFFFF.toInt(), lines = 1)
        info.addView(tTitle)
        info.addView(tArtist, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(6f) })
        info.addView(tAlbum, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(4f) })
        info.addView(View(a), a.lp(1, a.dpi(30f)))
        scrub = ScrubBar(a)
        scrub.onSeek = { Hub.cmd("seek", it) }
        info.addView(scrub, a.lp(MATCH, WRAP))
        val ctl = a.hstack(Gravity.CENTER)
        fun ctlBtn(res: Int, size: Float, pad: Float, fn: () -> Unit) = a.glyph(res, Color.WHITE).also { g ->
            g.pad(pad); g.onTap(Press.SCALE) { fn() }
            ctl.addView(g, a.lp(a.dpi(size), a.dpi(size)))
        }
        shuffle = ctlBtn(CR.drawable.ic_shuffle, 54f, 14f) { Hub.player.toggleShuffle() }
        ctl.addView(View(a), a.lp(0, 1, 1f))
        prev = ctlBtn(CR.drawable.ic_prev, 80f, 14f) { Hub.cmd("prev") }
        ctl.addView(View(a), a.lp(a.dpi(18f), 1))
        play = ctlBtn(CR.drawable.ic_play, 96f, 14f) { Hub.cmd("toggle") }
        ctl.addView(View(a), a.lp(a.dpi(18f), 1))
        next = ctlBtn(CR.drawable.ic_next, 80f, 14f) { Hub.cmd("next") }
        ctl.addView(View(a), a.lp(0, 1, 1f))
        repeat = ctlBtn(CR.drawable.ic_repeat, 54f, 14f) { Hub.player.toggleRepeat() }
        info.addView(ctl, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(10f) })
        mid.addView(info, a.lp(0, WRAP, 1f).apply { marginStart = a.dpi(40f) })
        col.addView(mid, a.lp(MATCH, 0, 1f))
        root.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        return root
    }

    private fun bind() {
        val np = Store.np
        tTitle.text = np.title.ifEmpty { "Nic nie gra" }
        tArtist.text = np.artist.ifEmpty { if (np.isEmpty) "Włącz muzykę w telefonie lub wybierz źródło" else "" }
        tAlbum.text = np.album
        play.setImageResource(if (np.playing) CR.drawable.ic_pause else CR.drawable.ic_play)
        scrub.enabledSeek = np.canSeek
        scrub.update(np.livePosition(), np.duration)
        prev.alpha = if (np.canSkip || np.offline) 1f else 0.35f
        next.alpha = prev.alpha
        val off = np.offline
        shuffle.visibility = if (off) View.VISIBLE else View.INVISIBLE
        repeat.visibility = shuffle.visibility
        shuffle.tint(if (np.shuffle) Color.WHITE else 0x59FFFFFF)
        repeat.tint(if (np.repeat) Color.WHITE else 0x59FFFFFF)
        if (off) {
            srcName.text = "W aucie"
            srcIcon.setImageResource(CR.drawable.ic_drive); srcIcon.tint(Color.WHITE)
            srcIcon.background = RoundDrawable(0xFF0A84FF.toInt(), a.dp(6f)); srcIcon.pad(5f)
        } else {
            srcName.text = (if (Store.appActive) "Radio • " else "") + np.appName.ifEmpty { if (Store.connected) "Telefon" else "Brak telefonu" }
            val icon = Store.icons.get(np.pkg)
            if (icon != null) { srcIcon.setImageBitmap(icon); srcIcon.imageTintList = null; srcIcon.background = null; srcIcon.setPadding(0, 0, 0, 0) }
            else { srcIcon.setImageResource(CR.drawable.ic_iphone); srcIcon.tint(Color.WHITE); srcIcon.background = RoundDrawable(0xFF8E8E93.toInt(), a.dp(6f)); srcIcon.pad(5f) }
        }
        // Music-app touch: artwork shrinks when paused, springs back on play
        if (lastPlaying != np.playing) {
            val s = if (np.playing || np.isEmpty) 1f else 0.86f
            if (lastPlaying == null) { artWrap.scaleX = s; artWrap.scaleY = s }
            else artWrap.animate().scaleX(s).scaleY(s).setDuration(480).setInterpolator(Motion.spring).start()
            lastPlaying = np.playing
        }
    }

    private fun bindArt() {
        val bmp = Store.art
        if (bmp != null && bmp === lastArt) return
        lastArt = bmp
        if (bmp != null) {
            art.setImageBitmap(bmp); art.imageTintList = null; art.background = null; art.setPadding(0, 0, 0, 0)
            back.setImageBitmap(Store.backdrop); back.setBackgroundColor(0)
        } else {
            art.setImageResource(CR.drawable.ic_music); art.tint(0x80FFFFFF.toInt())
            art.background = RoundDrawable(0xFF3A3A3C.toInt(), a.dp(14f), 0xFF545458.toInt(), 0xFF2C2C2E.toInt())
            val p = a.dpi(110f); art.setPadding(p, p, p, p)
            back.setImageDrawable(null); back.setBackgroundColor(Store.tint)
        }
        back.alpha = 0.4f; back.animate().alpha(1f).setDuration(500).start()
    }

    override fun onShow() {
        lastArt = null; lastPlaying = null
        bind(); bindArt()
        on(Topic.NP, Topic.SOURCES) { bind() }
        on(Topic.ART) { bindArt() }
        every(500) { val np = Store.np; if (np.playing) scrub.update(np.livePosition(), np.duration) }
    }
}

fun MainActivity.circleGlyph(res: Int, fill: Int, tint: Int, size: Float, fn: () -> Unit): View {
    val f = FrameLayout(this)
    f.background = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply { paint.color = fill }
    f.addView(glyph(res, tint), FrameLayout.LayoutParams(dpi(size * 0.45f), dpi(size * 0.45f), Gravity.CENTER))
    f.onTap(Press.SCALE) { fn() }
    return f
}

/** Which app should play: every media app active on the phone + songs stored in the car. */
class SourcesScreen(a: MainActivity) : Screen(a) {
    override val title = "Źródło"
    private lateinit var list: ListView
    private val adapter = Adapter<Any>({ MediaRow(a) }) { v, item, _ -> bindRow(v as MediaRow, item) }

    override fun build(): View {
        list = a.listView()
        list.adapter = adapter
        return list
    }

    private fun bindRow(r: MediaRow, item: Any) {
        r.eq.visibility = View.GONE
        r.trailing.text = ""
        when (item) {
            "offline" -> {
                val n = Hub.db.all().size
                r.title.text = "W aucie (offline)"
                r.sub.text = "$n ${plural(n, "utwór", "utwory", "utworów")} zapisanych w radiu"
                r.art.setImageResource(CR.drawable.ic_drive); r.art.tint(Color.WHITE)
                r.art.background = RoundDrawable(a.pal.blue, a.dp(10f)); r.art.pad(12f)
                r.eq.visibility = if (Store.localActive && Store.localNp.playing) View.VISIBLE else View.GONE
                r.onClick = { a.push(OfflineScreen(a)) }
            }
            "library" -> {
                r.title.text = "Muzyka z telefonu"
                r.sub.text = "Albumy, wykonawcy i utwory zapisane w telefonie"
                r.art.setImageResource(CR.drawable.ic_iphone); r.art.tint(Color.WHITE)
                r.art.background = RoundDrawable(0xFFFA2D48.toInt(), a.dp(10f)); r.art.pad(12f)
                r.onClick = { a.push(LibraryScreen(a)) }
            }
            "access" -> {
                r.title.text = "Aplikacje na radiu"
                r.sub.text = "Zezwól Boss Drive sterować YouTube Music i dodatkami audio"
                r.art.setImageResource(CR.drawable.ic_apps); r.art.tint(Color.WHITE)
                r.art.background = RoundDrawable(a.pal.indigo, a.dp(10f)); r.art.pad(12f)
                r.onClick = { try { a.startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) } catch (_: Throwable) { a.hud("Brak ustawień dostępu do powiadomień na tym radiu", CR.drawable.ic_info) } }
            }
            is RadioSrc -> {
                val s = item.s
                r.title.text = s.name
                r.sub.text = when { s.playing -> "Na radiu • odtwarzanie"; s.active -> "Na radiu • wstrzymane"; else -> "Na radiu • dotknij, aby otworzyć" }
                val icon = Store.icons.get(s.pkg)
                if (icon != null) { r.art.setImageBitmap(icon); r.art.imageTintList = null; r.art.background = null; r.art.setPadding(0, 0, 0, 0) }
                else { r.art.setImageResource(CR.drawable.ic_music); r.art.tint(Color.WHITE); r.art.background = RoundDrawable(a.pal.gray, a.dp(10f)); r.art.pad(12f) }
                r.eq.visibility = if (s.playing && Store.appActive) View.VISIBLE else View.GONE
                r.onClick = { AppMedia.select(a, s.pkg, pl.boss.drive.radio.Theme.isDark()); a.back() }
            }
            is Source -> {
                r.title.text = item.name
                r.sub.text = when { item.playing -> "Odtwarzanie"; item.active -> "Wstrzymane"; else -> "Dotknij, aby uruchomić" }
                val icon = Store.icons.get(item.pkg)
                if (icon != null) { r.art.setImageBitmap(icon); r.art.imageTintList = null; r.art.background = null; r.art.setPadding(0, 0, 0, 0) }
                else { r.art.setImageResource(CR.drawable.ic_music); r.art.tint(Color.WHITE); r.art.background = RoundDrawable(a.pal.gray, a.dp(10f)); r.art.pad(12f) }
                r.eq.visibility = if (item.playing && !Store.localActive) View.VISIBLE else View.GONE
                r.onClick = { Hub.usePhone(item.pkg); a.back() }
            }
        }
    }

    private fun refresh() {
        val items = ArrayList<Any>()
        items.addAll(Store.sources)
        items.add("library")
        items.add("offline")
        Store.radioSources.forEach { items.add(RadioSrc(it)) }
        if (!AppMedia.hasAccess(a)) items.add("access")
        adapter.items = items
    }

    override fun onShow() { refresh(); on(Topic.SOURCES, Topic.NP, Topic.OFFLINE) { refresh() } }
}

private class RadioSrc(val s: Source)

/** Up Next for the phone's current app (when the app exposes its queue). */
class QueueScreen(a: MainActivity) : Screen(a) {
    override val title = "Kolejka"
    private val adapter = Adapter<QueueItem>({ MediaRow(a) }) { v, q, i ->
        val r = v as MediaRow
        r.title.text = q.title; r.sub.text = q.sub; r.trailing.text = ""
        r.setArt(null)
        val cur = q.id == Store.queueActive
        r.eq.visibility = if (cur) View.VISIBLE else View.GONE
        r.title.setTextColor(if (cur) a.pal.pink else a.pal.label)
        r.onClick = { Hub.playQueue(q.id) }
    }
    private lateinit var box: FrameLayout

    override fun build(): View {
        box = FrameLayout(a)
        val list = a.listView(); list.adapter = adapter
        box.addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
        return box
    }

    private fun refresh() {
        adapter.items = Store.queue
        if (box.childCount > 1) box.removeViewAt(1)
        if (Store.queue.isEmpty()) box.addView(a.emptyState(CR.drawable.ic_list, "Kolejka jest pusta", "Ta aplikacja nie udostępnia kolejki albo nic nie jest odtwarzane."))
    }

    override fun onShow() { refresh(); on(Topic.QUEUE) { refresh() } }
}

/** Phone's local music library (MediaStore), browsed remotely. */
class LibraryScreen(a: MainActivity) : Screen(a) {
    override val title = "Biblioteka"
    private val tabs = listOf("Albumy" to "albums", "Wykonawcy" to "artists", "Utwory" to "songs", "Playlisty" to "playlists")
    private var sel = Hub.prefs.getInt("libTab", 0)
    private lateinit var box: FrameLayout
    private lateinit var list: ListView
    private val adapter = Adapter<LibItem>({ MediaRow(a) }) { v, it, i -> bindRow(v as MediaRow, it, i) }

    override fun build(): View {
        val col = a.vstack()
        val seg = Segmented(a, pal, tabs.map { it.first }, 16f)
        seg.select(sel, false)
        seg.onSelect = { sel = it; Hub.prefs.edit().putInt("libTab", it).apply(); refresh(true) }
        col.addView(seg, a.lp(MATCH, WRAP).apply { setMargins(a.dpi(24f), a.dpi(12f), a.dpi(24f), a.dpi(8f)) })
        box = FrameLayout(a)
        list = a.listView(); list.adapter = adapter
        list.isFastScrollEnabled = true
        box.addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
        col.addView(box, a.lp(MATCH, 0, 1f))
        return col
    }

    private fun req() = tabs[sel].second

    private fun bindRow(r: MediaRow, it: LibItem, i: Int) {
        r.title.text = it.title; r.sub.text = it.sub
        r.trailing.text = if (it.dur > 0) fmtTime(it.dur) else ""
        r.eq.visibility = View.GONE
        r.setArt(null, if (it.kind == "artist") CR.drawable.ic_person else if (it.kind == "playlist") CR.drawable.ic_list else CR.drawable.ic_music)
        r.onClick = {
            if (it.kind == "song") { Hub.playLibrary(req(), i); a.openMusic() }
            else a.push(LibListScreen(a, "${it.kind}:${it.id}", it.title))
        }
    }

    private fun refresh(request: Boolean) {
        val data = Store.library.get(req())
        adapter.items = data?.second ?: emptyList()
        if (box.childCount > 1) box.removeViewAt(1)
        if (data == null) {
            box.addView(a.vstack(Gravity.CENTER).apply { addView(pl.boss.drive.core.ui.Spinner(a, a.pal.gray), a.lp(a.dpi(36f), a.dpi(36f))) })
        } else if (data.second.isEmpty()) box.addView(a.emptyState(CR.drawable.ic_music, "Brak muzyki", "Na telefonie nie ma plików muzycznych w tej kategorii."))
        if (request && Store.connected) Hub.requestLibrary(req())
    }

    override fun onShow() { refresh(true); on(Topic.LIBRARY) { refresh(false) } }
}

/** Songs of one album / artist / playlist from the phone. */
class LibListScreen(a: MainActivity, private val req: String, override val title: String) : Screen(a) {
    private val adapter = Adapter<LibItem>({ MediaRow(a) }) { v, it, i ->
        val r = v as MediaRow
        r.title.text = it.title; r.sub.text = it.sub; r.trailing.text = if (it.dur > 0) fmtTime(it.dur) else ""
        r.eq.visibility = View.GONE; r.setArt(null)
        r.onClick = { Hub.playLibrary(req, i); a.openMusic() }
    }
    private lateinit var box: FrameLayout

    override fun build(): View {
        val col = a.vstack()
        val h = a.hstack()
        h.pad(24f, 14f, 24f, 8f)
        h.addView(a.pillButton("Odtwarzaj", CR.drawable.ic_play, a.pal.fill3, a.pal.pink) { Hub.playLibrary(req, 0); a.openMusic() }, a.lp(0, WRAP, 1f).apply { marginEnd = a.dpi(12f) })
        h.addView(a.pillButton("Losowo", CR.drawable.ic_shuffle, a.pal.fill3, a.pal.pink) { Hub.playLibrary("$req#shuffle", 0); a.openMusic() }, a.lp(0, WRAP, 1f))
        col.addView(h, a.lp(MATCH, WRAP))
        box = FrameLayout(a)
        val list = a.listView(); list.adapter = adapter
        box.addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
        col.addView(box, a.lp(MATCH, 0, 1f))
        return col
    }

    private fun refresh(request: Boolean) {
        val d = Store.library.get(req)
        adapter.items = d?.second ?: emptyList()
        if (box.childCount > 1) box.removeViewAt(1)
        if (d == null) box.addView(a.vstack(Gravity.CENTER).apply { addView(pl.boss.drive.core.ui.Spinner(a, a.pal.gray), a.lp(a.dpi(36f), a.dpi(36f))) })
        if (request) Hub.requestLibrary(req)
    }

    override fun onShow() { refresh(true); on(Topic.LIBRARY) { refresh(false) } }
}

/** Songs stored in the car — works with no phone at all. */
class OfflineScreen(a: MainActivity) : Screen(a) {
    override val title = "W aucie"
    private var tracks: List<Track> = emptyList()
    private lateinit var box: FrameLayout
    private lateinit var count: TextView
    private val adapter = Adapter<Track>({ MediaRow(a) }) { v, t, i ->
        val r = v as MediaRow
        r.title.text = t.title
        r.sub.text = listOf(t.artist, t.album).filter { it.isNotEmpty() }.joinToString(" — ")
        r.trailing.text = if (t.duration > 0) fmtTime(t.duration) else ""
        val cur = Store.localActive && Hub.player.current?.id == t.id
        r.eq.visibility = if (cur) View.VISIBLE else View.GONE
        r.eq.playing = Store.localNp.playing
        r.title.setTextColor(if (cur) a.pal.pink else a.pal.label)
        r.setArt(ArtCache.get(t.art) { notifyLater() })
        r.onClick = { Hub.playOffline(tracks, i); a.openMusic() }
    }
    private var notifyPosted = false
    private fun notifyLater() { if (!notifyPosted) { notifyPosted = true; Bg.main.postDelayed({ notifyPosted = false; adapter.notifyDataSetChanged() }, 120) } }

    override fun build(): View {
        val col = a.vstack()
        val h = a.hstack()
        h.pad(24f, 14f, 24f, 8f)
        count = a.text("", 15f, W.REGULAR, a.pal.label2)
        h.addView(a.pillButton("Odtwarzaj", CR.drawable.ic_play, a.pal.fill3, a.pal.pink) { if (tracks.isNotEmpty()) { Hub.playOffline(tracks, 0); a.openMusic() } }, a.lp(0, WRAP, 1f).apply { marginEnd = a.dpi(12f) })
        h.addView(a.pillButton("Losowo", CR.drawable.ic_shuffle, a.pal.fill3, a.pal.pink) {
            if (tracks.isNotEmpty()) { Hub.playOffline(tracks, (tracks.indices).random(), shuffle = true); a.openMusic() }
        }, a.lp(0, WRAP, 1f))
        col.addView(h, a.lp(MATCH, WRAP))
        col.addView(count, a.lp(WRAP, WRAP).apply { marginStart = a.dpi(26f); bottomMargin = a.dpi(4f) })
        box = FrameLayout(a)
        val list = a.listView(); list.adapter = adapter
        list.isFastScrollEnabled = true
        box.addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
        col.addView(box, a.lp(MATCH, 0, 1f))
        return col
    }

    private fun refresh() {
        Bg.load({ Hub.db.all() }) { t ->
            tracks = t
            adapter.items = t
            val mb = t.sumOf { it.size } / (1024 * 1024)
            count.text = "${t.size} ${plural(t.size, "utwór", "utwory", "utworów")} • $mb MB"
            if (box.childCount > 1) box.removeViewAt(1)
            if (t.isEmpty()) box.addView(a.emptyState(CR.drawable.ic_download, "Brak muzyki w aucie",
                "W aplikacji Boss Drive na telefonie wybierz „Wyślij do auta” — piosenki zapiszą się tutaj i zagrają bez internetu i bez telefonu."))
        }
    }

    override fun onShow() { refresh(); on(Topic.OFFLINE) { refresh() }; on(Topic.NP) { adapter.notifyDataSetChanged() } }
}

