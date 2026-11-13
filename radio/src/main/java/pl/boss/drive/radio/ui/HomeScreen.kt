package pl.boss.drive.radio.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.ScrubBar
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.blend
import pl.boss.drive.core.ui.clipRound
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.glyph
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.iconTile
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.tint
import pl.boss.drive.core.ui.vstack
import pl.boss.drive.radio.Hub
import pl.boss.drive.radio.MainActivity
import pl.boss.drive.radio.Store
import pl.boss.drive.radio.Tab
import pl.boss.drive.radio.Topic
import java.text.SimpleDateFormat
import java.util.Locale

/** CarPlay Dashboard: Now Playing widget + clock/weather + favourites, on the gradient wallpaper. */
class HomeScreen(a: MainActivity) : Screen(a) {
    override val title = "Start"

    // now playing widget
    private lateinit var npBack: ImageView
    private lateinit var npArt: ImageView
    private lateinit var npTitle: TextView
    private lateinit var npArtist: TextView
    private lateinit var npApp: TextView
    private lateinit var npPlay: ImageView
    private lateinit var npScrub: ScrubBar
    private lateinit var npFull: View
    private lateinit var npEmpty: View
    private lateinit var npShade: View
    // clock & weather
    private lateinit var time: TextView
    private lateinit var date: TextView
    private lateinit var wIcon: ImageView
    private lateinit var wTemp: TextView
    private lateinit var wPlace: TextView
    private lateinit var wDesc: TextView
    private lateinit var weatherRow: View
    // favourites
    private lateinit var favBox: FrameLayout
    private lateinit var xferBar: View
    private lateinit var xferText: TextView
    private lateinit var xferProg: ProgressBar
    private var lastArtKey: Any? = null

    override fun build(): View {
        val root = a.hstack(Gravity.FILL)
        root.pad(16f, 16f, 16f, 16f)
        root.addView(buildNowPlaying(), a.lp(0, MATCH, 1.25f).apply { marginEnd = a.dpi(14f) })
        val right = a.vstack()
        right.addView(buildClock(), a.lp(MATCH, 0, 1f))
        right.addView(buildFavs(), a.lp(MATCH, 0, 1.15f).apply { topMargin = a.dpi(14f) })
        root.addView(right, a.lp(0, MATCH, 1f))
        return root
    }

    // ------------------------------------------------------------------ now playing

    private fun buildNowPlaying(): View {
        val c = a.card(20f)
        npBack = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        c.addView(npBack, FrameLayout.LayoutParams(MATCH, MATCH))
        npShade = View(a).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x40000000, 0x99000000.toInt())) }
        c.addView(npShade, FrameLayout.LayoutParams(MATCH, MATCH))

        val full = a.vstack()
        full.pad(22f, 22f, 22f, 16f)
        val top = a.hstack(Gravity.TOP)
        npArt = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipRound(dp(10f)); elevation = dp(10f) }
        top.addView(npArt, a.lp(a.dpi(196f), a.dpi(196f)))
        val info = a.vstack()
        npApp = a.text("", 13f, W.SEMIBOLD, 0x99FFFFFF.toInt())
        npTitle = a.text("", 26f, W.BOLD, Color.WHITE, lines = 3)
        npArtist = a.text("", 19f, W.REGULAR, 0xB3FFFFFF.toInt(), lines = 3)
        info.addView(npApp)
        info.addView(npTitle, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(6f) })
        info.addView(npArtist, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(4f) })
        top.addView(info, a.lp(0, WRAP, 1f).apply { marginStart = a.dpi(20f); topMargin = a.dpi(4f) })
        top.onTap(Press.NONE) { a.openMusic() }
        full.addView(top, a.lp(MATCH, WRAP))
        full.addView(View(a), a.lp(1, 0, 1f))
        npScrub = ScrubBar(a)
        npScrub.onSeek = { Hub.cmd("seek", it) }
        full.addView(npScrub, a.lp(MATCH, WRAP))
        val ctl = a.hstack(Gravity.CENTER)
        fun btn(res: Int, size: Float, fn: () -> Unit): ImageView = a.glyph(res, Color.WHITE).also { g ->
            g.pad(size * 0.14f); g.onTap(Press.SCALE) { fn() }
            ctl.addView(g, a.lp(a.dpi(size), a.dpi(size)).apply { marginStart = a.dpi(22f); marginEnd = a.dpi(22f) })
        }
        btn(CR.drawable.ic_prev, 60f) { Hub.cmd("prev") }
        npPlay = btn(CR.drawable.ic_play, 76f) { Hub.cmd("toggle") }
        btn(CR.drawable.ic_next, 60f) { Hub.cmd("next") }
        full.addView(ctl, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(2f) })
        npFull = full
        c.addView(full, FrameLayout.LayoutParams(MATCH, MATCH))

        val empty = a.vstack(Gravity.CENTER)
        empty.pad(24f)
        empty.addView(a.glyph(CR.drawable.ic_music, 0x66FFFFFF), a.lp(a.dpi(60f), a.dpi(60f)))
        empty.addView(a.text("Nic nie gra", 26f, W.BOLD, Color.WHITE, gravity = Gravity.CENTER), a.lp(WRAP, WRAP).apply { topMargin = a.dpi(14f) })
        empty.addView(a.text("Włącz muzykę na telefonie albo posłuchaj piosenek zapisanych w aucie.", 16f, W.REGULAR, 0x99FFFFFF.toInt(), lines = 3, gravity = Gravity.CENTER),
            a.lp(MATCH, WRAP).apply { topMargin = a.dpi(6f) })
        val pills = a.hstack(Gravity.CENTER)
        pills.addView(a.pillButton("Wznów", CR.drawable.ic_play, 0x33FFFFFF, Color.WHITE) { Hub.cmd("play") }, a.lp(WRAP, WRAP).apply { marginEnd = a.dpi(10f) })
        pills.addView(a.pillButton("W aucie", CR.drawable.ic_drive, 0x33FFFFFF, Color.WHITE) { a.select(Tab.MUSIC); a.push(OfflineScreen(a)) })
        empty.addView(pills, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(20f) })
        npEmpty = empty
        c.addView(empty, FrameLayout.LayoutParams(MATCH, MATCH))
        return c
    }

    private fun bindNp() {
        val np = Store.np
        val has = !np.isEmpty
        npFull.visibility = if (has) View.VISIBLE else View.GONE
        npEmpty.visibility = if (has) View.GONE else View.VISIBLE
        npTitle.text = np.title
        npArtist.text = listOf(np.artist, np.album).filter { it.isNotEmpty() }.joinToString(" — ")
        npApp.text = if (Store.audioOnPhone) "DŹWIĘK GRA Z TELEFONU — POŁĄCZ BLUETOOTH" else (if (np.offline) "W AUCIE" else np.appName.uppercase()).ifEmpty { "TELEFON" }
        npApp.setTextColor(if (Store.audioOnPhone) 0xFFFFB340.toInt() else 0x99FFFFFF.toInt())
        npPlay.setImageResource(if (np.playing) CR.drawable.ic_pause else CR.drawable.ic_play)
        npScrub.enabledSeek = np.canSeek
        npScrub.update(np.livePosition(), np.duration)
    }

    private fun bindArt() {
        val art = Store.art
        val key = art ?: Store.np.artKey
        if (key === lastArtKey && art != null) return
        lastArtKey = key
        if (art != null) {
            npArt.setImageBitmap(art); npArt.background = null; npArt.setPadding(0, 0, 0, 0); npArt.imageTintList = null
            npBack.setImageBitmap(Store.backdrop)
            npBack.background = null
        } else {
            npArt.setImageResource(CR.drawable.ic_music); npArt.tint(0x99FFFFFF.toInt())
            val p = a.dpi(56f); npArt.setPadding(p, p, p, p)
            npArt.background = RoundDrawable(0xFF48484A.toInt(), a.dp(10f), 0xFF5A5A5E.toInt(), 0xFF3A3A3C.toInt())
            npBack.setImageDrawable(null)
            npBack.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF2C2C2E.toInt(), 0xFF1C1C1E.toInt()))
        }
        npArt.alpha = 0.3f; npArt.animate().alpha(1f).setDuration(300).start()
    }

    // ------------------------------------------------------------------ clock & weather

    private fun buildClock(): View {
        val c = a.card(20f)
        val v = a.vstack()
        v.pad(24f, 18f, 22f, 16f)
        time = a.text("", 60f, W.BOLD, pal.label)
        date = a.text("", 18f, W.MEDIUM, pal.label2)
        v.addView(time)
        v.addView(date, a.lp(WRAP, WRAP).apply { topMargin = a.dpi(4f) })
        v.addView(View(a), a.lp(1, 0, 1f))
        val w = a.hstack()
        wIcon = ImageView(a)
        w.addView(wIcon, a.lp(a.dpi(42f), a.dpi(42f)))
        wTemp = a.text("", 34f, W.SEMIBOLD, pal.label)
        w.addView(wTemp, a.lp(WRAP, WRAP).apply { marginStart = a.dpi(10f) })
        val wc = a.vstack()
        wPlace = a.text("", 16f, W.SEMIBOLD, pal.label)
        wDesc = a.text("", 14f, W.REGULAR, pal.label2)
        wc.addView(wPlace); wc.addView(wDesc)
        w.addView(wc, a.lp(0, WRAP, 1f).apply { marginStart = a.dpi(14f) })
        weatherRow = w
        v.addView(w, a.lp(MATCH, WRAP))
        c.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        return c
    }

    private fun bindClock() {
        val now = System.currentTimeMillis()
        time.text = android.text.format.DateFormat.format("H:mm", now)
        date.text = SimpleDateFormat("EEEE, d MMMM", Locale("pl")).format(now).replaceFirstChar { it.uppercase() }
        val w = Store.weather
        weatherRow.visibility = if (w == null) View.INVISIBLE else View.VISIBLE
        if (w != null) {
            val (res, color, desc) = weatherIcon(w.code, w.isDay)
            wIcon.setImageResource(res); wIcon.tint(color)
            wTemp.text = "${w.temp}°"
            wPlace.text = w.place.ifEmpty { desc }
            wDesc.text = (if (w.place.isEmpty()) "" else "$desc  ") + "↑${w.hi}° ↓${w.lo}°"
        }
    }

    private fun weatherIcon(code: Int, day: Boolean): Triple<Int, Int, String> {
        val sun = 0xFFFFCC00.toInt(); val cloud = if (pal.dark) 0xFFE5E5EA.toInt() else 0xFF8E8E93.toInt(); val blue = 0xFF5AC8FA.toInt()
        return when (code) {
            0 -> if (day) Triple(CR.drawable.ic_sun, sun, "Bezchmurnie") else Triple(CR.drawable.ic_moon, 0xFFE5E5EA.toInt(), "Bezchmurnie")
            1, 2 -> Triple(CR.drawable.ic_cloud_sun, if (day) sun else cloud, "Częściowe zachmurzenie")
            3 -> Triple(CR.drawable.ic_cloud, cloud, "Pochmurno")
            45, 48 -> Triple(CR.drawable.ic_fog, cloud, "Mgła")
            in 51..57 -> Triple(CR.drawable.ic_cloud_rain, blue, "Mżawka")
            in 61..67, in 80..82 -> Triple(CR.drawable.ic_cloud_rain, blue, "Deszcz")
            in 71..77, 85, 86 -> Triple(CR.drawable.ic_cloud_snow, cloud, "Śnieg")
            in 95..99 -> Triple(CR.drawable.ic_cloud_bolt, 0xFFFFCC00.toInt(), "Burza")
            else -> Triple(CR.drawable.ic_cloud, cloud, "")
        }
    }

    // ------------------------------------------------------------------ favourites / connection

    private fun buildFavs(): View {
        val c = a.card(20f)
        favBox = FrameLayout(a)
        c.addView(favBox, FrameLayout.LayoutParams(MATCH, MATCH))
        val x = a.vstack()
        x.background = RoundDrawable(if (pal.dark) 0xFF2C2C2E.toInt() else 0xFFF2F2F7.toInt(), a.dp(14f))
        x.pad(14f, 10f, 14f, 12f)
        xferText = a.text("", 14f, W.MEDIUM, pal.label)
        x.addView(xferText)
        xferProg = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progressDrawable = progressDrawable(pal.blue, pal.fill3)
        }
        x.addView(xferProg, a.lp(MATCH, a.dpi(4f)).apply { topMargin = a.dpi(8f) })
        xferBar = x
        c.addView(x, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { setMargins(a.dpi(12f), 0, a.dpi(12f), a.dpi(12f)) })
        return c
    }

    private fun progressDrawable(fg: Int, bg: Int): android.graphics.drawable.Drawable {
        val r = a.dp(2f)
        val back = GradientDrawable().apply { setColor(bg); cornerRadius = r }
        val front = android.graphics.drawable.ClipDrawable(GradientDrawable().apply { setColor(fg); cornerRadius = r }, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)
        return android.graphics.drawable.LayerDrawable(arrayOf(back, front)).apply { setId(0, android.R.id.background); setId(1, android.R.id.progress) }
    }

    private fun bindFavs() {
        favBox.removeAllViews()
        val v = a.vstack()
        v.pad(22f, 18f, 22f, 16f)
        if (!Store.connected) {
            val h = a.hstack()
            h.addView(a.iconTile(CR.drawable.ic_iphone, pal.blue, 44f, 0.6f), a.lp(a.dpi(44f), a.dpi(44f)))
            h.addView(a.text("Połącz telefon", 22f, W.BOLD, pal.label), a.lp(0, WRAP, 1f).apply { marginStart = a.dpi(14f) })
            v.addView(h)
            v.addView(a.text("Włącz hotspot w telefonie i aplikację Boss Drive. Radio połączy się samo — przez Wi-Fi albo Bluetooth.",
                16f, W.REGULAR, pal.label2, lines = 4), a.lp(MATCH, WRAP).apply { topMargin = a.dpi(12f) })
            v.addView(View(a), a.lp(1, 0, 1f))
            v.addView(a.pillButton("Ustawienia Wi-Fi", CR.drawable.ic_wifi, pal.fill3, pal.blue) { Hub.openWifiSettings(a) }, a.lp(WRAP, WRAP))
            favBox.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
            return
        }
        val head = a.hstack()
        head.addView(a.text("Ulubione", 20f, W.BOLD, pal.label), a.lp(0, WRAP, 1f))
        head.addView(a.text("Telefon", 16f, W.REGULAR, pal.blue))
        head.addView(a.glyph(CR.drawable.ic_chevron_right, pal.blue), a.lp(a.dpi(16f), a.dpi(16f)))
        head.onTap { a.select(Tab.PHONE) }
        v.addView(head, a.lp(MATCH, WRAP))
        val favs = Store.favorites.take(4)
        if (favs.isEmpty()) {
            v.addView(a.text("Dodaj ulubione kontakty w telefonie (gwiazdka), a pojawią się tutaj.", 16f, W.REGULAR, pal.label2, lines = 3),
                a.lp(MATCH, WRAP).apply { topMargin = a.dpi(12f) })
        } else {
            val row = a.hstack(Gravity.TOP)
            for (f in favs) {
                val cell = a.vstack(Gravity.CENTER_HORIZONTAL)
                val av = Avatar(a); av.set(f.name, Store.photos.get(f.photo))
                cell.addView(av, a.lp(a.dpi(66f), a.dpi(66f)))
                cell.addView(a.text(f.name.substringBefore(' '), 14f, W.MEDIUM, pal.label, gravity = Gravity.CENTER), a.lp(MATCH, WRAP).apply { topMargin = a.dpi(7f) })
                cell.onTap(Press.SCALE) { Hub.call("dial", f.number); a.hud("Dzwonię: ${f.name}", CR.drawable.ic_phone) }
                row.addView(cell, a.lp(0, WRAP, 1f))
            }
            v.addView(row, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(16f) })
        }
        val missed = Store.recents.count { it.type == 3 && System.currentTimeMillis() - it.time < 12 * 3_600_000 }
        val unread = Store.unread
        if (missed + unread > 0) {
            val info = listOfNotNull(
                if (missed > 0) "$missed ${plural(missed, "nieodebrane", "nieodebrane", "nieodebranych")}" else null,
                if (unread > 0) "$unread ${plural(unread, "wiadomość", "wiadomości", "wiadomości")}" else null,
            ).joinToString(" • ")
            v.addView(a.text(info, 15f, W.MEDIUM, pal.red), a.lp(MATCH, WRAP).apply { topMargin = a.dpi(12f) })
        }
        favBox.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun bindTransfer() {
        val t = Store.transfer
        xferBar.visibility = if (t == null) View.GONE else View.VISIBLE
        if (t != null) {
            val pct = if (t.size > 0) (t.done * 100 / t.size).toInt() else 0
            val more = if (t.queued > 0) "  (+${t.queued})" else ""
            xferText.text = "Zapisuję w aucie: ${t.title} — $pct%$more"
            xferProg.progress = if (t.size > 0) (t.done * 1000 / t.size).toInt() else 0
        }
    }

    override fun onShow() {
        bindNp(); lastArtKey = null; bindArt(); bindClock(); bindFavs(); bindTransfer()
        on(Topic.NP, Topic.STATUS) { bindNp() }
        on(Topic.ART) { bindArt() }
        on(Topic.WEATHER) { bindClock() }
        on(Topic.LINK, Topic.CONTACTS, Topic.RECENTS, Topic.MESSAGES) { bindFavs() }
        on(Topic.TRANSFER) { bindTransfer() }
        every(1000) {
            val np = Store.np
            if (np.playing) npScrub.update(np.livePosition(), np.duration)
            if (System.currentTimeMillis() / 1000 % 15 == 0L) bindClock()
        }
    }
}

fun plural(n: Int, one: String, few: String, many: String): String = when {
    n == 1 -> one
    n % 10 in 2..4 && n % 100 !in 12..14 -> few
    else -> many
}
