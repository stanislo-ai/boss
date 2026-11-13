package pl.boss.drive.radio.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.net.Discovery
import pl.boss.drive.core.ui.IosSwitch
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.Row
import pl.boss.drive.core.ui.Segmented
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.addRows
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.section
import pl.boss.drive.core.ui.sectionFooter
import pl.boss.drive.core.ui.sectionHeader
import pl.boss.drive.core.ui.vstack
import pl.boss.drive.core.util.Bg
import pl.boss.drive.radio.BuildConfigCompat
import pl.boss.drive.radio.AppMedia
import pl.boss.drive.radio.Hub
import pl.boss.drive.radio.MainActivity
import pl.boss.drive.radio.Store
import pl.boss.drive.radio.Theme
import pl.boss.drive.radio.Topic

/** iOS Settings-style grouped list. Rebuilt on show (cheap: ~20 rows). */
class SettingsScreen(a: MainActivity) : Screen(a) {
    override val title = "Ustawienia"
    private lateinit var body: LinearLayout

    override fun build(): View {
        val sv = ScrollView(a)
        sv.setBackgroundColor(pal.groupedBg)
        val outer = a.vstack(Gravity.CENTER_HORIZONTAL)
        outer.addView(a.largeTitle("Ustawienia"), a.lp(MATCH, WRAP))
        body = a.vstack()
        outer.addView(body, a.lp(a.dpi(680f), WRAP).apply { bottomMargin = a.dpi(40f) })
        sv.addView(outer)
        return sv
    }

    private fun toggle(key: String, def: Boolean, onChange: (Boolean) -> Unit = {}) = IosSwitch(a, pal).apply {
        set(Hub.prefs.getBoolean(key, def), false)
        this.onChange = { Hub.prefs.edit().putBoolean(key, it).apply(); onChange(it) }
    }

    private fun rebuild() {
        body.removeAllViews()
        val p = pal
        // ---- phone
        body.addView(a.sectionHeader(p, "Telefon"))
        val s1 = a.section(p)
        val conn = if (Store.connected) Row(a, p, Store.phoneName.ifEmpty { "Telefon" }, if (Store.transport == "wifi") "Połączono przez Wi-Fi" else "Połączono przez Bluetooth",
            icon = CR.drawable.ic_iphone, iconColor = p.green, minHeightDp = 60f)
        else Row(a, p, "Brak połączenia", "Szukam telefonu z aplikacją Boss Drive…", icon = CR.drawable.ic_iphone, iconColor = p.gray, minHeightDp = 60f)
        val wifi = Row(a, p, "Wi-Fi radia", "Połącz radio z hotspotem telefonu", icon = CR.drawable.ic_wifi, iconColor = p.blue, chevron = true)
        wifi.onTap(Press.HIGHLIGHT, p.cellPressed) { Hub.openWifiSettings(a) }
        val rows = mutableListOf<View>(conn, wifi)
        val pairedName = Hub.prefs.getString("pairedName", "") ?: ""
        if (pairedName.isNotEmpty()) {
            val forget = Row(a, p, "Zapomnij „$pairedName”", titleColor = p.red)
            forget.onTap(Press.HIGHLIGHT, p.cellPressed) {
                a.alert("Zapomnieć telefon?", "Radio połączy się z pierwszym telefonem, który się zgłosi.", listOf("Anuluj" to 0, "Zapomnij" to 2)) { if (it == 2) { Hub.forgetPhone(); rebuild() } }
            }
            rows += forget
        }
        s1.addRows(p, rows)
        body.addView(s1)
        body.addView(a.sectionFooter(p, "Dźwięk z telefonu gra przez Bluetooth radia jak dotąd — Boss Drive dodaje sterowanie, okładki, połączenia i wiadomości. Wi-Fi (hotspot telefonu) przyspiesza wysyłanie piosenek do auta."))

        // ---- apps on the radio
        body.addView(a.sectionHeader(p, "Aplikacje na radiu"))
        val sApps = a.section(p)
        val access = AppMedia.hasAccess(a)
        val accRow = Row(a, p, "Sterowanie aplikacjami", if (access) "YouTube Music i dodatki audio w „Teraz gra”" else "Wymaga dostępu do powiadomień",
            value = if (access) "Włączone" else "Włącz", icon = CR.drawable.ic_apps, iconColor = p.indigo, chevron = !access)
        if (!access) { accRow.valueView?.setTextColor(p.blue); accRow.onTap(Press.HIGHLIGHT, p.cellPressed) {
            try { a.startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) } catch (_: Throwable) { a.hud("To radio ukrywa te ustawienia", CR.drawable.ic_info) } } }
        val store = Row(a, p, "Sklep z dodatkami", icon = CR.drawable.ic_bag, iconColor = p.blue, chevron = true)
        store.onTap(Press.HIGHLIGHT, p.cellPressed) { a.select(pl.boss.drive.radio.Tab.STORE) }
        sApps.addRows(p, listOf(accRow, store))
        body.addView(sApps)

        // ---- appearance
        body.addView(a.sectionHeader(p, "Wygląd"))
        val s2 = a.section(p)
        val seg = Segmented(a, p, listOf("Automatycznie", "Jasny", "Ciemny"), 15f)
        seg.select(Theme.mode(), false)
        seg.onSelect = { Hub.prefs.edit().putInt("theme", it).apply(); Bg.main.postDelayed({ Store.emit(Topic.THEME) }, 300) }
        val segWrap = a.vstack().apply { setPadding(a.dpi(16f), a.dpi(12f), a.dpi(16f), a.dpi(12f)); addView(seg, a.lp(MATCH, WRAP)) }
        s2.addRows(p, listOf(segWrap,
            Row(a, p, "Pełny ekran", "Ukryj pasek systemowy radia", icon = CR.drawable.ic_apps, iconColor = p.indigo, accessory = toggle("fullscreen", true))))
        body.addView(s2)
        body.addView(a.sectionFooter(p, "Automatycznie: jasny w dzień, ciemny po zachodzie słońca (liczone dla miejsca, w którym jesteś)."))

        // ---- startup & notifications
        body.addView(a.sectionHeader(p, "Uruchamianie i powiadomienia"))
        val s3 = a.section(p)
        s3.addRows(p, listOf(
            Row(a, p, "Otwieraj po uruchomieniu auta", icon = CR.drawable.ic_power, iconColor = p.orange, accessory = toggle("openOnBoot", true)),
            Row(a, p, "Otwieraj po połączeniu telefonu", icon = CR.drawable.ic_iphone, iconColor = p.green, accessory = toggle("openOnConnect", false)),
            Row(a, p, "Banery wiadomości", icon = CR.drawable.ic_message, iconColor = 0xFF2FD158.toInt(), accessory = toggle("banners", true)),
            Row(a, p, "Czytaj wiadomości na głos", "Telefon przeczyta każdą nową wiadomość", icon = CR.drawable.ic_speaker, iconColor = p.pink, accessory = toggle("autoRead", false)),
        ))
        body.addView(s3)

        // ---- offline music
        body.addView(a.sectionHeader(p, "Muzyka w aucie"))
        val s4 = a.section(p)
        val tracks = Hub.db.all()
        val used = tracks.sumOf { it.size }
        val free = Hub.freeBytes()
        val rows4 = mutableListOf<View>(
            Row(a, p, "Utwory", value = tracks.size.toString(), icon = CR.drawable.ic_music, iconColor = 0xFFFA2D48.toInt()),
            Row(a, p, "Zajęte", value = mb(used), icon = CR.drawable.ic_drive, iconColor = p.blue),
            Row(a, p, "Wolne miejsce", value = mb(free), icon = CR.drawable.ic_download, iconColor = p.gray),
        )
        if (tracks.isNotEmpty()) {
            val del = Row(a, p, "Usuń wszystkie utwory", titleColor = p.red)
            del.onTap(Press.HIGHLIGHT, p.cellPressed) {
                a.alert("Usunąć ${tracks.size} utworów?", "Pliki zostaną skasowane z radia. Na telefonie nic się nie zmieni.", listOf("Anuluj" to 0, "Usuń" to 2)) {
                    if (it == 2) Bg.run {
                        Bg.ui { Hub.player.deactivate() }
                        Hub.db.all().forEach { t -> Hub.db.delete(t.id) }
                        Hub.sendLibrary(); Store.emit(Topic.OFFLINE); Bg.ui { rebuild() }
                    }
                }
            }
            rows4 += del
        }
        s4.addRows(p, rows4)
        body.addView(s4)

        // ---- about
        body.addView(a.sectionHeader(p, "Informacje"))
        val s5 = a.section(p)
        s5.addRows(p, listOf(
            Row(a, p, "Nazwa radia", value = Hub.deviceName),
            Row(a, p, "Adres w sieci", value = Discovery.localIpv4().firstOrNull() ?: "brak Wi-Fi"),
            Row(a, p, "Wersja", value = BuildConfigCompat.version(a)),
        ))
        body.addView(s5)
        body.addView(a.sectionFooter(p, "Boss Drive • zoptymalizowane pod radia z 1 GB RAM."))
    }

    private fun mb(b: Long) = if (b > 1024L * 1024 * 1024) "%.1f GB".format(b / 1024.0 / 1024 / 1024) else "${b / (1024 * 1024)} MB"

    override fun onShow() { rebuild(); on(Topic.LINK, Topic.OFFLINE) { rebuild() } }
}
