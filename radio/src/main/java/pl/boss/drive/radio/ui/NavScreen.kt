package pl.boss.drive.radio.ui

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.clipRound
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.glyph
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack
import pl.boss.drive.radio.Hub
import pl.boss.drive.radio.MainActivity
import pl.boss.drive.radio.Store
import pl.boss.drive.radio.Topic

/** Paint a manoeuvre icon from the navigation app white, whatever colours it came in. */
fun ImageView.maneuver() {
    val b = Store.navIcon
    if (b != null) { setImageBitmap(b); colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN) }
    else { setImageResource(CR.drawable.ic_location); colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN) }
}

/** Navigation guidance mirrored from the phone (Google Maps, Waze, …), CarPlay style. */
class NavScreen(a: MainActivity) : Screen(a) {
    override val title = "Nawigacja"
    private lateinit var arrow: ImageView
    private lateinit var dist: TextView
    private lateinit var instr: TextView
    private lateinit var eta: TextView
    private lateinit var app: TextView
    private lateinit var actions: LinearLayout
    private lateinit var npTitle: TextView
    private lateinit var npPlay: ImageView
    private lateinit var guide: View
    private lateinit var idle: View

    override fun build(): View {
        val root = FrameLayout(a)
        val row = a.hstack(Gravity.FILL)
        row.pad(16f)
        // big manoeuvre card
        val card = FrameLayout(a)
        card.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF1F7A4D.toInt(), 0xFF0F5434.toInt())).apply { cornerRadius = a.dp(22f) }
        card.clipRound(a.dp(22f))
        val g = a.vstack(Gravity.CENTER_VERTICAL)
        g.pad(34f, 26f, 30f, 26f)
        arrow = ImageView(a)
        g.addView(arrow, a.lp(a.dpi(190f), a.dpi(190f)))
        dist = a.text("", 72f, W.BOLD, Color.WHITE)
        g.addView(dist, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(10f) })
        instr = a.text("", 30f, W.SEMIBOLD, 0xE6FFFFFF.toInt(), lines = 3)
        g.addView(instr, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(4f) })
        card.addView(g, FrameLayout.LayoutParams(MATCH, MATCH))
        guide = card
        row.addView(card, a.lp(0, MATCH, 1.45f).apply { marginEnd = a.dpi(14f) })
        // side: ETA, actions, music
        val side = a.vstack()
        val etaCard = a.card(20f)
        val ev = a.vstack(); ev.pad(22f, 18f, 22f, 18f)
        app = a.text("", 14f, W.SEMIBOLD, pal.label2)
        ev.addView(app)
        eta = a.text("", 26f, W.BOLD, pal.label, lines = 3)
        ev.addView(eta, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(6f) })
        actions = a.vstack()
        ev.addView(actions, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(12f) })
        etaCard.addView(ev, FrameLayout.LayoutParams(MATCH, WRAP))
        side.addView(etaCard, a.lp(MATCH, 0, 1f))
        val music = a.card(20f)
        val mh = a.hstack(); mh.pad(18f, 0f, 12f, 0f)
        mh.addView(a.glyph(CR.drawable.ic_music, 0xFFFA2D48.toInt()), a.lp(a.dpi(26f), a.dpi(26f)))
        npTitle = a.text("", 17f, W.SEMIBOLD, pal.label, lines = 2)
        mh.addView(npTitle, a.lp(0, WRAP, 1f).apply { marginStart = a.dpi(12f) })
        npPlay = a.glyph(CR.drawable.ic_play, pal.label).apply { pad(12f); onTap(Press.SCALE) { Hub.cmd("toggle") } }
        mh.addView(npPlay, a.lp(a.dpi(56f), a.dpi(56f)))
        mh.addView(a.glyph(CR.drawable.ic_next, pal.label).apply { pad(14f); onTap(Press.SCALE) { Hub.cmd("next") } }, a.lp(a.dpi(56f), a.dpi(56f)))
        music.addView(mh, FrameLayout.LayoutParams(MATCH, MATCH))
        music.onTap(Press.NONE) { a.openMusic() }
        side.addView(music, a.lp(MATCH, a.dpi(96f)).apply { topMargin = a.dpi(14f) })
        row.addView(side, a.lp(0, MATCH, 1f))
        root.addView(row, FrameLayout.LayoutParams(MATCH, MATCH))
        idle = a.emptyState(CR.drawable.ic_location, "Brak aktywnej nawigacji",
            "Włącz trasę w Mapach Google, Waze albo innej nawigacji na telefonie — wskazówki pojawią się tutaj.").apply { setBackgroundColor(pal.bg) }
        root.addView(idle, FrameLayout.LayoutParams(MATCH, MATCH))
        return root
    }

    private fun bind() {
        val n = Store.nav
        idle.visibility = if (n.active) View.GONE else View.VISIBLE
        arrow.maneuver()
        dist.text = n.distance
        instr.text = n.instruction
        eta.text = n.eta.ifEmpty { "W drodze" }
        app.text = n.app.uppercase()
        actions.removeAllViews()
        n.actions.forEachIndexed { i, label ->
            if (label.isBlank()) return@forEachIndexed
            actions.addView(a.pillButton(label, 0, pal.fill3, if (i == 0) pal.red else pal.blue) { Hub.navCommand(i) },
                a.lp(MATCH, WRAP).apply { topMargin = a.dpi(8f) })
        }
        bindMusic()
    }

    private fun bindMusic() {
        val np = Store.np
        npTitle.text = if (np.isEmpty) "Muzyka" else listOf(np.title, np.artist).filter { it.isNotEmpty() }.joinToString(" — ")
        npPlay.setImageResource(if (np.playing) CR.drawable.ic_pause else CR.drawable.ic_play)
    }

    override fun onShow() { bind(); on(Topic.NAV) { bind() }; on(Topic.NP) { bindMusic() } }
}

