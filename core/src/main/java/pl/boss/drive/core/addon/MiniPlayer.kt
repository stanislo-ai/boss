package pl.boss.drive.core.addon

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import pl.boss.drive.core.R
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.ScrubBar
import pl.boss.drive.core.ui.Spinner
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

/** Floating "now playing" bar for audio add-ons (Music-app mini player look). */
class MiniPlayer(private val a: AddonActivity) : FrameLayout(a) {
    private val art = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipRound(a.dp(8f)) }
    private val title: TextView = a.text("", 17f, W.SEMIBOLD, a.pal.label)
    private val sub: TextView = a.text("", 14f, W.REGULAR, a.pal.label2)
    private val play = a.glyph(R.drawable.ic_play, a.pal.label)
    private val back = a.glyph(R.drawable.ic_back15, a.pal.label)
    private val fwd = a.glyph(R.drawable.ic_back15, a.pal.label).apply { scaleX = -1f }
    private val spin = Spinner(a, a.pal.gray)
    private val scrub = ScrubBar(a, a.pal.label, showTimes = false)
    private var unsub: (() -> Unit)? = null
    private val tick = object : Runnable { override fun run() { if (Audio.playing) scrub.update(Audio.position, Audio.duration); postDelayed(this, 1000) } }

    init {
        background = RoundDrawable(if (a.pal.dark) 0xF22C2C2E.toInt() else 0xF7FFFFFF.toInt(), a.dp(18f))
        elevation = a.dp(14f)
        val col = a.vstack()
        col.pad(14f, 10f, 14f, 6f)
        val h = a.hstack()
        h.addView(art, a.lp(a.dpi(52f), a.dpi(52f)).apply { marginEnd = a.dpi(14f) })
        val t = a.vstack(); t.addView(title); t.addView(sub)
        h.addView(t, a.lp(0, WRAP, 1f))
        h.addView(spin, a.lp(a.dpi(24f), a.dpi(24f)).apply { marginEnd = a.dpi(12f) })
        fun btn(v: ImageView, size: Float, fn: () -> Unit) { v.pad(size * 0.2f); v.onTap(Press.SCALE) { fn() }; h.addView(v, a.lp(a.dpi(size), a.dpi(size)).apply { marginStart = a.dpi(6f) }) }
        btn(back, 50f) { Audio.seekBy(-15_000) }
        btn(play, 58f) { Audio.toggle() }
        btn(fwd, 50f) { Audio.seekBy(30_000) }
        col.addView(h, a.lp(MATCH, WRAP))
        scrub.onSeek = { Audio.seekTo(it) }
        col.addView(scrub, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(6f) })
        addView(col, LayoutParams(MATCH, WRAP))
        onTap(Press.NONE) { }
    }

    fun bind() {
        visibility = if (Audio.key.isEmpty()) View.GONE else View.VISIBLE
        title.text = Audio.title; sub.text = if (Audio.live) "Na żywo • ${Audio.subtitle}" else Audio.subtitle
        if (Audio.art != null) { art.setImageBitmap(Audio.art); art.background = null; art.setPadding(0, 0, 0, 0); art.imageTintList = null }
        else { art.setImageResource(R.drawable.ic_music); art.background = RoundDrawable(a.pal.card3, a.dp(8f)); art.setPadding(a.dpi(12f), a.dpi(12f), a.dpi(12f), a.dpi(12f)) }
        play.setImageResource(if (Audio.playing) R.drawable.ic_pause else R.drawable.ic_play)
        spin.visibility = if (Audio.buffering) View.VISIBLE else View.GONE
        back.visibility = if (Audio.live) View.GONE else View.VISIBLE
        fwd.visibility = back.visibility
        scrub.visibility = back.visibility
        scrub.update(Audio.position, Audio.duration)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); unsub = Audio.listen { bind() }; bind(); post(tick) }
    override fun onDetachedFromWindow() { unsub?.invoke(); removeCallbacks(tick); super.onDetachedFromWindow() }

    companion object {
        fun lpBottom(a: AddonActivity) = LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { setMargins(a.dpi(20f), 0, a.dpi(20f), a.dpi(14f)) }
    }
}
