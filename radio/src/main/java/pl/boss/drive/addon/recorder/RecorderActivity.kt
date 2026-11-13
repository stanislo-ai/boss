package pl.boss.drive.addon.recorder

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import pl.boss.drive.core.R as CR
import pl.boss.drive.core.addon.ARow
import pl.boss.drive.core.addon.Addon
import pl.boss.drive.core.addon.AddonActivity
import pl.boss.drive.core.addon.Addons
import pl.boss.drive.core.addon.Audio
import pl.boss.drive.core.addon.SimpleAdapter
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.Motion
import pl.boss.drive.core.ui.Press
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.WRAP
import pl.boss.drive.core.ui.dp
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.fmtTime
import pl.boss.drive.core.ui.hstack
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.onTap
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack
import pl.boss.drive.core.ui.withAlpha
import pl.boss.drive.core.util.Bg
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

data class Note(val file: File, val time: Long, val dur: Long)

/** Voice notes: one huge record button, list of recordings to play back later. */
class RecorderActivity : AddonActivity() {
    override val addon: Addon get() = Addons.byPkg("pl.boss.drive.addon.recorder")!!
    private var rec: MediaRecorder? = null
    private var recFile: File? = null
    private var recStart = 0L
    private lateinit var button: RecButton
    private lateinit var timer: TextView
    private lateinit var box: FrameLayout
    private val adapter = SimpleAdapter<Note>({ ARow(this, 48f) }) { v, n, _ -> bind(v as ARow, n) }
    private val fmt = SimpleDateFormat("d MMM, HH:mm", Locale("pl"))
    private var unsub: (() -> Unit)? = null
    private val tick = object : Runnable {
        override fun run() {
            val r = rec ?: return
            timer.text = fmtTime(SystemClock.elapsedRealtime() - recStart)
            button.level = try { (r.maxAmplitude / 32767f).coerceIn(0f, 1f) } catch (_: Throwable) { 0f }
            timer.postDelayed(this, 100)
        }
    }

    private fun dir() = File(filesDir, "notes").also { it.mkdirs() }

    override fun buildContent(): View {
        val root = vstack()
        root.addView(header("Dyktafon"))
        val body = hstack()
        body.setPadding(dpi(20f), 0, dpi(24f), dpi(18f))
        val left = vstack(Gravity.CENTER)
        button = RecButton(this)
        button.onTap(Press.SCALE) { if (rec == null) startRec() else stopRec() }
        left.addView(button, lp(dpi(220f), dpi(220f)))
        timer = text("Dotknij, aby nagrać", 24f, W.SEMIBOLD, pal.label2, gravity = Gravity.CENTER)
        left.addView(timer, lp(MATCH, WRAP).apply { topMargin = dpi(20f) })
        body.addView(left, lp(0, MATCH, 0.9f))
        val c = card(18f)
        box = FrameLayout(this)
        val list = listView(); list.adapter = adapter
        box.addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
        c.addView(box, FrameLayout.LayoutParams(MATCH, MATCH))
        body.addView(c, lp(0, MATCH, 1.1f).apply { marginStart = dpi(20f) })
        root.addView(body, lp(MATCH, 0, 1f))
        return root
    }

    private fun refresh() {
        Bg.load({
            dir().listFiles()?.filter { it.extension == "m4a" }?.sortedByDescending { it.lastModified() }?.map { f ->
                val d = try { MediaMetadataRetriever().run { setDataSource(f.path); val x = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0; release(); x } } catch (_: Throwable) { 0L }
                Note(f, f.lastModified(), d)
            } ?: emptyList()
        }) { notes ->
            adapter.items = notes
            if (box.childCount > 1) box.removeViewAt(1)
            if (notes.isEmpty()) box.addView(empty(CR.drawable.ic_mic, "Brak nagrań", "Twoje notatki głosowe pojawią się tutaj."))
        }
    }

    private fun bind(r: ARow, n: Note) {
        r.title.text = "Notatka " + fmt.format(n.time)
        r.sub.text = fmtTime(n.dur)
        r.trailing.text = ""
        val playing = Audio.key == n.file.path && Audio.playing
        r.setArt(null, if (playing) CR.drawable.ic_pause else CR.drawable.ic_play, addon.color)
        r.setAction(CR.drawable.ic_trash, pal.red)
        r.onAction = { confirm("Usunąć nagranie?", r.title.text.toString(), "Usuń") { if (Audio.key == n.file.path) Audio.stop(); n.file.delete(); refresh() } }
        r.onClick = {
            if (Audio.key == n.file.path) Audio.toggle()
            else { Audio.owner = addon.pkg; Audio.play(n.file.path, n.file.path, r.title.text.toString(), "Dyktafon", null) }
        }
    }

    private fun startRec() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 3); return }
        if (Audio.playing) Audio.pause()
        val f = File(dir(), "rec_${System.currentTimeMillis()}.m4a")
        try {
            val r = MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000); r.setAudioSamplingRate(32_000)
            r.setOutputFile(f.path)
            r.prepare(); r.start()
            rec = r; recFile = f; recStart = SystemClock.elapsedRealtime()
            button.recording = true
            timer.setTextColor(pal.red)
            timer.post(tick)
        } catch (e: Throwable) {
            f.delete(); timer.text = "Mikrofon niedostępny"
        }
    }

    private fun stopRec() {
        val r = rec ?: return
        rec = null
        try { r.stop() } catch (_: Throwable) { recFile?.delete() }
        r.release()
        button.recording = false; button.level = 0f
        timer.setTextColor(pal.label2); timer.text = "Zapisano"
        refresh()
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) { if (g.firstOrNull() == PackageManager.PERMISSION_GRANTED) startRec() }
    override fun onResume() { super.onResume(); refresh(); unsub = Audio.listen { adapter.notifyDataSetChanged() } }
    override fun onPause() { stopRec(); unsub?.invoke(); super.onPause() }
}

/** iOS Voice Memos style button: red dot that morphs into a rounded square while recording. */
class RecButton(private val a: AddonActivity) : View(a) {
    var recording = false
        set(v) { field = v; animateMorph(if (v) 1f else 0f) }
    var level = 0f
        set(v) { field = v; invalidate() }
    private var morph = 0f
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = a.pal.red }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun animateMorph(to: Float) {
        android.animation.ValueAnimator.ofFloat(morph, to).apply {
            duration = 280; interpolator = Motion.spring
            addUpdateListener { morph = it.animatedValue as Float; invalidate() }; start()
        }
    }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat(); val cx = width / 2f; val cy = height / 2f
        if (recording) { glow.color = withAlpha(a.pal.red, 0.18f); c.drawCircle(cx, cy, s / 2 * (0.86f + 0.14f * level), glow) }
        ring.strokeWidth = s * 0.035f; ring.color = a.pal.label
        c.drawCircle(cx, cy, s * 0.4f, ring)
        val half = s * (0.33f - 0.15f * morph)
        val rad = half * (1f - 0.7f * morph)
        c.drawRoundRect(cx - half, cy - half, cx + half, cy + half, rad, rad, red)
    }
}

