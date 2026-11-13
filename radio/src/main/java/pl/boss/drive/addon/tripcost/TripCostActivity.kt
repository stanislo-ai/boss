package pl.boss.drive.addon.tripcost

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import pl.boss.drive.addon.fuel.fuelDefaults
import pl.boss.drive.core.addon.Addon
import pl.boss.drive.core.addon.AddonActivity
import pl.boss.drive.core.addon.Addons
import pl.boss.drive.core.addon.NumPad
import pl.boss.drive.core.addon.toDoubleOrNullPl
import pl.boss.drive.core.ui.IosSwitch
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack
import java.util.Locale

/** Fuel cost of a trip and the split per person (defaults come from the Fuel add-on). */
class TripCostActivity : AddonActivity() {
    override val addon: Addon get() = Addons.byPkg("pl.boss.drive.addon.tripcost")!!
    private val prefs by lazy { getSharedPreferences("tripcost", Context.MODE_PRIVATE) }
    private val pl = Locale("pl")
    private val fields = linkedMapOf("km" to "", "cons" to "", "price" to "")
    private val labels = mapOf("km" to "Dystans (km)", "cons" to "Spalanie (l/100 km)", "price" to "Cena paliwa (zł/l)")
    private var active = "km"
    private var people = 1
    private var both = false
    private lateinit var pad: NumPad
    private val values = HashMap<String, TextView>()
    private val rows = HashMap<String, View>()
    private lateinit var total: TextView
    private lateinit var per: TextView
    private lateinit var liters: TextView
    private lateinit var peopleView: TextView

    override fun buildContent(): View {
        val (cons, price) = fuelDefaults(this)
        fields["km"] = prefs.getString("km", "") ?: ""
        fields["cons"] = prefs.getString("cons", null) ?: cons?.let { "%.1f".format(pl, it) } ?: "6,5"
        fields["price"] = prefs.getString("price", null) ?: price?.let { "%.2f".format(pl, it) } ?: "6,50"
        people = prefs.getInt("people", 1); both = prefs.getBoolean("both", false)

        val root = vstack()
        root.addView(header("Koszt podróży"))
        val body = hstack(Gravity.TOP)
        body.pad(20f, 0f, 24f, 16f)
        val left = vstack()
        val res = card(20f)
        val rv = vstack(); rv.pad(22f, 14f, 22f, 14f)
        rv.addView(text("KOSZT PALIWA", 13f, W.SEMIBOLD, pal.label2))
        total = text("—", 44f, W.BOLD, pal.label); rv.addView(total)
        per = text("", 19f, W.SEMIBOLD, pal.green); rv.addView(per)
        liters = text("", 15f, W.REGULAR, pal.label2); rv.addView(liters, lp(WRAP, WRAP).apply { topMargin = dpi(2f) })
        res.addView(rv, FrameLayout.LayoutParams(MATCH, WRAP))
        left.addView(res, lp(MATCH, WRAP))
        for ((k, label) in labels) {
            val r = hstack(); r.pad(18f, 0f, 18f, 0f)
            r.addView(text(label, 17f, W.MEDIUM, pal.label2), lp(0, WRAP, 1f))
            val v = text("", 24f, W.BOLD, pal.label); r.addView(v)
            values[k] = v; rows[k] = r
            r.onTap(Press.NONE) { active = k; pad.value = fields[k]!!; paint() }
            left.addView(r, lp(MATCH, dpi(54f)).apply { topMargin = dpi(8f) })
        }
        val pr = hstack(); pr.pad(18f, 0f, 8f, 0f); pr.background = RoundDrawable(pal.card, dp(14f))
        pr.addView(text("Osoby", 17f, W.MEDIUM, pal.label2), lp(0, WRAP, 1f))
        fun step(lbl: String, d: Int) = text(lbl, 26f, W.BOLD, pal.blue, gravity = Gravity.CENTER).apply {
            background = RoundDrawable(pal.fill3, dp(10f)); onTap(Press.SCALE) { people = (people + d).coerceIn(1, 9); calc() }
        }
        pr.addView(step("−", -1), lp(dpi(48f), dpi(40f)))
        peopleView = text("1", 24f, W.BOLD, pal.label, gravity = Gravity.CENTER); pr.addView(peopleView, lp(dpi(46f), WRAP))
        pr.addView(step("+", 1), lp(dpi(48f), dpi(40f)))
        left.addView(pr, lp(MATCH, dpi(54f)).apply { topMargin = dpi(8f) })
        val br = hstack(); br.pad(18f, 0f, 12f, 0f); br.background = RoundDrawable(pal.card, dp(14f))
        br.addView(text("Tam i z powrotem", 17f, W.MEDIUM, pal.label2), lp(0, WRAP, 1f))
        val sw = IosSwitch(this, pal); sw.set(both, false); sw.onChange = { both = it; calc() }
        br.addView(sw)
        left.addView(br, lp(MATCH, dpi(52f)).apply { topMargin = dpi(8f) })
        body.addView(left, lp(0, WRAP, 1f))
        pad = NumPad(this, allowDot = true) { v -> fields[active] = v; calc() }
        body.addView(pad, lp(WRAP, WRAP).apply { marginStart = dpi(30f) })
        root.addView(body, lp(MATCH, 0, 1f))
        pad.value = fields["km"]!!
        paint()
        return root
    }

    private fun paint() {
        for ((k, r) in rows) r.background = RoundDrawable(if (k == active) pal.fill2 else pal.card, dp(14f), strokeColor = if (k == active) pal.green else 0, strokeWidth = dp(2f))
        calc()
    }

    private fun calc() {
        for ((k, v) in values) v.text = fields[k]!!.ifEmpty { "—" }
        peopleView.text = people.toString()
        val km = fields["km"]!!.toDoubleOrNullPl(); val c = fields["cons"]!!.toDoubleOrNullPl(); val p = fields["price"]!!.toDoubleOrNullPl()
        prefs.edit().putString("km", fields["km"]).putString("cons", fields["cons"]).putString("price", fields["price"]).putInt("people", people).putBoolean("both", both).apply()
        if (km == null || c == null || p == null || km <= 0) { total.text = "—"; per.text = "Wpisz dystans"; liters.text = ""; return }
        val dist = if (both) km * 2 else km
        val l = dist * c / 100
        val cost = l * p
        total.text = "%.2f zł".format(pl, cost)
        per.text = if (people > 1) "%.2f zł na osobę (%d os.)".format(pl, cost / people, people) else "1 osoba"
        liters.text = "%.1f l paliwa • %.0f km".format(pl, l, dist)
    }
}
