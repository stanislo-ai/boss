package pl.boss.drive.addon.service

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.addon.Addon
import pl.boss.drive.core.addon.AddonActivity
import pl.boss.drive.core.addon.Addons
import pl.boss.drive.core.addon.NumPad
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.Segmented
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.iconTile
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Car deadlines: inspection, insurance, oil (date + km), seasonal tyres. */
class ServiceActivity : AddonActivity() {
    override val addon: Addon get() = Addons.byPkg(packageName)!!
    private val prefs by lazy { getSharedPreferences("service", Context.MODE_PRIVATE) }
    private val date = SimpleDateFormat("d MMMM yyyy", Locale("pl"))
    private lateinit var grid: LinearLayout
    private lateinit var odoPill: TextView
    private var overlay: View? = null

    private enum class Status { OK, SOON, LATE, UNSET }

    override fun buildContent(): View {
        val root = vstack()
        odoPill = text("", 17f, W.SEMIBOLD, pal.blue, gravity = Gravity.CENTER).apply {
            background = RoundDrawable(pal.fill3, dp(12f)); pad(18f, 14f, 18f, 14f); onTap(Press.SCALE) { editKm("Aktualny przebieg", "odo") }
        }
        root.addView(header("Serwis", odoPill))
        grid = vstack()
        grid.pad(20f, 0f, 24f, 20f)
        root.addView(grid, lp(MATCH, 0, 1f))
        refresh()
        return root
    }

    private fun days(ms: Long) = TimeUnit.MILLISECONDS.toDays(ms - System.currentTimeMillis()).toInt()

    private fun refresh() {
        val odo = prefs.getInt("odo", 0)
        odoPill.text = if (odo > 0) "Przebieg: ${fmtKm(odo)}" else "Podaj przebieg"
        grid.removeAllViews()
        val cards = ArrayList<View>()
        // inspection
        val insp = prefs.getLong("insp", 0)
        cards += card("Przegląd techniczny", CR.drawable.ic_check, pal.green, dateStatus(insp), dateText(insp), if (insp > 0) date.format(insp) else "Dotknij, aby ustawić") { pickDate("insp") }
        // insurance
        val oc = prefs.getLong("oc", 0)
        cards += card("Ubezpieczenie OC/AC", CR.drawable.ic_info, pal.blue, dateStatus(oc), dateText(oc), if (oc > 0) "do " + date.format(oc) else "Dotknij, aby ustawić") { pickDate("oc") }
        // oil
        val oilKm = prefs.getInt("oilKm", 0); val oilDate = prefs.getLong("oilDate", 0); val interval = prefs.getInt("oilEvery", 15000)
        val kmLeft = if (oilKm > 0 && odo > 0) oilKm + interval - odo else null
        val oilDue = if (oilDate > 0) Calendar.getInstance().apply { timeInMillis = oilDate; add(Calendar.YEAR, 1) }.timeInMillis else 0L
        val oilStatus = when {
            oilKm == 0 && oilDate == 0L -> Status.UNSET
            (kmLeft != null && kmLeft < 0) || (oilDue > 0 && days(oilDue) < 0) -> Status.LATE
            (kmLeft != null && kmLeft < 1000) || (oilDue > 0 && days(oilDue) < 30) -> Status.SOON
            else -> Status.OK
        }
        val oilMain = when { kmLeft != null -> if (kmLeft >= 0) "za ${fmtKm(kmLeft)}" else "${fmtKm(-kmLeft)} po terminie"; oilDue > 0 -> dateText(oilDue); else -> "—" }
        cards += card("Wymiana oleju", CR.drawable.ic_fuel, pal.orange, oilStatus, oilMain,
            if (oilDate > 0) "ostatnio ${date.format(oilDate)} • co ${fmtKm(interval)}" else "Dotknij, aby zapisać wymianę") { editOil() }
        // tyres
        val winter = prefs.getBoolean("winter", false)
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        val target = if (winter) Calendar.getInstance().apply { set(if (cal.get(Calendar.MONTH) >= 4) y + 1 else y, Calendar.APRIL, 15) } else
            Calendar.getInstance().apply { set(if (cal.get(Calendar.MONTH) >= 11) y + 1 else y, Calendar.NOVEMBER, 1) }
        val td = days(target.timeInMillis)
        val known = prefs.contains("winter")
        val tyreStatus = if (!known) Status.UNSET else if (td < 0) Status.LATE else if (td < 21) Status.SOON else Status.OK
        cards += card("Opony", CR.drawable.ic_car, pal.indigo, tyreStatus, if (!known) "—" else if (winter) "Zimowe" else "Letnie",
            if (!known) "Dotknij, aby ustawić założone opony"
            else "zmiana na ${if (winter) "letnie" else "zimowe"} ok. ${SimpleDateFormat("d MMMM", Locale("pl")).format(target.time)} • dotknij, aby zmienić") {
            val m = cal.get(Calendar.MONTH)
            prefs.edit().putBoolean("winter", if (!known) (m >= Calendar.NOVEMBER || m <= Calendar.MARCH) else !winter).apply(); refresh()
        }
        cards.chunked(2).forEachIndexed { i, pair ->
            val row = hstack()
            pair.forEachIndexed { j, c -> row.addView(c, lp(0, MATCH, 1f).apply { if (j == 0) marginEnd = dpi(14f) }) }
            grid.addView(row, lp(MATCH, 0, 1f).apply { if (i > 0) topMargin = dpi(14f) })
        }
    }

    private fun dateStatus(t: Long) = when { t == 0L -> Status.UNSET; days(t) < 0 -> Status.LATE; days(t) < 30 -> Status.SOON; else -> Status.OK }
    private fun dateText(t: Long): String {
        if (t == 0L) return "—"
        val d = days(t)
        return when { d < 0 -> "${-d} dni po terminie"; d == 0 -> "dziś"; d == 1 -> "jutro"; d < 60 -> "za $d dni"; else -> "za ${d / 30} mies." }
    }
    private fun fmtKm(km: Int) = "%,d km".format(km).replace(',', ' ')

    private fun card(title: String, glyph: Int, color: Int, st: Status, main: String, sub: String, onClick: () -> Unit): View {
        val c = card(20f)
        val v = vstack()
        v.pad(20f, 18f, 20f, 16f)
        val top = hstack()
        top.addView(iconTile(glyph, color, 40f, 0.58f), lp(dpi(40f), dpi(40f)))
        top.addView(text(title, 19f, W.SEMIBOLD, pal.label), lp(0, WRAP, 1f).apply { marginStart = dpi(12f) })
        val (label, lc) = when (st) { Status.OK -> "OK" to pal.green; Status.SOON -> "Wkrótce" to pal.orange; Status.LATE -> "Po terminie" to pal.red; Status.UNSET -> "Brak danych" to pal.gray }
        top.addView(text(label, 14f, W.SEMIBOLD, Color.WHITE).apply { background = RoundDrawable(lc, dp(10f)); pad(10f, 4f, 10f, 4f) })
        v.addView(top)
        v.addView(View(this), lp(1, 0, 1f))
        v.addView(text(main, 32f, W.BOLD, if (st == Status.LATE) pal.red else pal.label))
        v.addView(text(sub, 15f, W.REGULAR, pal.label2, lines = 2), lp(MATCH, WRAP).apply { topMargin = dpi(4f) })
        c.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        c.onTap(Press.SCALE) { onClick() }
        return c
    }

    private fun pickDate(key: String, then: (() -> Unit)? = null) {
        val cal = Calendar.getInstance().apply { val t = prefs.getLong(key, 0); if (t > 0) timeInMillis = t }
        DatePickerDialog(this, if (pal.dark) AlertDialog.THEME_DEVICE_DEFAULT_DARK else AlertDialog.THEME_DEVICE_DEFAULT_LIGHT, { _, y, m, d ->
            prefs.edit().putLong(key, Calendar.getInstance().apply { set(y, m, d, 12, 0) }.timeInMillis).apply(); refresh(); then?.invoke()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun editKm(title: String, key: String, extra: View? = null, then: (() -> Unit)? = null) {
        val ov = hstack(Gravity.CENTER)
        ov.setBackgroundColor(pal.bg); ov.isClickable = true
        val left = vstack()
        left.addView(text(title, 28f, W.BOLD, pal.label))
        val value = text("", 44f, W.BOLD, pal.blue)
        left.addView(value, lp(WRAP, WRAP).apply { topMargin = dpi(14f) })
        extra?.let { left.addView(it, lp(MATCH, WRAP).apply { topMargin = dpi(18f) }) }
        val pad = NumPad(this, allowDot = false) { v -> value.text = if (v.isEmpty()) "— km" else fmtKm(v.toIntOrNull() ?: 0) }
        pad.value = prefs.getInt(key, 0).takeIf { it > 0 }?.toString() ?: ""
        val btns = hstack()
        btns.addView(pill("Anuluj", 0, pal.fill3, pal.label) { closeOverlay() }, lp(0, WRAP, 1f).apply { marginEnd = dpi(10f) })
        btns.addView(pill("Zapisz", CR.drawable.ic_check, pal.blue, Color.WHITE) {
            pad.value.toIntOrNull()?.let { prefs.edit().putInt(key, it).apply() }
            closeOverlay(); refresh(); then?.invoke()
        }, lp(0, WRAP, 1f))
        left.addView(btns, lp(MATCH, WRAP).apply { topMargin = dpi(24f) })
        ov.addView(left, lp(dpi(400f), WRAP).apply { marginEnd = dpi(40f) })
        ov.addView(pad)
        overlay = ov
        content.addView(ov, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun editOil() {
        val seg = Segmented(this, pal, listOf("10 tys.", "15 tys.", "20 tys.", "30 tys."), 15f)
        val options = listOf(10000, 15000, 20000, 30000)
        seg.select(options.indexOf(prefs.getInt("oilEvery", 15000)).coerceAtLeast(0), false)
        seg.onSelect = { prefs.edit().putInt("oilEvery", options[it]).apply() }
        val wrap = vstack().apply { addView(text("Wymiana co (km)", 15f, W.SEMIBOLD, pal.label2)); addView(seg, lp(MATCH, WRAP).apply { topMargin = dpi(8f) }) }
        editKm("Przebieg przy wymianie oleju", "oilKm", wrap) {
            prefs.edit().putLong("oilDate", System.currentTimeMillis()).apply()
            if (prefs.getInt("odo", 0) < prefs.getInt("oilKm", 0)) prefs.edit().putInt("odo", prefs.getInt("oilKm", 0)).apply()
            refresh()
        }
    }

    private fun closeOverlay() { overlay?.let { content.removeView(it) }; overlay = null }
    override fun onBackInside(): Boolean { if (overlay != null) { closeOverlay(); return true }; return false }
}
