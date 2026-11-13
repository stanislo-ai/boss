package pl.boss.drive.phone

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import org.json.JSONArray
import pl.boss.drive.core.net.Proto
import pl.boss.drive.core.net.obj
import pl.boss.drive.core.net.pkt
import pl.boss.drive.core.util.Img

/**
 * Mirrors whatever media app is playing on the phone (Spotify, YouTube Music, Tidal, radio apps…)
 * through MediaSessionManager — needs notification-listener access, no per-app integration.
 */
class MediaBridge(private val ctx: Context) {
    private companion object { const val RADIO_PKG = "pl.boss.drive.radio" }
    private val msm = ctx.getSystemService(MediaSessionManager::class.java)
    private val comp = ComponentName(ctx, NotifListener::class.java)
    private val h = CarLink.handler
    private var controllers: List<MediaController> = emptyList()
    private var primary: MediaController? = null
    private var lastArtKey = ""
    private var cachedMetaKey = ""
    private var cachedArt: Bitmap? = null
    private var lastPlaying = false
    private val sentIcons = HashSet<String>()
    @Volatile var lastTitle = ""; private set
    @Volatile var lastArtist = ""; private set
    @Volatile var lastApp = ""; private set
    @Volatile var playing = false; private set
    private var started = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> h.post { setControllers(list ?: emptyList()) } }

    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { h.post { maybeSwitchPrimary(); publish() } }
        override fun onMetadataChanged(metadata: MediaMetadata?) { h.post { publish() } }
        override fun onQueueChanged(queue: MutableList<android.media.session.MediaSession.QueueItem>?) { h.post { publishQueue() } }
        override fun onSessionDestroyed() { h.post { refresh() } }
    }

    fun start() {
        if (started) return
        if (!Perms.notificationAccess(ctx)) return
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, comp, h)
            started = true
            refresh()
        } catch (e: SecurityException) { /* listener access not granted yet */ }
    }

    fun stop() {
        if (!started) return
        try { msm.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Throwable) {}
        primary?.unregisterCallback(cb)
        controllers.forEach { if (it !== primary) try { it.unregisterCallback(cb) } catch (_: Throwable) {} }
        primary = null; started = false
    }

    fun refresh() {
        if (!started) { start(); return }
        try { setControllers(msm.getActiveSessions(comp)) } catch (_: SecurityException) {}
    }

    private fun setControllers(list: List<MediaController>) {
        controllers.forEach { try { it.unregisterCallback(cb) } catch (_: Throwable) {} }
        controllers = list.filter { it.packageName != RADIO_PKG && (it.packageName != ctx.packageName || it.sessionToken == LocalLibrary.player?.token()) }
        controllers.forEach { it.registerCallback(cb, h) }
        primary = pickPrimary()
        publish(); publishQueue(); publishSources()
    }

    private fun isPlaying(c: MediaController?) = c?.playbackState?.state.let {
        it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING || it == PlaybackState.STATE_CONNECTING
    }

    private fun pickPrimary(): MediaController? = controllers.firstOrNull { isPlaying(it) } ?: controllers.firstOrNull { it.metadata != null } ?: controllers.firstOrNull()

    private fun maybeSwitchPrimary() {
        if (!isPlaying(primary)) {
            val p = controllers.firstOrNull { isPlaying(it) }
            if (p != null && p !== primary) { primary = p; publishQueue(); publishSources(); lastArtKey = "" }
        }
    }

    private fun appLabel(pkg: String): String = try {
        val pm = ctx.packageManager; pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Throwable) { pkg }

    fun pushAll() { lastArtKey = ""; sentIcons.clear(); refresh(); if (!started) publish() }

    fun publish() {
        val c = primary
        val md = c?.metadata
        val st = c?.playbackState
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val playingNow = isPlaying(c)
        var pos = st?.position ?: 0L
        if (st != null && st.state == PlaybackState.STATE_PLAYING && st.lastPositionUpdateTime > 0) {
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        if (dur > 0) pos = pos.coerceIn(0, dur)
        val actions = st?.actions ?: 0L
        val pkg = c?.packageName ?: ""
        val app = if (pkg == ctx.packageName) "Muzyka z telefonu" else if (pkg.isEmpty()) "" else appLabel(pkg)
        val metaKey = "$pkg|$title|$artist|$album"
        val artBmp = if (md == null) null else if (metaKey == cachedMetaKey && cachedArt != null) cachedArt else art(md).also { cachedArt = it; cachedMetaKey = metaKey }
        val artKey = if (title.isEmpty()) "" else (title + "|" + artist + "|" + album + "|" + (artBmp?.width ?: 0)).hashCode().toString()
        val userStarted = playingNow && !lastPlaying && SystemClock.elapsedRealtime() - CarLink.lastRadioCmdAt > 3000
        lastPlaying = playingNow
        lastTitle = title; lastArtist = artist; lastApp = app; playing = playingNow
        CarLink.send(pkt(Proto.NOW_PLAYING,
            "title" to title, "artist" to artist, "album" to album, "dur" to dur, "pos" to pos,
            "playing" to playingNow, "speed" to (st?.playbackSpeed ?: 1f).toDouble().let { if (it <= 0) 1.0 else it },
            "pkg" to pkg, "app" to app, "art" to (if (artBmp != null) artKey else ""),
            "canSeek" to (actions and PlaybackState.ACTION_SEEK_TO != 0L),
            "canSkip" to (actions and (PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L || actions == 0L),
            "user" to userStarted,
        ))
        if (artBmp != null && artKey != lastArtKey) {
            lastArtKey = artKey
            CarLink.send(pkt(Proto.ART, "key" to artKey, body = Img.jpeg(artBmp, 400, 85)))
        }
        Events.emit(Ev.NP)
    }

    private fun art(md: MediaMetadata): Bitmap? {
        md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { return it }
        md.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { return it }
        md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.let { return it }
        for (k in arrayOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)) {
            val u = md.getString(k) ?: continue
            try {
                val uri = Uri.parse(u)
                if (uri.scheme == "content" || uri.scheme == "file" || uri.scheme == "android.resource") {
                    ctx.contentResolver.openInputStream(uri)?.use { return Img.decode(it.readBytes(), 600) }
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    fun publishQueue() {
        val c = primary ?: return
        val q = try { c.queue } catch (_: Throwable) { null } ?: emptyList()
        val items = JSONArray()
        for (it in q.take(150)) items.put(obj("id" to it.queueId, "title" to (it.description.title?.toString() ?: ""), "sub" to (it.description.subtitle?.toString() ?: "")))
        CarLink.send(pkt(Proto.QUEUE, "items" to items, "active" to (c.playbackState?.activeQueueItemId ?: -1L)))
    }

    /** Active sessions first, then other installed media apps the user can start from the car. */
    fun publishSources() {
        val items = JSONArray()
        val seen = HashSet<String>()
        for (c in controllers) {
            val pkg = c.packageName
            if (pkg == ctx.packageName || !seen.add(pkg)) continue
            items.put(obj("pkg" to pkg, "name" to appLabel(pkg), "active" to true, "playing" to isPlaying(c)))
            sendIcon(pkg)
        }
        for (pkg in installedMediaApps()) {
            if (!seen.add(pkg)) continue
            items.put(obj("pkg" to pkg, "name" to appLabel(pkg), "active" to false, "playing" to false))
            sendIcon(pkg)
        }
        CarLink.send(pkt(Proto.SOURCES, "items" to items))
    }

    private fun installedMediaApps(): List<String> {
        val pm = ctx.packageManager
        val out = LinkedHashSet<String>()
        try {
            pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), 0).forEach { out.add(it.serviceInfo.packageName) }
        } catch (_: Throwable) {}
        out.remove(ctx.packageName)
        // keep the list short and relevant for the car
        val preferred = listOf("com.spotify.music", "com.google.android.apps.youtube.music", "com.aspiro.tidal", "deezer.android.app",
            "com.apple.android.music", "com.amazon.mp3", "com.soundcloud.android", "pl.rmf.fm", "com.audioteka", "com.storytel")
        return out.sortedBy { val i = preferred.indexOf(it); if (i < 0) 99 else i }.take(8)
    }

    private fun sendIcon(pkg: String) {
        if (!sentIcons.add(pkg)) return
        try {
            val d = ctx.packageManager.getApplicationIcon(pkg)
            val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b); d.setBounds(0, 0, 96, 96); d.draw(cv)
            CarLink.send(pkt(Proto.ICON, "pkg" to pkg, body = Img.png(b, 96)))
            b.recycle()
        } catch (_: Throwable) {}
    }

    // ------------------------------------------------------------------ commands from the car

    fun command(a: String, pos: Long) = h.post {
        val c = primary
        val tc = c?.transportControls
        if (tc == null) {
            if (a == "play" || a == "toggle") mediaButton(lastPkg(), KeyEvent.KEYCODE_MEDIA_PLAY)
            return@post
        }
        when (a) {
            "play" -> tc.play()
            "pause" -> tc.pause()
            "toggle" -> if (isPlaying(c)) tc.pause() else tc.play()
            "next" -> tc.skipToNext()
            "prev" -> tc.skipToPrevious()
            "seek" -> tc.seekTo(pos)
        }
    }

    private fun lastPkg() = CarLink.prefs.getString("lastMediaPkg", "com.spotify.music") ?: "com.spotify.music"

    fun selectSource(pkg: String) = h.post {
        CarLink.prefs.edit().putString("lastMediaPkg", pkg).apply()
        controllers.forEach { if (it.packageName != pkg && isPlaying(it)) it.transportControls.pause() }
        val c = controllers.firstOrNull { it.packageName == pkg }
        if (c != null) { primary = c; c.transportControls.play(); publish(); publishQueue() }
        else mediaButton(pkg, KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    fun playQueueItem(id: Long) = h.post { primary?.transportControls?.skipToQueueItem(id) }

    /** Head unit started its own offline playback — silence the phone. */
    fun pauseAll() = h.post { controllers.forEach { if (isPlaying(it)) it.transportControls.pause() } }

    /** Wake an app that has no session yet (e.g. Spotify not opened since boot). */
    private fun mediaButton(pkg: String, code: Int) {
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val i = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg).putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, code))
            try { ctx.sendBroadcast(i) } catch (_: Throwable) {}
        }
        h.postDelayed({ refresh() }, 1500)
    }
}
