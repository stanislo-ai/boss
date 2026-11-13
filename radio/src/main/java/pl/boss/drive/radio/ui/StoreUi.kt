package pl.boss.drive.radio.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.addon.Addon
import pl.boss.drive.core.addon.Addons
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.Spinner
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.clipRound
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.glyph
import pl.boss.drive.core.ui.hairline
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.iconTile
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack
import pl.boss.drive.radio.AddonStore
import pl.boss.drive.radio.AddonStore.State
import pl.boss.drive.radio.AppMedia
import pl.boss.drive.radio.MainActivity
import pl.boss.drive.radio.Store
import pl.boss.drive.radio.Tab
import pl.boss.drive.radio.Theme
import pl.boss.drive.radio.Topic

/** App Store style download ring with a stop square. */
class ProgressRing(ctx: Context, private val color: Int, private val track: Int) : View(ctx) {
    var pct = 0; set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val r = RectF()
    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat(); val sw = s * 0.09f
        r.set(sw, sw, s - sw, s - sw)
        p.style = Paint.Style.STROKE; p.strokeWidth = sw
        p.color = track; c.drawArc(r, 0f, 360f, false, p)
        p.color = color; c.drawArc(r, -90f, 360f * pct / 100f, false, p)
        p.style = Paint.Style.FILL; val q = s * 0.16f
        c.drawRoundRect(s / 2 - q, s / 2 - q, s / 2 + q, s / 2 + q, q * 0.3f, q * 0.3f, p)
    }
}

/** GET / OPEN / UPDATE capsule, or a progress ring, depending on the add-on state. */
fun MainActivity.storeButton(addon: Addon, big: Boolean = false, onAfter: () -> Unit = {}): View {
    val st = AddonStore.state(this, addon)
    val h = if (big) 40f else 32f
    return when (st) {
        State.DOWNLOADING -> FrameLayout(this).apply {
            addView(ProgressRing(this@storeButton, pal.blue, pal.fill3).also { it.pct = AddonStore.progress[addon.pkg] ?: 0 }, FrameLayout.LayoutParams(dpi(h), dpi(h), Gravity.CENTER))
            minimumWidth = dpi(if (big) 120f else 92f)
        }
        State.INSTALLING -> FrameLayout(this).apply {
            addView(Spinner(this@storeButton, pal.gray), FrameLayout.LayoutParams(dpi(h * 0.8f), dpi(h * 0.8f), Gravity.CENTER)); minimumWidth = dpi(if (big) 120f else 92f)
        }
        else -> {
            val label = when (st) { State.OPEN -> "OTWÓRZ"; State.UPDATE -> "AKTUALIZUJ"; else -> "POBIERZ" }
            val filled = big && st != State.UNAVAILABLE
            text(label, if (big) 17f else 15f, W.BOLD, if (filled) Color.WHITE else if (st == State.UNAVAILABLE) pal.gray else pal.blue, gravity = Gravity.CENTER).apply {
                background = RoundDrawable(if (filled) pal.blue else pal.fill3, dp(h / 2))
                minWidth = dpi(if (big) 120f else 92f); setPadding(dpi(16f), 0, dpi(16f), 0); height = dpi(h)
                letterSpacing = 0.02f
                onTap(Press.SCALE) {
                    when (st) {
                        State.OPEN -> if (addon.audio) AppMedia.select(this@storeButton, addon.pkg, Theme.isDark()) else AddonStore.open(this@storeButton, addon.pkg, Theme.isDark())
                        State.GET, State.UPDATE -> AddonStore.get(this@storeButton, addon) {
                            alert("Pozwól na instalację", "Radio musi pozwolić Boss Drive instalować dodatki. Za chwilę otworzą się ustawienia — włącz przełącznik i wróć.",
                                listOf("Anuluj" to 0, "Ustawienia" to 1)) { if (it == 1) AddonStore.openInstallSettings(this@storeButton) }
                        }
                        State.UNAVAILABLE -> hud("Połącz telefon z Boss Drive — dodatki pobierają się z niego", CR.drawable.ic_iphone)
                        else -> {}
                    }
                    onAfter()
                }
            }
        }
    }
}

class StoreScreen(a: MainActivity) : Screen(a) {
    override val title = "Sklep"
    private lateinit var body: LinearLayout

    override fun build(): View {
        val sv = ScrollView(a)
        sv.setBackgroundColor(pal.bg)
        val col = a.vstack()
        col.pad(28f, 18f, 28f, 30f)
        col.addView(a.text("DODATKI DO BOSS DRIVE", 14f, W.SEMIBOLD, pal.label2))
        col.addView(a.text("Sklep", 34f, W.BOLD, pal.label), a.lp(WRAP, WRAP).apply { topMargin = a.dpi(2f) })
        body = a.vstack()
        col.addView(body, a.lp(MATCH, WRAP))
        sv.addView(col)
        return sv
    }

    private fun rebuild() {
        body.removeAllViews()
        val featured = Addons.ALL.first()
        body.addView(featuredCard(featured), a.lp(MATCH, a.dpi(214f)).apply { topMargin = a.dpi(16f) })
        body.addView(a.text("Wszystkie dodatki", 24f, W.BOLD, pal.label), a.lp(WRAP, WRAP).apply { topMargin = a.dpi(26f); bottomMargin = a.dpi(6f) })
        val items = Addons.ALL.drop(1)
        val cols = a.hstack(Gravity.TOP)
        listOf(items.filterIndexed { i, _ -> i % 2 == 0 }, items.filterIndexed { i, _ -> i % 2 == 1 }).forEachIndexed { ci, list ->
            val c = a.vstack()
            list.forEachIndexed { i, ad -> c.addView(row(ad, last = i == list.size - 1), a.lp(MATCH, WRAP)) }
            cols.addView(c, a.lp(0, WRAP, 1f).apply { if (ci == 0) marginEnd = a.dpi(30f) })
        }
        body.addView(cols, a.lp(MATCH, WRAP))
        body.addView(a.text("Dodatki pobierają się z telefonu przez Boss Drive — bez internetu. Każdy waży ok. 100 KB i zajmuje pamięć tylko wtedy, gdy jest otwarty. " +
            "YouTube Music instaluje się ze Sklepu Google Play na radiu.", 14f, W.REGULAR, pal.label3, lines = 4), a.lp(MATCH, WRAP).apply { topMargin = a.dpi(22f) })
    }

    private fun featuredCard(ad: Addon): View {
        val f = FrameLayout(a)
        f.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ad.color, ad.color2)).apply { cornerRadius = a.dp(22f) }
        f.clipRound(a.dp(22f))
        // soft decorative glyph
        f.addView(a.glyph(ad.glyph, 0x22FFFFFF), FrameLayout.LayoutParams(a.dpi(300f), a.dpi(300f), Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = a.dpi(-40f) })
        val h = a.hstack()
        h.pad(28f, 22f, 28f, 22f)
        val col = a.vstack()
        col.addView(a.text("POLECANE", 14f, W.SEMIBOLD, 0xB3FFFFFF.toInt()))
        col.addView(a.text("${ad.name} na radiu", 30f, W.BOLD, Color.WHITE), a.lp(WRAP, WRAP).apply { topMargin = a.dpi(4f) })
        col.addView(a.text("Playlisty offline, które same się aktualizują. Sterowanie z kierownicy.", 17f, W.REGULAR, 0xE6FFFFFF.toInt(), lines = 2), a.lp(MATCH, WRAP).apply { topMargin = a.dpi(6f) })
        col.addView(View(a), a.lp(1, 0, 1f))
        val row = a.hstack()
        row.addView(a.iconTile(ad.glyph, ad.color, 52f, 0.58f).apply { background = RoundDrawable(Color.WHITE, a.dp(12f)); (getChildAt(0) as android.widget.ImageView).imageTintList = android.content.res.ColorStateList.valueOf(ad.color) },
            a.lp(a.dpi(52f), a.dpi(52f)))
        val t = a.vstack()
        t.addView(a.text(ad.name, 18f, W.SEMIBOLD, Color.WHITE)); t.addView(a.text(ad.subtitle, 14f, W.REGULAR, 0xB3FFFFFF.toInt()))
        row.addView(t, a.lp(0, WRAP, 1f).apply { marginStart = a.dpi(12f) })
        row.addView(a.storeButton(ad) { rebuild() }.apply { (this as? android.widget.TextView)?.let { tv -> tv.background = RoundDrawable(0x40FFFFFF, a.dp(16f)); tv.setTextColor(Color.WHITE) } })
        col.addView(row, a.lp(MATCH, WRAP))
        h.addView(col, a.lp(0, MATCH, 1f))
        f.addView(h, FrameLayout.LayoutParams(MATCH, MATCH))
        f.onTap(Press.NONE) { a.push(StoreDetail(a, ad)) }
        return f
    }

    private fun row(ad: Addon, last: Boolean): View {
        val f = FrameLayout(a)
        val h = a.hstack()
        h.pad(0f, 12f, 0f, 12f)
        h.addView(a.iconTile(ad.glyph, ad.color, 64f, 0.56f), a.lp(a.dpi(64f), a.dpi(64f)))
        val c = a.vstack()
        c.addView(a.text(ad.name, 18f, W.SEMIBOLD, pal.label))
        c.addView(a.text(ad.subtitle, 15f, W.REGULAR, pal.label2), a.lp(MATCH, WRAP).apply { topMargin = a.dpi(2f) })
        c.addView(a.text(ad.category, 13f, W.REGULAR, pal.label3), a.lp(MATCH, WRAP).apply { topMargin = a.dpi(2f) })
        h.addView(c, a.lp(0, WRAP, 1f).apply { marginStart = a.dpi(14f); marginEnd = a.dpi(10f) })
        h.addView(a.storeButton(ad) { rebuild() })
        f.addView(h, FrameLayout.LayoutParams(MATCH, WRAP))
        if (!last) f.addView(a.hairline(pal.separator), FrameLayout.LayoutParams(MATCH, maxOf(1, a.dpi(0.5f)), Gravity.BOTTOM).apply { leftMargin = a.dpi(78f) })
        f.onTap(Press.NONE) { a.push(StoreDetail(a, ad)) }
        return f
    }

    override fun onShow() { rebuild(); on(Topic.STORE, Topic.LINK) { rebuild() } }
}

class StoreDetail(a: MainActivity, private val ad: Addon) : Screen(a) {
    override val title = ad.name
    private lateinit var body: LinearLayout

    override fun build(): View {
        val sv = ScrollView(a)
        sv.setBackgroundColor(pal.bg)
        body = a.vstack()
        body.pad(30f, 22f, 30f, 30f)
        sv.addView(body)
        return sv
    }

    private fun rebuild() {
        body.removeAllViews()
        val top = a.hstack(Gravity.TOP)
        top.addView(a.iconTile(ad.glyph, ad.color, 128f, 0.56f), a.lp(a.dpi(128f), a.dpi(128f)))
        val c = a.vstack()
        c.addView(a.text(ad.name, 30f, W.BOLD, pal.label))
        c.addView(a.text(ad.subtitle, 18f, W.REGULAR, pal.label2), a.lp(MATCH, WRAP).apply { topMargin = a.dpi(2f) })
        c.addView(View(a), a.lp(1, a.dpi(22f)))
        val btns = a.hstack()
        btns.addView(a.storeButton(ad, big = true) { rebuild() })
        if (AddonStore.installedVersion(a, ad.pkg) > 0) btns.addView(a.text("Usuń", 17f, W.REGULAR, pal.red).apply { pad(18f, 8f, 8f, 8f); onTap { AddonStore.uninstall(a, ad.pkg) } })
        c.addView(btns)
        top.addView(c, a.lp(0, WRAP, 1f).apply { marginStart = a.dpi(24f) })
        body.addView(top)
        body.addView(a.hairline(pal.separator), a.lp(MATCH, maxOf(1, a.dpi(0.5f))).apply { topMargin = a.dpi(22f) })
        // info strip like the App Store
        val info = a.hstack()
        val size = AddonStore.available[ad.pkg]?.first
        listOf("KATEGORIA" to ad.category, "ŹRÓDŁO" to (if (ad.external) "Google Play" else "Boss Drive"),
            "ROZMIAR" to (if (size != null) "${size / 1024} KB" else "—"), "WERSJA" to "${ad.version}.0").forEachIndexed { i, (k, v) ->
            if (i > 0) info.addView(View(a).apply { setBackgroundColor(pal.separator) }, a.lp(maxOf(1, a.dpi(0.5f)), a.dpi(40f)))
            info.addView(a.vstack(Gravity.CENTER_HORIZONTAL).apply {
                addView(a.text(k, 12f, W.SEMIBOLD, pal.label3), a.lp(WRAP, WRAP)); addView(a.text(v, 18f, W.BOLD, pal.label2), a.lp(WRAP, WRAP).apply { topMargin = a.dpi(4f) })
            }, a.lp(0, WRAP, 1f))
        }
        body.addView(info, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(14f) })
        body.addView(a.hairline(pal.separator), a.lp(MATCH, maxOf(1, a.dpi(0.5f))).apply { topMargin = a.dpi(14f) })
        val two = a.hstack(Gravity.TOP)
        two.addView(a.text(ad.description, 18f, W.REGULAR, pal.label, lines = 0).apply { setLineSpacing(0f, 1.2f) }, a.lp(0, WRAP, 1.2f).apply { marginEnd = a.dpi(30f) })
        val feats = a.vstack()
        feats.addView(a.text("Co potrafi", 20f, W.BOLD, pal.label), a.lp(WRAP, WRAP).apply { bottomMargin = a.dpi(8f) })
        ad.features.forEach { f ->
            feats.addView(a.hstack(Gravity.TOP).apply {
                addView(a.glyph(CR.drawable.ic_check, ad.color), a.lp(a.dpi(20f), a.dpi(20f)).apply { topMargin = a.dpi(2f); marginEnd = a.dpi(10f) })
                addView(a.text(f, 16f, W.REGULAR, pal.label, lines = 3), a.lp(0, WRAP, 1f))
            }, a.lp(MATCH, WRAP).apply { bottomMargin = a.dpi(10f) })
        }
        two.addView(feats, a.lp(0, WRAP, 1f))
        body.addView(two, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(20f) })
    }

    override fun onShow() { rebuild(); on(Topic.STORE, Topic.LINK) { rebuild() } }
}

/** CarPlay home screen: big app icons with names, on the wallpaper. */
class AppsScreen(a: MainActivity) : Screen(a) {
    override val title = "Aplikacje"
    private lateinit var grid: LinearLayout

    override fun build(): View {
        grid = a.vstack(Gravity.CENTER)
        grid.pad(30f, 30f, 30f, 30f)
        return grid
    }

    private data class Item(val name: String, val glyph: Int, val color: Int, val badge: Boolean = false, val go: () -> Unit)

    private fun rebuild() {
        grid.removeAllViews()
        val items = ArrayList<Item>()
        items += Item("Muzyka", CR.drawable.ic_music, 0xFFFA2D48.toInt()) { a.select(Tab.MUSIC) }
        items += Item("Telefon", CR.drawable.ic_phone, 0xFF34C759.toInt()) { a.select(Tab.PHONE) }
        items += Item("Wiadomości", CR.drawable.ic_message, 0xFF2FD158.toInt()) { a.select(Tab.MESSAGES) }
        for (ad in Addons.ALL) if (AddonStore.installedVersion(a, ad.pkg) > 0) items += Item(ad.name, ad.glyph, ad.color) {
            if (ad.audio) AppMedia.select(a, ad.pkg, Theme.isDark()) else AddonStore.open(a, ad.pkg, Theme.isDark())
        }
        val updates = Addons.ALL.any { AddonStore.state(a, it) == State.UPDATE }
        items += Item("Sklep", CR.drawable.ic_bag, 0xFF0A84FF.toInt(), badge = updates) { a.select(Tab.STORE) }
        items += Item("Ustawienia", CR.drawable.ic_gear, 0xFF8E8E93.toInt()) { a.select(Tab.SETTINGS) }
        items.chunked(5).forEach { rowItems ->
            val row = a.hstack(Gravity.TOP)
            rowItems.forEach { it2 ->
                val cell = a.vstack(Gravity.CENTER_HORIZONTAL)
                val icon = FrameLayout(a)
                icon.addView(a.iconTile(it2.glyph, it2.color, 92f, 0.54f), FrameLayout.LayoutParams(a.dpi(92f), a.dpi(92f)))
                if (it2.badge) icon.addView(View(a).apply { background = RoundDrawable(0xFFFF3B30.toInt(), a.dp(10f)) },
                    FrameLayout.LayoutParams(a.dpi(20f), a.dpi(20f), Gravity.TOP or Gravity.END).apply { topMargin = a.dpi(-5f); rightMargin = a.dpi(-5f) })
                cell.addView(icon, a.lp(a.dpi(92f), a.dpi(92f)))
                cell.addView(a.text(it2.name, 16f, W.MEDIUM, pal.label, gravity = Gravity.CENTER), a.lp(MATCH, WRAP).apply { topMargin = a.dpi(9f) })
                cell.onTap(Press.SCALE) { it2.go() }
                row.addView(cell, a.lp(a.dpi(150f), WRAP))
            }
            grid.addView(row, a.lp(WRAP, WRAP).apply { bottomMargin = a.dpi(26f) })
        }
    }

    override fun onShow() { rebuild(); on(Topic.STORE, Topic.SOURCES) { rebuild() } }
}

