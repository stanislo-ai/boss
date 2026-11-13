package pl.boss.drive.core.addon

import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.RoundDrawable
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.blend
import pl.boss.drive.core.ui.clipRound
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.hairline
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.pad
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.tint
import pl.boss.drive.core.ui.vstack

/** Standard add-on list row: [art] title / subtitle ... trailing text [action glyph]. */
class ARow(val a: AddonActivity, artSize: Float = 56f) : FrameLayout(a) {
    val art = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipRound(a.dp(8f)) }
    val title: TextView = a.text("", 18f, W.MEDIUM, a.pal.label, lines = 2)
    val sub: TextView = a.text("", 15f, W.REGULAR, a.pal.label2)
    val trailing: TextView = a.text("", 15f, W.SEMIBOLD, a.pal.label2)
    val action = ImageView(a)
    var onClick: (() -> Unit)? = null
    var onAction: (() -> Unit)? = null
    var onLong: (() -> Unit)? = null

    init {
        val h = a.hstack()
        h.pad(20f, 10f, 16f, 10f)
        h.addView(art, a.lp(a.dpi(artSize), a.dpi(artSize)).apply { marginEnd = a.dpi(16f) })
        val col = a.vstack(Gravity.CENTER_VERTICAL)
        col.addView(title); col.addView(sub, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(3f) })
        h.addView(col, a.lp(0, WRAP, 1f))
        h.addView(trailing, a.lp(WRAP, WRAP).apply { marginStart = a.dpi(10f) })
        action.pad(11f)
        action.onTap(Press.SCALE) { onAction?.invoke() }
        h.addView(action, a.lp(a.dpi(50f), a.dpi(50f)).apply { marginStart = a.dpi(6f) })
        addView(h, LayoutParams(MATCH, WRAP))
        addView(a.hairline(a.pal.separator), LayoutParams(MATCH, maxOf(1, a.dpi(0.5f)), Gravity.BOTTOM).apply { marginStart = a.dpi(artSize + 36f) })
        onTap(Press.HIGHLIGHT, a.pal.cellPressed) { onClick?.invoke() }
        setOnLongClickListener { onLong?.invoke(); onLong != null }
    }

    fun setArt(b: Bitmap?, fallback: Int, color: Int) {
        if (b != null) { art.setImageBitmap(b); art.imageTintList = null; art.background = null; art.setPadding(0, 0, 0, 0) }
        else {
            art.setImageResource(fallback); art.tint(Color.WHITE)
            art.background = RoundDrawable(color, a.dp(8f), blend(color, Color.WHITE, 0.15f), color)
            val p = a.dpi(14f); art.setPadding(p, p, p, p)
        }
    }

    fun setAction(res: Int, color: Int) {
        if (res == 0) { action.visibility = GONE; return }
        action.visibility = VISIBLE; action.setImageResource(res); action.tint(color)
    }

}
