package pl.boss.drive.core.addon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import pl.boss.drive.core.R

/**
 * Tiny shared audio engine for audio add-ons (podcasts, web radio): MediaPlayer + MediaSession
 * (so Boss Drive's Now Playing and the steering wheel buttons control it) + audio focus
 * (so calls and navigation pause/duck it). Kept alive by [AudioService] only while playing.
 */
object Audio {
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private var mp: MediaPlayer? = null
    private var prepared = false
    var playing = false; private set
    var buffering = false; private set
    var live = false; private set
    var title = ""; private set
    var subtitle = ""; private set
    var art: Bitmap? = null; private set
    var key = ""; private set
    private var pausedByFocus = false
    private val listeners = ArrayList<() -> Unit>()
    var onComplete: ((String) -> Unit)? = null
    var onPosition: ((String, Long) -> Unit)? = null
    var onSkip: ((Int) -> Unit)? = null   // +1 next / -1 previous (for lists)

    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private lateinit var am: AudioManager
    private lateinit var focus: AudioFocusRequest
    lateinit var session: MediaSession; private set

    fun init(c: Context) {
        if (::app.isInitialized) return
        app = c.applicationContext
        am = app.getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { f ->
                when (f) {
                    AudioManager.AUDIOFOCUS_LOSS -> pause()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (playing) { pausedByFocus = true; pause(keepFocus = true) }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mp?.setVolume(0.25f, 0.25f)
                    AudioManager.AUDIOFOCUS_GAIN -> { mp?.setVolume(1f, 1f); if (pausedByFocus) { pausedByFocus = false; resume() } }
                }
            }.build()
        session = MediaSession(app, "BossDriveAddon").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onStop() = stop()
                override fun onSeekTo(pos: Long) = seekTo(pos)
                override fun onSkipToNext() { if (live) onSkip?.invoke(1) else seekBy(30_000) }
                override fun onSkipToPrevious() { if (live) onSkip?.invoke(-1) else seekBy(-15_000) }
                override fun onFastForward() = seekBy(30_000)
                override fun onRewind() = seekBy(-15_000)
            })
        }
    }

    fun listen(f: () -> Unit): () -> Unit { listeners.add(f); return { listeners.remove(f) } }
    private fun changed() { publishSession(); listeners.toList().forEach { it() } }

    val position: Long get() = mp?.takeIf { prepared && !live }?.currentPosition?.toLong() ?: 0L
    val duration: Long get() = mp?.takeIf { prepared && !live }?.duration?.toLong()?.coerceAtLeast(0) ?: 0L

    /** [source] is a local path or an http(s) URL. */
    fun play(key: String, source: String, title: String, subtitle: String, art: Bitmap?, startAt: Long = 0, live: Boolean = false) {
        savePosition()
        release()
        this.key = key; this.title = title; this.subtitle = subtitle; this.art = art; this.live = live
        if (live) liveSource = source
        prepared = false; buffering = true; playing = false
        val p = MediaPlayer()
        mp = p
        try {
            p.setAudioAttributes(attrs)
            p.setDataSource(source)
            p.setOnPreparedListener {
                prepared = true; buffering = false
                if (startAt > 0 && !live) it.seekTo(startAt.toInt())
                resume()
            }
            p.setOnCompletionListener { val k = this.key; playing = false; changed(); stopService(); onComplete?.invoke(k) }
            p.setOnErrorListener { _, _, _ -> buffering = false; playing = false; changed(); stopService(); true }
            p.setOnInfoListener { _, what, _ ->
                when (what) { MediaPlayer.MEDIA_INFO_BUFFERING_START -> { buffering = true; changed() }; MediaPlayer.MEDIA_INFO_BUFFERING_END -> { buffering = false; changed() } }
                true
            }
            p.prepareAsync()
        } catch (e: Throwable) { buffering = false }
        session.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle)
            .apply { if (art != null) putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art) }
            .build())
        session.isActive = true
        changed()
        main.removeCallbacks(tick); main.postDelayed(tick, 10_000)
    }

    fun resume() {
        val p = mp ?: run { if (live && liveSource.isNotEmpty() && key.isNotEmpty()) restartLive(); return }
        if (!prepared) return
        if (am.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        p.start(); playing = true
        startService()
        changed()
    }

    fun pause(keepFocus: Boolean = false) {
        mp?.let { if (prepared && it.isPlaying) it.pause() }
        playing = false
        if (!keepFocus) am.abandonAudioFocusRequest(focus)
        savePosition()
        if (live) { release(); prepared = false }   // streams: drop the connection, reconnect on play
        stopService()
        changed()
    }

    fun toggle() { if (playing) pause() else resume() }

    private var liveSource = ""
    private fun restartLive() { play(key, liveSource, title, subtitle, art, 0, true) }

    fun seekTo(ms: Long) { mp?.let { if (prepared && !live) { it.seekTo(ms.toInt()); changed() } } }
    fun seekBy(d: Long) = seekTo((position + d).coerceIn(0, duration))

    fun stop() { savePosition(); release(); playing = false; key = ""; session.isActive = false; am.abandonAudioFocusRequest(focus); stopService(); changed() }

    private val tick = object : Runnable {
        override fun run() { if (playing) savePosition(); main.postDelayed(this, 10_000) }
    }

    private fun savePosition() { if (key.isNotEmpty() && prepared && !live) onPosition?.invoke(key, position) }

    private fun release() { mp?.let { try { it.reset(); it.release() } catch (_: Throwable) {} }; mp = null }

    private fun publishSession() {
        if (!::session.isInitialized) return
        val st = when { buffering -> PlaybackState.STATE_BUFFERING; playing -> PlaybackState.STATE_PLAYING; else -> PlaybackState.STATE_PAUSED }
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (!live) actions = actions or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND
        session.setPlaybackState(PlaybackState.Builder().setActions(actions).setState(st, position, if (playing) 1f else 0f).build())
    }

    private fun startService() { try { app.startForegroundService(Intent(app, AudioService::class.java)) } catch (_: Throwable) {} }
    private fun stopService() { try { app.stopService(Intent(app, AudioService::class.java)) } catch (_: Throwable) {} }
}

/** Keeps the add-on process alive (foreground) only while audio is playing. */
class AudioService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("audio", "Odtwarzanie", NotificationManager.IMPORTANCE_LOW))
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pi = launch?.let { android.app.PendingIntent.getActivity(this, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE) }
        startForeground(7, Notification.Builder(this, "audio").setSmallIcon(R.drawable.ic_music)
            .setContentTitle(Audio.title.ifEmpty { "Boss Drive" }).setContentText(Audio.subtitle)
            .setStyle(Notification.MediaStyle().setMediaSession(Audio.session.sessionToken))
            .setContentIntent(pi).setOngoing(true).build())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
}
