package pl.boss.drive.phone.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Motion
import pl.boss.drive.core.ui.Palette
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
import pl.boss.drive.core.ui.style
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack

/** UIAlertController look-alikes: centred alert and bottom action sheet. */
object Ios {
    private fun base(a: Activity, content: View, gravity: Int): Dialog {
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = FrameLayout(a)
        root.setOnClickListener { d.dismiss() }
        content.isClickable = true
        root.addView(content, FrameLayout.LayoutParams(if (gravity == Gravity.CENTER) a.dpi(270f) else MATCH, WRAP, gravity).apply {
            if (gravity != Gravity.CENTER) setMargins(a.dpi(8f), 0, a.dpi(8f), a.dpi(8f))
        })
        d.setContentView(root)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setDimAmount(0.4f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        d.setOnShowListener {
            if (gravity == Gravity.CENTER) { content.scaleX = 1.15f; content.scaleY = 1.15f; content.alpha = 0f
                content.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(280).setInterpolator(Motion.spring).start() }
            else { content.translationY = a.dp(400f); content.animate().translationY(0f).setDuration(350).setInterpolator(Motion.push).start() }
        }
        return d
    }

    fun alert(a: Activity, pal: Palette, title: String, msg: String, buttons: List<Pair<String, Int>>, input: String? = null, onPick: (Int, String) -> Unit) {
        val card = a.vstack()
        card.background = RoundDrawable(if (pal.dark) 0xF22C2C2E.toInt() else 0xF7F2F2F2.toInt(), a.dp(14f))
        val body = a.vstack(Gravity.CENTER_HORIZONTAL).apply { pad(16f, 19f, 16f, 18f) }
        body.addView(a.text(title, 17f, W.SEMIBOLD, pal.label, lines = 3, gravity = Gravity.CENTER), a.lp(MATCH, WRAP))
        if (msg.isNotEmpty()) body.addView(a.text(msg, 13f, W.REGULAR, pal.label, lines = 8, gravity = Gravity.CENTER), a.lp(MATCH, WRAP).apply { topMargin = a.dpi(4f) })
        var field: EditText? = null
        if (input != null) {
            field = EditText(a).apply {
                setText(input); setSelection(input.length)
                style(13f, W.REGULAR, pal.label); setHintTextColor(pal.label3)
                background = RoundDrawable(if (pal.dark) 0xFF1C1C1E.toInt() else Color.WHITE, a.dp(7f), strokeColor = pal.separator, strokeWidth = a.dp(0.5f))
                setPadding(a.dpi(8f), a.dpi(7f), a.dpi(8f), a.dpi(7f))
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                isSingleLine = true
            }
            body.addView(field, a.lp(MATCH, WRAP).apply { topMargin = a.dpi(14f) })
        }
        card.addView(body, a.lp(MATCH, WRAP))
        card.addView(a.hairline(pal.separator))
        val side = buttons.size == 2
        val row = if (side) a.hstack() else a.vstack()
        lateinit var d: Dialog
        buttons.forEachIndexed { i, (label, kind) ->
            if (i > 0) row.addView(View(a).apply { setBackgroundColor(pal.separator) }, if (side) a.lp(maxOf(1, a.dpi(0.5f)), MATCH) else a.lp(MATCH, maxOf(1, a.dpi(0.5f))))
            val b = a.text(label, 17f, if (kind == 1) W.SEMIBOLD else W.REGULAR, if (kind == 2) pal.red else pal.blue, gravity = Gravity.CENTER)
            b.onTap(Press.HIGHLIGHT, pal.cellPressed) { d.dismiss(); onPick(kind, field?.text?.toString() ?: "") }
            row.addView(b, if (side) a.lp(0, a.dpi(44f), 1f) else a.lp(MATCH, a.dpi(44f)))
        }
        card.addView(row, a.lp(MATCH, WRAP))
        d = base(a, card, Gravity.CENTER)
        d.show()
        field?.requestFocus()
    }

    /** Action sheet: list of options + separate Cancel button. */
    fun sheet(a: Activity, pal: Palette, title: String, options: List<String>, onPick: (Int) -> Unit) {
        val col = a.vstack()
        val group = a.vstack()
        group.background = RoundDrawable(if (pal.dark) 0xF22C2C2E.toInt() else 0xF7F9F9F9.toInt(), a.dp(14f))
        lateinit var d: Dialog
        if (title.isNotEmpty()) {
            group.addView(a.text(title, 13f, W.SEMIBOLD, pal.label2, lines = 2, gravity = Gravity.CENTER).apply { pad(16f, 14f, 16f, 14f) }, a.lp(MATCH, WRAP))
        }
        val list = a.vstack()
        options.forEachIndexed { i, o ->
            if (i > 0 || title.isNotEmpty()) list.addView(a.hairline(pal.separator))
            val b = a.text(o, 20f, W.REGULAR, pal.blue, gravity = Gravity.CENTER)
            b.onTap(Press.HIGHLIGHT, pal.cellPressed) { d.dismiss(); onPick(i) }
            list.addView(b, a.lp(MATCH, a.dpi(57f)))
        }
        val sv = ScrollView(a); sv.addView(list)
        group.addView(sv, a.lp(MATCH, WRAP))
        col.addView(group, a.lp(MATCH, WRAP))
        val cancel = a.text("Anuluj", 20f, W.SEMIBOLD, pal.blue, gravity = Gravity.CENTER)
        cancel.background = RoundDrawable(if (pal.dark) 0xFF2C2C2E.toInt() else Color.WHITE, a.dp(14f))
        cancel.onTap(Press.DIM) { d.dismiss() }
        col.addView(cancel, a.lp(MATCH, a.dpi(57f)).apply { topMargin = a.dpi(8f) })
        d = base(a, col, Gravity.BOTTOM)
        d.show()
    }

    fun hud(a: Activity, pal: Palette, msg: String) {
        val root = a.window.decorView as ViewGroup
        val v = a.text(msg, 15f, W.SEMIBOLD, pal.label, lines = 3, gravity = Gravity.CENTER)
        v.background = RoundDrawable(if (pal.dark) 0xF02C2C2E.toInt() else 0xF5F2F2F7.toInt(), a.dp(14f))
        v.pad(20f, 14f, 20f, 14f); v.elevation = a.dp(12f)
        root.addView(v, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        v.alpha = 0f; v.scaleX = 0.9f; v.scaleY = 0.9f
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(Motion.spring).start()
        v.postDelayed({ v.animate().alpha(0f).setDuration(250).withEndAction { root.removeView(v) }.start() }, 1700)
    }
}

