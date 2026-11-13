package pl.boss.drive.radio

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import pl.boss.drive.core.addon.Addons
import pl.boss.drive.core.addon.Audio
import pl.boss.drive.core.net.Proto
import pl.boss.drive.core.net.pkt
import pl.boss.drive.core.util.Bg
import pl.boss.drive.core.util.Img

/** Built-in audio add-ons (Podcasts, Radio online) play through the shared [Audio] engine in this process. */
object InApp {
    fun key(pkg: String) = Audio.owner == pkg && Audio.key.isNotEmpty()
    fun playing(pkg: String) = Audio.owner == pkg && Audio.playing
}

/** Only needed so Android lets Boss Drive see and control media apps installed on the radio. */
class RadioNotifListener : NotificationListenerService() {
    override fun onListenerConnected() { AppMedia.start(this) }
}

/**
 * Media apps running on the head unit itself (YouTube Music, our Podcasts / Radio online add-ons…).
 * They show up in Now Playing and obey the same buttons as the phone's music.
 */
object AppMedia {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var ctx: Context
    private var msm: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()
    private var primary: MediaController? = null
    private var started = false
    private var lastArtKey = ""
    private var inApp = false
    private var inAppHooked = false

    /** Follow our own built-in audio add-ons (no special permission needed — same app). */
    fun hookInApp(c: Context) {
        if (inAppHooked) return
        inAppHooked = true
        ctx = c.applicationContext
        Audio.init(ctx)
        Audio.listen { onInApp() }
    }

    private fun onInApp() {
        if (Audio.key.isEmpty()) {
            if (inApp) { inApp = false; Store.appActive = false; Store.emit(Topic.NP, Topic.ART) }
            publishSources(); return
        }
        if (Audio.playing && !inApp) {
            inApp = true; primary = null
            Store.appActive = true
            if (Store.localActive) Hub.player.deactivate()
            Hub.send(pkt(Proto.LOCAL_PLAYING, "on" to true))
        }
        if (!inApp) return
        val name = Addons.byPkg(Audio.owner)?.name ?: "Boss Drive"
        val key = "inapp|" + Audio.key
        Store.appNp = NowPlaying(title = Audio.title, artist = Audio.subtitle, duration = Audio.duration, position = Audio.position,
            posAt = SystemClock.elapsedRealtime(), playing = Audio.playing, pkg = Audio.owner, appName = name, artKey = key,
            canSeek = !Audio.live, canSkip = true)
        if (key != lastArtKey) {
            lastArtKey = key
            val bmp = Audio.art
            if (bmp == null) { Store.appArt = null; Store.appBackdrop = null; Store.appTint = 0xFF3A3A3C.toInt(); Store.emit(Topic.ART) }
            else Bg.run { val back = Img.backdrop(bmp); val tint = Img.dominant(bmp); Store.ui { Store.appArt = bmp; Store.appBackdrop = back; Store.appTint = tint; Store.emit(Topic.ART) } }
        }
        Store.emit(Topic.NP)
        publishSources()
    }

    fun hasAccess(c: Context): Boolean {
        val flat = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(c.packageName + "/")
    }

    fun start(c: Context) {
        ctx = c.applicationContext
        if (started || !hasAccess(ctx)) { if (!started) publishSources(); return }
        try {
            val m = ctx.getSystemService(MediaSessionManager::class.java)
            val comp = ComponentName(ctx, RadioNotifListener::class.java)
            m.addOnActiveSessionsChangedListener({ list -> set(list ?: emptyList()) }, comp, h)
            msm = m; started = true
            set(m.getActiveSessions(comp))
        } catch (_: SecurityException) {}
    }

    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { pick(); publish() }
        override fun onMetadataChanged(metadata: MediaMetadata?) { publish() }
        override fun onSessionDestroyed() { msm?.let { m -> try { set(m.getActiveSessions(ComponentName(ctx, RadioNotifListener::class.java))) } catch (_: Throwable) {} } }
    }

    private fun set(list: List<MediaController>) {
        controllers.forEach { try { it.unregisterCallback(cb) } catch (_: Throwable) {} }
        controllers = list.filter { it.packageName != ctx.packageName }
        controllers.forEach { it.registerCallback(cb, h) }
        pick(); publish(); publishSources()
    }

    private fun isPlaying(c: MediaController?) = c?.playbackState?.state.let { it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING }

    private fun pick() {
        val playing = controllers.firstOrNull { isPlaying(it) }
        if (playing != null && (playing !== primary || !Store.appActive || inApp)) {
            if (inApp) { inApp = false; if (Audio.playing) Audio.pause() }
            if (playing !== primary) lastArtKey = ""
            primary = playing
            // a radio app started playing: it becomes the active source
            if (!Store.appActive) {
                Store.appActive = true
                if (Store.localActive) Hub.player.deactivate()
                Hub.send(pkt(Proto.LOCAL_PLAYING, "on" to true))
                Store.emit(Topic.NP, Topic.ART)
            }
        }
        if (primary != null && primary !in controllers) primary = controllers.firstOrNull()
        if (primary == null && Store.appActive) { Store.appActive = false; Store.emit(Topic.NP, Topic.ART) }
    }

    private fun label(pkg: String) = try { val pm = ctx.packageManager; pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Throwable) { pkg }

    private fun publish() {
        val c = primary ?: return
        val md = c.metadata; val st = c.playbackState
        var pos = st?.position ?: 0L
        if (st != null && st.state == PlaybackState.STATE_PLAYING && st.lastPositionUpdateTime > 0) pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val actions = st?.actions ?: 0L
        val artKey = "${c.packageName}|$title|$artist"
        Store.appNp = NowPlaying(title = title, artist = artist, album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0, position = pos, posAt = SystemClock.elapsedRealtime(),
            playing = isPlaying(c), speed = st?.playbackSpeed?.takeIf { it > 0 } ?: 1f, pkg = c.packageName, appName = label(c.packageName),
            artKey = artKey, canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L, canSkip = true)
        val bmp = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (artKey != lastArtKey) {
            lastArtKey = artKey
            if (bmp == null) { Store.appArt = null; Store.appBackdrop = null; Store.appTint = 0xFF3A3A3C.toInt(); Store.emit(Topic.ART) }
            else Bg.run {
                val b = Img.scaled(bmp, 512); val back = Img.backdrop(b); val tint = Img.dominant(b)
                Store.ui { Store.appArt = b; Store.appBackdrop = back; Store.appTint = tint; Store.emit(Topic.ART) }
            }
        }
        Store.emit(Topic.NP)
    }

    /** Sessions running now + installed audio add-ons / YouTube Music that can be started. */
    fun publishSources() {
        val out = LinkedHashMap<String, Source>()
        for (c in controllers) out[c.packageName] = Source(c.packageName, label(c.packageName), true, isPlaying(c))
        for (a in Addons.ALL.filter { it.audio }) if (AddonStore.isInstalled(ctx, a) && a.pkg !in out) out[a.pkg] = Source(a.pkg, a.name, InApp.key(a.pkg), InApp.playing(a.pkg))
        for (pkg in out.keys) if (Store.icons.get(pkg) == null) icon(pkg)?.let { Store.icons.put(pkg, it) }
        Store.radioSources = out.values.toList()
        Store.emit(Topic.SOURCES)
    }

    private fun icon(pkg: String): Bitmap? = try {
        val d = ctx.packageManager.getApplicationIcon(pkg)
        Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { b -> d.setBounds(0, 0, 96, 96); d.draw(Canvas(b)) }
    } catch (_: Throwable) { null }

    fun command(a: String, pos: Long) {
        if (inApp) {
            when (a) {
                "play" -> Audio.resume(); "pause" -> Audio.pause(); "toggle" -> Audio.toggle(); "seek" -> Audio.seekTo(pos)
                "next" -> if (Audio.live) Audio.onSkip?.invoke(1) else Audio.seekBy(30_000)
                "prev" -> if (Audio.live) Audio.onSkip?.invoke(-1) else Audio.seekBy(-15_000)
            }
            return
        }
        val tc = primary?.transportControls ?: return
        when (a) {
            "play" -> tc.play(); "pause" -> tc.pause(); "toggle" -> if (isPlaying(primary)) tc.pause() else tc.play()
            "next" -> tc.skipToNext(); "prev" -> tc.skipToPrevious(); "seek" -> tc.seekTo(pos)
        }
    }

    /** Make a radio app the source: resume its session, or open it so the user can pick something. */
    fun select(a: android.app.Activity, pkg: String, dark: Boolean) {
        if (Store.localActive) Hub.player.deactivate()
        if (InApp.key(pkg)) { Audio.resume(); return }
        val c = controllers.firstOrNull { it.packageName == pkg }
        if (c != null) { primary = c; Store.appActive = true; c.transportControls.play(); Hub.send(pkt(Proto.LOCAL_PLAYING, "on" to true)); publish(); return }
        AddonStore.open(a, pkg, dark)
    }

    fun deactivate() {
        if (!Store.appActive) return
        if (inApp) { inApp = false; if (Audio.playing) Audio.pause() }
        primary?.let { if (isPlaying(it)) it.transportControls.pause() }
        Store.appActive = false
        Store.emit(Topic.NP, Topic.ART)
    }
}
