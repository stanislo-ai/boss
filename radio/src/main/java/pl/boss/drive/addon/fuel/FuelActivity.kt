package pl.boss.drive.addon.fuel

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.addon.Addon
import pl.boss.drive.core.addon.AddonActivity
import pl.boss.drive.core.addon.Addons
import pl.boss.drive.core.addon.NumPad
import pl.boss.drive.core.addon.SimpleAdapter
import pl.boss.drive.core.addon.toDoubleOrNullPl
import pl.boss.drive.core.ui.IosSwitch
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.hairline
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class Fill(val time: Long, val odo: Int, val liters: Double, val price: Double, val full: Boolean) {
    val cost get() = liters * price
}

/** Fuel log with real consumption (full-to-full), cost per 100 km and monthly spend. */
class FuelActivity : AddonActivity() {
    override val addon: Addon get() = Addons.byPkg("pl.boss.drive.addon.fuel")!!
    private val prefs by lazy { getSharedPreferences("fuel", Context.MODE_PRIVATE) }
    private var fills = ArrayList<Fill>()
    private lateinit var main: View
    private var editor: View? = null
    private val stats = HashMap<String, TextView>()
    private lateinit var box: FrameLayout
    private val adapter = SimpleAdapter<Fill>({ FillRow(this) }) { v, f, _ -> (v as FillRow).bind(f) }

    override fun buildContent(): View {
        load()
        val root = vstack()
        root.addView(header("Paliwo", pill("Tankowanie", CR.drawable.ic_plus, pal.orange, Color.WHITE) { openEditor() }))
        val body = hstack(Gravity.TOP)
        body.pad(20f, 0f, 24f, 18f)
        val left = vstack()
        listOf(listOf("avg" to "Średnie spalanie", "last" to "Ostatnie"), listOf("cost" to "Koszt 100 km", "month" to "Ten miesiąc")).forEachIndexed { ri, row ->
            val h = hstack()
            row.forEachIndexed { ci, (k, cap) ->
                val (c, v) = stat(cap, 34f); stats[k] = v
                h.addView(c, lp(0, MATCH, 1f).apply { if (ci == 0) marginEnd = dpi(12f) })
            }
            left.addView(h, lp(MATCH, 0, 1f).apply { if (ri > 0) topMargin = dpi(12f) })
        }
        left.addView(text("Spalanie liczone metodą „do pełna” (l/100 km). Przytrzymaj tankowanie na liście, aby je usunąć.", 14f, W.REGULAR, pal.label3, lines = 3),
            lp(MATCH, WRAP).apply { topMargin = dpi(12f) })
        body.addView(left, lp(0, MATCH, 1f))
        val right = card(18f)
        box = FrameLayout(this)
        val list = listView(); list.adapter = adapter
        box.addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
        right.addView(box, FrameLayout.LayoutParams(MATCH, MATCH))
        body.addView(right, lp(0, MATCH, 1.1f).apply { marginStart = dpi(18f) })
        root.addView(body, lp(MATCH, 0, 1f))
        main = root
        refresh()
        return root
    }

    private fun refresh() {
        val sorted = fills.sortedByDescending { it.odo }
        adapter.items = sorted
        if (box.childCount > 1) box.removeViewAt(1)
        if (sorted.isEmpty()) box.addView(empty(CR.drawable.ic_fuel, "Brak tankowań", "Dotknij „Tankowanie”, aby dodać pierwsze."))
        val (avg, last) = consumption()
        stats["avg"]?.text = avg?.let { "%.1f l".format(PL, it) } ?: "—"
        stats["last"]?.text = last?.let { "%.1f l".format(PL, it) } ?: "—"
        val avgPrice = fills.takeLast(5).map { it.price }.average().takeIf { !it.isNaN() }
        stats["cost"]?.text = if (avg != null && avgPrice != null) "%.0f zł".format(avg * avgPrice) else "—"
        val cal = Calendar.getInstance(); cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0)
        val month = fills.filter { it.time >= cal.timeInMillis }.sumOf { it.cost }
        stats["month"]?.text = "%.0f zł".format(month)
    }

    /** Average over all full-to-full spans, and the most recent span. */
    private fun consumption(): Pair<Double?, Double?> {
        val s = fills.sortedBy { it.odo }
        val fullIdx = s.indices.filter { s[it].full }
        if (fullIdx.size < 2) return null to null
        fun span(a: Int, b: Int): Double? {
            val km = s[b].odo - s[a].odo
            if (km <= 0) return null
            val l = (a + 1..b).sumOf { s[it].liters }
            return l / km * 100
        }
        val first = fullIdx.first(); val lastI = fullIdx.last()
        return span(first, lastI) to span(fullIdx[fullIdx.size - 2], lastI)
    }

    // ------------------------------------------------------------------ editor

    private fun openEditor() {
        val fields = linkedMapOf("odo" to "", "liters" to "", "price" to (fills.maxByOrNull { it.time }?.price?.let { "%.2f".format(it).replace('.', ',') } ?: ""))
        var active = "odo"
        var full = true
        val labels = mapOf("odo" to "Przebieg (km)", "liters" to "Zatankowano (l)", "price" to "Cena za litr (zł)")
        val ed = hstack(Gravity.CENTER)
        ed.setBackgroundColor(pal.bg)
        ed.isClickable = true
        val form = vstack()
        form.addView(text("Nowe tankowanie", 28f, W.BOLD, pal.label), lp(MATCH, WRAP).apply { bottomMargin = dpi(14f) })
        val valueViews = HashMap<String, TextView>()
        val rowViews = HashMap<String, View>()
        lateinit var pad: NumPad
        fun paint() {
            for ((k, r) in rowViews) r.background = RoundDrawable(if (k == active) pal.fill2 else pal.card, dp(14f), strokeColor = if (k == active) pal.orange else 0, strokeWidth = dp(2f))
            for ((k, v) in valueViews) v.text = fields[k]!!.ifEmpty { "—" }
        }
        for ((k, label) in labels) {
            val r = hstack()
            r.pad(18f, 0f, 18f, 0f)
            r.addView(text(label, 18f, W.MEDIUM, pal.label2), lp(0, WRAP, 1f))
            val v = text("", 26f, W.BOLD, pal.label)
            r.addView(v); valueViews[k] = v; rowViews[k] = r
            r.onTap(Press.NONE) { active = k; pad.value = fields[k]!!; paint() }
            form.addView(r, lp(MATCH, dpi(66f)).apply { bottomMargin = dpi(10f) })
        }
        val fullRow = hstack()
        fullRow.pad(18f, 0f, 18f, 0f)
        fullRow.background = RoundDrawable(pal.card, dp(14f))
        fullRow.addView(text("Do pełna", 18f, W.MEDIUM, pal.label), lp(0, WRAP, 1f))
        val sw = IosSwitch(this, pal); sw.set(true, false); sw.onChange = { full = it }
        fullRow.addView(sw)
        form.addView(fullRow, lp(MATCH, dpi(60f)))
        val btns = hstack()
        btns.addView(pill("Anuluj", 0, pal.fill3, pal.label) { closeEditor() }, lp(0, WRAP, 1f).apply { marginEnd = dpi(10f) })
        btns.addView(pill("Zapisz", CR.drawable.ic_check, pal.orange, Color.WHITE) {
            val odo = fields["odo"]!!.toDoubleOrNullPl()?.toInt(); val l = fields["liters"]!!.toDoubleOrNullPl(); val p = fields["price"]!!.toDoubleOrNullPl()
            if (odo == null || l == null || p == null || l <= 0 || p <= 0) { active = listOf("odo", "liters", "price").first { fields[it]!!.toDoubleOrNullPl() == null }; pad.value = fields[active]!!; paint(); return@pill }
            fills.add(Fill(System.currentTimeMillis(), odo, l, p, full)); save(); closeEditor(); refresh()
        }, lp(0, WRAP, 1f))
        form.addView(btns, lp(MATCH, WRAP).apply { topMargin = dpi(14f) })
        ed.addView(form, lp(dpi(400f), WRAP).apply { marginEnd = dpi(40f) })
        pad = NumPad(this, allowDot = true) { v -> fields[active] = v; valueViews[active]?.text = v.ifEmpty { "—" } }
        ed.addView(pad)
        content.addView(ed, FrameLayout.LayoutParams(MATCH, MATCH))
        editor = ed
        ed.alpha = 0f; ed.translationY = dp(40f)
        ed.animate().alpha(1f).translationY(0f).setDuration(280).start()
        paint()
    }

    private fun closeEditor() { editor?.let { content.removeView(it) }; editor = null }

    override fun onBackInside(): Boolean { if (editor != null) { closeEditor(); return true }; return false }

    fun delete(f: Fill) = confirm("Usunąć tankowanie?", "%.1f l • %s".format(f.liters, DATE.format(f.time)), "Usuń") { fills.remove(f); save(); refresh() }

    private fun load() {
        fills.clear()
        val arr = JSONArray(prefs.getString("fills", "[]"))
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let {
            fills.add(Fill(it.optLong("t"), it.optInt("o"), it.optDouble("l"), it.optDouble("p"), it.optBoolean("f", true)))
        }
    }

    private fun save() {
        val arr = JSONArray()
        fills.forEach { arr.put(JSONObject().put("t", it.time).put("o", it.odo).put("l", it.liters).put("p", it.price).put("f", it.full)) }
        prefs.edit().putString("fills", arr.toString()).apply()
    }

    companion object { val PL = Locale("pl"); val DATE = SimpleDateFormat("d MMM yyyy", PL) }
}

class FillRow(private val a: FuelActivity) : FrameLayout(a) {
    private val title = a.text("", 18f, W.SEMIBOLD, a.pal.label)
    private val sub = a.text("", 15f, W.REGULAR, a.pal.label2)
    private val cost = a.text("", 18f, W.SEMIBOLD, a.pal.label)
    private var fill: Fill? = null

    init {
        val h = a.hstack()
        h.pad(18f, 12f, 18f, 12f)
        val col = a.vstack()
        col.addView(title); col.addView(sub, a.lp(WRAP, WRAP).apply { topMargin = a.dpi(2f) })
        h.addView(col, a.lp(0, WRAP, 1f))
        h.addView(cost)
        addView(h, LayoutParams(MATCH, WRAP))
        addView(a.hairline(a.pal.separator), LayoutParams(MATCH, maxOf(1, a.dpi(0.5f)), Gravity.BOTTOM).apply { marginStart = a.dpi(18f) })
        onTap(Press.HIGHLIGHT, a.pal.cellPressed) { }
        setOnLongClickListener { fill?.let { a.delete(it) }; true }
    }

    fun bind(f: Fill) {
        fill = f
        title.text = "%.2f l".format(f.liters).replace('.', ',') + (if (f.full) "" else "  (częściowo)")
        sub.text = "${FuelActivity.DATE.format(f.time)} • ${"%,d".format(f.odo).replace(',', ' ')} km • ${"%.2f".format(f.price).replace('.', ',')} zł/l"
        cost.text = "%.0f zł".format(f.cost)
    }
}


/** Average consumption (l/100 km, full-to-full) and latest fuel price from the Fuel add-on, for other add-ons. */
fun fuelDefaults(c: Context): Pair<Double?, Double?> {
    val arr = try { JSONArray(c.getSharedPreferences("fuel", Context.MODE_PRIVATE).getString("fills", "[]")) } catch (_: Throwable) { JSONArray() }
    val fills = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { Fill(it.optLong("t"), it.optInt("o"), it.optDouble("l"), it.optDouble("p"), it.optBoolean("f", true)) }
    val price = fills.maxByOrNull { it.time }?.price
    val s = fills.sortedBy { it.odo }
    val full = s.indices.filter { s[it].full }
    if (full.size < 2) return null to price
    val km = s[full.last()].odo - s[full.first()].odo
    val l = (full.first() + 1..full.last()).sumOf { s[it].liters }
    return (if (km > 0) l / km * 100 else null) to price
}
