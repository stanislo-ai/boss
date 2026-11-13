package pl.boss.drive.phone.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.ui.IosSwitch
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.Row
import pl.boss.drive.core.ui.Spinner
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.addRows
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.glyph
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.iconTile
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.section
import pl.boss.drive.core.ui.sectionFooter
import pl.boss.drive.core.ui.sectionHeader
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack
import pl.boss.drive.phone.CarLink
import pl.boss.drive.phone.CarService
import pl.boss.drive.phone.Ev
import pl.boss.drive.phone.Perms

fun Context.largeTitle(pal: pl.boss.drive.core.ui.Palette, s: String, sub: String? = null): View = vstack().apply {
    pad(16f, 12f, 16f, 6f)
    addView(text(s, 34f, W.BOLD, pal.label))
    if (sub != null) addView(text(sub, 15f, W.REGULAR, pal.label2, lines = 2), lp(MATCH, WRAP).apply { topMargin = dpi(2f) })
}

/** Checklist item: everything the app needs, one tap each. */
class PermItem(val title: String, val sub: String, val icon: Int, val color: Int, val ok: (Context) -> Boolean, val fix: (MainActivity) -> Unit)

fun permItems(pal: pl.boss.drive.core.ui.Palette): List<PermItem> {
    fun req(a: MainActivity, p: Array<String>) { if (p.isNotEmpty()) a.requestPermissions(p, 7) }
    fun open(a: MainActivity, i: Intent) { try { a.startActivity(i) } catch (_: Throwable) { a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.packageName))) } }
    return listOf(
        PermItem("Dostęp do powiadomień", "Sterowanie muzyką z każdej aplikacji i wiadomości", CR.drawable.ic_music, 0xFFFA2D48.toInt(),
            { Perms.notificationAccess(it) }) { open(it, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        PermItem("Połączenia", "Odbieranie i dzwonienie z radia", CR.drawable.ic_phone, pal.green, { Perms.has(it, Perms.PHONE) }) { req(it, Perms.PHONE) },
        PermItem("Kontakty", "Ulubione i lista kontaktów w aucie", CR.drawable.ic_person, pal.gray, { Perms.has(it, Perms.CONTACTS) }) { req(it, Perms.CONTACTS) },
        PermItem("Historia połączeń", "Ostatnie połączenia i numer dzwoniącego", CR.drawable.ic_clock_line, pal.indigo, { Perms.has(it, Perms.CALL_LOG) }) { req(it, Perms.CALL_LOG) },
        PermItem("SMS", "„Prowadzę, oddzwonię” jednym dotknięciem", CR.drawable.ic_message, 0xFF2FD158.toInt(), { Perms.has(it, Perms.SMS) }) { req(it, Perms.SMS) },
        PermItem("Muzyka na telefonie", "Wysyłanie piosenek do auta", CR.drawable.ic_download, pal.pink, { Perms.has(it, Perms.AUDIO) }) { req(it, Perms.AUDIO) },
        PermItem("Bluetooth", "Rozpoznawanie auta i zapasowe połączenie", CR.drawable.ic_bluetooth, pal.blue, { Perms.has(it, Perms.BT) }) { req(it, Perms.BT) },
        PermItem("Lokalizacja", "Pogoda w aucie i miejsce parkowania", CR.drawable.ic_location, pal.blue, { Perms.has(it, Perms.LOCATION) }) { req(it, Perms.LOCATION) },
        PermItem("Powiadomienia", "Status połączenia z autem", CR.drawable.ic_info, pal.red, { Perms.notificationsAllowed(it) && Perms.has(it, Perms.NOTIF) }) { req(it, Perms.NOTIF) },
        PermItem("Działanie w tle", "Auto połączy się samo, nawet gdy telefon jest w kieszeni", CR.drawable.ic_power, pal.orange, { Perms.batteryUnrestricted(it) }) {
            @SuppressLint("BatteryLife")
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + it.packageName))
            open(it, i)
        },
    )
}

// =====================================================================================
class AutoPage(a: MainActivity) : Page(a) {
    private lateinit var body: LinearLayout

    override fun build(): View {
        val sv = ScrollView(a)
        val col = a.vstack()
        col.addView(a.largeTitle(pal, "Boss Drive"))
        body = a.vstack()
        col.addView(body, a.lp(MATCH, WRAP).apply { bottomMargin = a.dpi(30f) })
        sv.addView(col)
        return sv
    }

    private fun rebuild() {
        body.removeAllViews()
        val ctx = a
        // ---- hero status card
        val card = ctx.vstack()
        card.background = RoundDrawable(pal.card, ctx.dp(14f))
        card.pad(16f, 16f, 16f, 16f)
        val top = ctx.hstack()
        val connected = CarLink.connected
        val tileColor = if (connected) pal.green else if (CarLink.running) pal.blue else pal.gray
        top.addView(ctx.iconTile(CR.drawable.ic_car, tileColor, 58f, 0.6f), ctx.lp(ctx.dpi(58f), ctx.dpi(58f)))
        val tc = ctx.vstack()
        val title = when {
            connected -> "Połączono z autem"
            CarLink.waitingApproval -> "Zatwierdź na radiu"
            CarLink.running -> "Szukam radia…"
            else -> "Rozłączono"
        }
        val sub = when {
            connected -> CarLink.radioName + " • " + (if (CarLink.link?.isWifi == true) "Wi-Fi (hotspot)" else "Bluetooth — bez hotspotu")
            CarLink.waitingApproval -> "Na ekranie radia dotknij „Połącz”"
            CarLink.running -> if (CarLink.prefs.getString("carBt", "").isNullOrEmpty()) "Pierwszy raz: włącz hotspot w telefonie — potem wystarczy sam Bluetooth"
                else "Łączę przez Bluetooth z „${CarLink.prefs.getString("carBtName", "radiem")}”…"
            else -> "Połączy się samo, gdy wsiądziesz do auta"
        }
        tc.addView(ctx.text(title, 20f, W.BOLD, pal.label))
        tc.addView(ctx.text(sub, 14f, W.REGULAR, pal.label2, lines = 3), ctx.lp(MATCH, WRAP).apply { topMargin = ctx.dpi(2f) })
        top.addView(tc, ctx.lp(0, WRAP, 1f).apply { marginStart = ctx.dpi(14f) })
        if (CarLink.running && !connected) top.addView(Spinner(ctx, pal.gray), ctx.lp(ctx.dpi(22f), ctx.dpi(22f)))
        card.addView(top)
        val btns = ctx.hstack()
        fun btn(label: String, fill: Int, fg: Int, fn: () -> Unit) = ctx.text(label, 16f, W.SEMIBOLD, fg, gravity = Gravity.CENTER).apply {
            background = RoundDrawable(fill, ctx.dp(11f)); minHeight = ctx.dpi(44f); onTap(Press.DIM) { fn() }
        }
        if (CarLink.running) {
            btns.addView(btn("Rozłącz", pal.fill3, pal.red) { CarService.start(a, CarService.ACTION_STOP); a.window.decorView.postDelayed({ rebuild() }, 400) }, ctx.lp(0, WRAP, 1f))
            if (!connected) btns.addView(btn("Hotspot", pal.blue, Color.WHITE) {
                try { a.startActivity(Intent("android.settings.TETHER_SETTINGS")) } catch (_: Throwable) { a.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
            }, ctx.lp(0, WRAP, 1f).apply { marginStart = ctx.dpi(10f) })
        } else {
            btns.addView(btn("Połącz teraz", pal.blue, Color.WHITE) { CarService.start(a); a.window.decorView.postDelayed({ rebuild() }, 300) }, ctx.lp(0, WRAP, 1f))
        }
        card.addView(btns, ctx.lp(MATCH, WRAP).apply { topMargin = ctx.dpi(16f) })
        body.addView(card, ctx.lp(MATCH, WRAP).apply { setMargins(ctx.dpi(16f), ctx.dpi(10f), ctx.dpi(16f), 0) })

        // ---- sound isn't reaching the car
        if (connected && !CarLink.status.a2dp) {
            val warn = ctx.vstack()
            warn.background = RoundDrawable(if (pal.dark) 0xFF3A2A12.toInt() else 0xFFFFF3E0.toInt(), ctx.dp(14f))
            warn.pad(16f, 14f, 16f, 14f)
            warn.addView(ctx.text("Dźwięk gra z głośnika telefonu", 17f, W.SEMIBOLD, pal.orange))
            warn.addView(ctx.text("Połącz telefon z radiem przez Bluetooth i włącz w nim „Dźwięk multimediów” (Ustawienia Bluetooth → ⚙ przy radiu). " +
                "Jeśli dalej cicho — na radiu dotknij przycisku Bluetooth w „Teraz gra”.", 14f, W.REGULAR, pal.label, lines = 5), ctx.lp(MATCH, WRAP).apply { topMargin = ctx.dpi(4f) })
            warn.addView(ctx.text("Ustawienia Bluetooth", 16f, W.SEMIBOLD, pal.blue).apply {
                pad(0f, 10f, 0f, 0f); onTap { try { a.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (_: Throwable) {} }
            })
            body.addView(warn, ctx.lp(MATCH, WRAP).apply { setMargins(ctx.dpi(16f), ctx.dpi(12f), ctx.dpi(16f), 0) })
        }

        // ---- now playing
        if (CarLink.running && CarLink.media.lastTitle.isNotEmpty()) {
            body.addView(ctx.sectionHeader(pal, "Teraz gra"))
            val s = ctx.section(pal)
            s.addRows(pal, listOf(Row(ctx, pal, CarLink.media.lastTitle, listOf(CarLink.media.lastArtist, CarLink.media.lastApp).filter { it.isNotEmpty() }.joinToString(" • "),
                icon = if (CarLink.media.playing) CR.drawable.ic_waveform else CR.drawable.ic_pause, iconColor = 0xFFFA2D48.toInt(), minHeightDp = 56f)))
            body.addView(s)
        }

        // ---- transfers summary
        val pending = CarLink.transfers.pendingCount()
        if (pending > 0) {
            body.addView(ctx.sectionHeader(pal, "Do auta"))
            val s = ctx.section(pal)
            val cur = CarLink.transfers.current
            val r = Row(ctx, pal, if (cur != null) "Wysyłam: ${cur.title}" else "$pending w kolejce",
                if (connected) "Piosenki zapiszą się w radiu" else "Wyślą się po połączeniu z autem",
                icon = CR.drawable.ic_download, iconColor = pal.blue, chevron = true)
            r.onTap(Press.HIGHLIGHT, pal.cellPressed) { a.select(1) }
            s.addRows(pal, listOf(r)); body.addView(s)
        }

        // ---- setup checklist
        val missing = permItems(pal).filter { !it.ok(ctx) }
        if (missing.isNotEmpty()) {
            body.addView(ctx.sectionHeader(pal, "Dokończ konfigurację"))
            val s = ctx.section(pal)
            s.addRows(pal, missing.map { p -> Row(ctx, pal, p.title, p.sub, value = "Włącz", icon = p.icon, iconColor = p.color, minHeightDp = 58f).apply {
                valueView?.setTextColor(pal.blue); onTap(Press.HIGHLIGHT, pal.cellPressed) { p.fix(a) } } })
            body.addView(s)
            body.addView(ctx.sectionFooter(pal, "Jeśli przełącznik jest wyszarzony (Android 13+): Ustawienia → Aplikacje → Boss Drive → ⋮ → „Zezwól na ustawienia z ograniczeniami”."))
        }

        // ---- parking
        val pt = CarLink.prefs.getLong("pTime", 0)
        if (pt > 0) {
            body.addView(ctx.sectionHeader(pal, "Parking"))
            val s = ctx.section(pal)
            val r = Row(ctx, pal, "Tu zostawiłeś auto", "Zapisano " + android.text.format.DateUtils.getRelativeTimeSpanString(pt).toString().lowercase(),
                icon = CR.drawable.ic_parking, iconColor = pal.blue, chevron = true, minHeightDp = 56f)
            r.onTap(Press.HIGHLIGHT, pal.cellPressed) {
                val lat = CarLink.prefs.getFloat("pLat", 0f); val lon = CarLink.prefs.getFloat("pLon", 0f)
                try { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(Moje auto)"))) } catch (_: Throwable) {}
            }
            s.addRows(pal, listOf(r)); body.addView(s)
        }

        // ---- how to
        body.addView(ctx.sectionHeader(pal, "Jak to działa"))
        val how = ctx.section(pal)
        how.addRows(pal, listOf(
            Row(ctx, pal, "1. Aplikacja na radiu", "Zainstaluj Boss Drive Car na radiu (plik radio.apk)", icon = CR.drawable.ic_car, iconColor = pal.blue, minHeightDp = 56f),
            Row(ctx, pal, "2. Hotspot", "Połącz radio raz z hotspotem tego telefonu (Wi-Fi radia)", icon = CR.drawable.ic_wifi, iconColor = pal.blue, minHeightDp = 56f),
            Row(ctx, pal, "3. Bluetooth", "Sparuj telefon z radiem jak zwykle — dźwięk i rozmowy idą tędy", icon = CR.drawable.ic_bluetooth, iconColor = pal.blue, minHeightDp = 56f),
            Row(ctx, pal, "4. Gotowe", "Po wejściu do auta wszystko połączy się samo", icon = CR.drawable.ic_check, iconColor = pal.green, minHeightDp = 56f),
        ))
        body.addView(how)
    }

    override fun onShow() {
        rebuild()
        on(Ev.LINK) { rebuild() }
        on(Ev.PERMS) { rebuild() }
        on(Ev.NP) { rebuild() }
        on(Ev.TRANSFERS) { if (CarLink.transfers.current == null) rebuild() }
    }
}

// =====================================================================================
class SettingsPage(a: MainActivity) : Page(a) {
    private lateinit var body: LinearLayout

    override fun build(): View {
        val sv = ScrollView(a)
        val col = a.vstack()
        col.addView(a.largeTitle(pal, "Ustawienia"))
        body = a.vstack()
        col.addView(body, a.lp(MATCH, WRAP).apply { bottomMargin = a.dpi(30f) })
        sv.addView(col)
        return sv
    }

    private fun toggle(key: String, def: Boolean, fn: (Boolean) -> Unit = {}) = IosSwitch(a, pal).apply {
        set(CarLink.prefs.getBoolean(key, def), false)
        onChange = { CarLink.prefs.edit().putBoolean(key, it).apply(); fn(it) }
    }

    @SuppressLint("MissingPermission")
    private fun chooseCar() {
        if (!Perms.has(a, Perms.BT)) { a.requestPermissions(Perms.BT, 7); return }
        val ad = (a.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
        val devs = ad.bondedDevices?.toList()?.sortedBy { it.name ?: "" } ?: emptyList()
        if (devs.isEmpty()) { Ios.hud(a, pal, "Brak sparowanych urządzeń Bluetooth"); return }
        Ios.sheet(a, pal, "Które urządzenie to Twoje radio?", devs.map { it.name ?: it.address } + "Wykrywaj automatycznie") { i ->
            if (i < devs.size) CarLink.prefs.edit().putString("carBt", devs[i].address).putString("carBtName", devs[i].name ?: "").apply()
            else CarLink.prefs.edit().remove("carBt").remove("carBtName").apply()
            rebuild()
        }
    }

    private fun rebuild() {
        body.removeAllViews()
        val ctx = a
        val p = pal
        body.addView(ctx.sectionHeader(p, "Auto"))
        val s1 = ctx.section(p)
        val carName = CarLink.prefs.getString("carBtName", "")?.takeIf { it.isNotEmpty() && !CarLink.prefs.getString("carBt", "").isNullOrEmpty() } ?: "Automatycznie"
        val car = Row(ctx, p, "Bluetooth auta", value = carName, icon = CR.drawable.ic_bluetooth, iconColor = p.blue, chevron = true)
        car.onTap(Press.HIGHLIGHT, p.cellPressed) { chooseCar() }
        s1.addRows(p, listOf(
            car,
            Row(ctx, p, "Łącz automatycznie w aucie", icon = CR.drawable.ic_car, iconColor = p.green, accessory = toggle("autoStart", true)),
            Row(ctx, p, "Zawsze aktywne", "Gdy auto nie jest wykrywane przez Bluetooth", icon = CR.drawable.ic_power, iconColor = p.orange,
                accessory = toggle("alwaysOn", false) { if (it) CarService.start(a) }),
        ))
        body.addView(s1)

        body.addView(ctx.sectionHeader(p, "Połączenie"))
        val s2 = ctx.section(p)
        val hs = Row(ctx, p, "Hotspot", "Radio łączy się z hotspotem telefonu", icon = CR.drawable.ic_wifi, iconColor = p.blue, chevron = true)
        hs.onTap(Press.HIGHLIGHT, p.cellPressed) { try { a.startActivity(Intent("android.settings.TETHER_SETTINGS")) } catch (_: Throwable) { a.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) } }
        val ipNow = CarLink.prefs.getString("manualIp", "") ?: ""
        val ip = Row(ctx, p, "Adres radia", value = ipNow.ifEmpty { "Automatycznie" }, icon = CR.drawable.ic_drive, iconColor = p.gray, chevron = true)
        ip.onTap(Press.HIGHLIGHT, p.cellPressed) {
            Ios.alert(a, p, "Adres IP radia", "Zostaw puste, aby wyszukiwać automatycznie. Adres znajdziesz na radiu: Ustawienia → Informacje.",
                listOf("Anuluj" to 0, "Zapisz" to 1), input = ipNow) { k, txt -> if (k == 1) { CarLink.prefs.edit().putString("manualIp", txt.trim()).apply(); rebuild() } }
        }
        s2.addRows(p, listOf(hs, ip))
        body.addView(s2)

        body.addView(ctx.sectionHeader(p, "Wiadomości i parking"))
        val s3 = ctx.section(p)
        s3.addRows(p, listOf(
            Row(ctx, p, "Pokazuj wiadomości w aucie", "SMS, WhatsApp, Messenger, Telegram…", icon = CR.drawable.ic_message, iconColor = 0xFF2FD158.toInt(), accessory = toggle("forwardMessages", true)),
            Row(ctx, p, "Zapamiętuj miejsce parkowania", icon = CR.drawable.ic_parking, iconColor = p.blue, accessory = toggle("parking", true)),
        ))
        body.addView(s3)

        body.addView(ctx.sectionHeader(p, "Uprawnienia"))
        val s4 = ctx.section(p)
        s4.addRows(p, permItems(p).map { pi ->
            val ok = pi.ok(ctx)
            val acc: View = if (ok) ctx.glyph(CR.drawable.ic_check, p.green).apply { layoutParams = ctx.lp(ctx.dpi(20f), ctx.dpi(20f)) }
                else ctx.text("Włącz", 17f, W.REGULAR, p.blue)
            Row(ctx, p, pi.title, icon = pi.icon, iconColor = pi.color, accessory = acc).apply { onTap(Press.HIGHLIGHT, p.cellPressed) { pi.fix(a) } }
        })
        body.addView(s4)
        body.addView(ctx.sectionFooter(p, "Dźwięk i rozmowy idą przez Bluetooth radia (najlepsza jakość, redukcja echa w radiu). Boss Drive dodaje ekran w stylu CarPlay, sterowanie, wiadomości i muzykę offline."))

        body.addView(ctx.sectionHeader(p, "Informacje"))
        val s5 = ctx.section(p)
        val ver = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Throwable) { "1.0" }
        s5.addRows(p, listOf(Row(ctx, p, "Wersja", value = ver ?: "1.0"), Row(ctx, p, "Telefon", value = CarLink.deviceName)))
        body.addView(s5)
    }

    override fun onShow() { rebuild(); on(Ev.PERMS) { rebuild() } }
}

