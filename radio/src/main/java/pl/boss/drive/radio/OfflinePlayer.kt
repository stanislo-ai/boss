package pl.boss.drive.radio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import pl.boss.drive.core.util.Bg
import pl.boss.drive.core.util.Img

/**
 * Plays songs stored on the head unit. Owns a MediaSession so steering-wheel
 * buttons work while offline music is playing.
 */
class OfflinePlayer(private val ctx: Context, private val db: TrackDb, private val onActiveChanged: (Boolean) -> Unit) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = ctx.getSharedPreferences("player", Context.MODE_PRIVATE)
    private var mp: MediaPlayer? = null
    private var list: List<Track> = emptyList()
    private var order = IntArray(0)
    private var idx = 0
    private var prepared = false
    private var playWhenReady = false
    var shuffle = prefs.getBoolean("shuffle", false); private set
    var repeat = prefs.getBoolean("repeat", true); private set
    private var pausedByFocus = false
    private var pausedByCall = false
    val current: Track? get() = list.getOrNull(order.getOrElse(idx) { -1 })
    val isPlaying get() = mp?.isPlaying == true

    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focusListener = AudioManager.OnAudioFocusChangeListener { f ->
        when (f) {
            AudioManager.AUDIOFOCUS_LOSS -> { pausedByFocus = false; pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (isPlaying) { pausedByFocus = true; pause(keepFocus = true) }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mp?.setVolume(0.25f, 0.25f)
            AudioManager.AUDIOFOCUS_GAIN -> { mp?.setVolume(1f, 1f); if (pausedByFocus) { pausedByFocus = false; play() } }
        }
    }
    private val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attrs).setOnAudioFocusChangeListener(focusListener).setWillPauseWhenDucked(false).build()

    val session = MediaSession(ctx, "BossDriveOffline").apply {
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() = play()
            override fun onPause() = pause()
            override fun onSkipToNext() = next()
            override fun onSkipToPrevious() = prev()
            override fun onSeekTo(pos: Long) = seek(pos)
            override fun onStop() = pause()
        })
    }

    fun playList(tracks: List<Track>, start: Int, shuffleAll: Boolean = false) {
        if (tracks.isEmpty()) return
        list = tracks
        if (shuffleAll) shuffle = true
        buildOrder(start)
        idx = 0
        load(true)
    }

    fun resumeLast(): Boolean {
        val all = db.all(); if (all.isEmpty()) return false
        val lastId = prefs.getLong("last", -1)
        val i = all.indexOfFirst { it.id == lastId }.coerceAtLeast(0)
        list = all; buildOrder(i); idx = 0
        load(true, prefs.getLong("lastPos", 0))
        return true
    }

    private fun buildOrder(start: Int) {
        val n = list.size
        order = IntArray(n) { it }
        if (shuffle) {
            val rest = (0 until n).filter { it != start }.shuffled()
            order = (listOf(start) + rest).toIntArray()
        } else {
            order = IntArray(n) { (start + it) % n }
        }
    }

    private fun load(autoplay: Boolean, seekTo: Long = 0) {
        val t = current ?: return
        releasePlayer()
        prepared = false
        playWhenReady = autoplay
        val p = MediaPlayer()
        mp = p
        try {
            p.setAudioAttributes(attrs)
            p.setDataSource(t.path)
            p.setOnPreparedListener {
                prepared = true
                if (seekTo > 0) it.seekTo(seekTo.toInt())
                if (playWhenReady) start() else publish()
            }
            p.setOnCompletionListener { onComplete() }
            p.setOnErrorListener { _, _, _ -> Bg.main.postDelayed({ next() }, 300); true }
            p.prepareAsync()
        } catch (e: Throwable) {
            Bg.main.postDelayed({ next() }, 300)
        }
        prefs.edit().putLong("last", t.id).putLong("lastPos", 0).apply()
        Bg.load({ if (t.art.isNotEmpty()) Img.decodeFile(t.art, 512) else null }) { bmp ->
            if (current?.id != t.id) return@load
            Store.localArt = bmp
            Store.localBackdrop = bmp?.let { Img.backdrop(it) }
            Store.localTint = bmp?.let { Img.dominant(it) } ?: 0xFF3A3A3C.toInt()
            updateMetadata(t, bmp)
            Store.emit(Topic.ART)
        }
        Bg.run { db.played(t.id) }
        updateMetadata(t, null)
        publish()
    }

    private fun updateMetadata(t: Track, art: android.graphics.Bitmap?) {
        session.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, t.duration)
            .apply { if (art != null) putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art) }
            .build())
    }

    private fun start() {
        val p = mp ?: return
        if (am.requestAudioFocus(focusReq) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        p.start()
        session.isActive = true
        setActive(true)
        publish()
    }

    fun play() {
        if (Store.call.active) { pausedByCall = true; return }
        if (mp == null) { if (list.isEmpty()) { resumeLast(); return }; load(true); return }
        if (!prepared) { playWhenReady = true; return }
        start()
    }

    fun pause(keepFocus: Boolean = false) {
        playWhenReady = false
        mp?.let { if (prepared && it.isPlaying) it.pause() }
        if (!keepFocus) am.abandonAudioFocusRequest(focusReq)
        savePos()
        publish()
    }

    fun toggle() = if (isPlaying) pause() else play()

    fun next() {
        if (list.isEmpty()) return
        idx++
        if (idx >= order.size) { idx = 0; if (!repeat) { load(false); return } }
        load(true)
    }

    fun prev() {
        val p = mp
        if (p != null && prepared && p.currentPosition > 3000) { p.seekTo(0); publish(); return }
        if (list.isEmpty()) return
        idx = if (idx - 1 < 0) order.size - 1 else idx - 1
        load(true)
    }

    fun seek(ms: Long) { mp?.let { if (prepared) { it.seekTo(ms.toInt()); publish() } } }

    fun toggleShuffle() {
        shuffle = !shuffle
        prefs.edit().putBoolean("shuffle", shuffle).apply()
        val curIdx = order.getOrElse(idx) { 0 }
        buildOrder(curIdx); idx = 0
        publish()
    }

    fun toggleRepeat() { repeat = !repeat; prefs.edit().putBoolean("repeat", repeat).apply(); publish() }

    /** Calls always win: pause during a call, resume after. */
    fun onCallState(active: Boolean) {
        if (active && isPlaying) { pausedByCall = true; pause() }
        else if (!active && pausedByCall) { pausedByCall = false; Bg.main.postDelayed({ play() }, 1200) }
    }

    /** Hand control back to the phone. */
    fun deactivate() {
        pause()
        setActive(false)
    }

    private fun setActive(a: Boolean) {
        if (a) Store.appActive = false
        if (Store.localActive != a) { Store.localActive = a; onActiveChanged(a); Store.emit(Topic.NP, Topic.ART) }
        if (!a) session.isActive = false
    }

    private fun onComplete() {
        if (idx + 1 >= order.size && !repeat) { pause(); idx = 0; load(false); return }
        next()
    }

    private fun savePos() { mp?.let { if (prepared) prefs.edit().putLong("lastPos", it.currentPosition.toLong()).apply() } }

    private fun publish() {
        val t = current
        val p = mp
        val playing = p != null && prepared && p.isPlaying
        val pos = if (p != null && prepared) p.currentPosition.toLong() else 0L
        Store.localNp = NowPlaying(
            title = t?.title ?: "", artist = t?.artist ?: "", album = t?.album ?: "",
            duration = if (p != null && prepared && p.duration > 0) p.duration.toLong() else (t?.duration ?: 0),
            position = pos, posAt = SystemClock.elapsedRealtime(), playing = playing,
            pkg = "offline", appName = "W aucie", artKey = t?.id?.toString() ?: "", offline = true,
            shuffle = shuffle, repeat = repeat,
        )
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO)
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, pos, if (playing) 1f else 0f)
            .build())
        Store.emit(Topic.NP)
    }

    private fun releasePlayer() {
        mp?.let { try { it.reset(); it.release() } catch (_: Throwable) {} }
        mp = null
    }

    fun release() { savePos(); releasePlayer(); am.abandonAudioFocusRequest(focusReq); session.release() }
}
