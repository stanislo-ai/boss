package pl.boss.drive.radio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import pl.boss.drive.core.net.Link
import pl.boss.drive.core.net.Packet
import pl.boss.drive.core.net.Proto
import pl.boss.drive.core.net.forEachObj
import pl.boss.drive.core.net.obj
import pl.boss.drive.core.net.pkt
import pl.boss.drive.core.net.str
import pl.boss.drive.core.util.Bg
import pl.boss.drive.core.util.Img
import java.io.File
import java.io.RandomAccessFile

/** Head-unit brain: owns the phone link, routes packets into [Store], exposes actions to the UI. */
@SuppressLint("StaticFieldLeak")
object Hub : Link.Listener {
    lateinit var app: Context; private set
    lateinit var db: TrackDb; private set
    lateinit var player: OfflinePlayer; private set
    lateinit var prefs: android.content.SharedPreferences; private set
    @Volatile var link: Link? = null; private set
    val deviceId: String by lazy {
        prefs.getString("id", null) ?: java.util.UUID.randomUUID().toString().take(12).also { prefs.edit().putString("id", it).apply() }
    }
    val deviceName: String get() = prefs.getString("name", null) ?: "Radio " + android.os.Build.MODEL

    fun init(ctx: Context) {
        app = ctx.applicationContext
        prefs = app.getSharedPreferences("radio", Context.MODE_PRIVATE)
        db = TrackDb(app)
        player = OfflinePlayer(app, db) { active ->
            send(pkt(Proto.LOCAL_PLAYING, "on" to active))
        }
    }

    // ------------------------------------------------------------------ link management

    /** Called by [LinkService] for every accepted socket. Prefer Wi-Fi over Bluetooth. */
    fun attach(l: Link) {
        val cur = link
        if (cur != null && cur.isOpen) {
            if (cur.isWifi && !l.isWifi) { l.close(); return }
            link = l
            cur.close()
        } else link = l
        l.start()
    }

    override fun onClosed(link: Link) {
        if (this.link === link) {
            this.link = null
            Store.connected = false
            Store.transport = ""
            Store.transfer = null
            if (Store.call.active) { Store.call = CallState(); Store.ui { player.onCallState(false) } }
            Store.phoneNp = Store.phoneNp.copy(playing = false)
            Store.emit(Topic.LINK, Topic.TRANSFER, Topic.CALL, Topic.NP)
            closeIncoming()
        }
    }

    fun send(p: Packet) { link?.takeIf { it.authed }?.send(p) }

    val pairedId get() = prefs.getString("paired", "") ?: ""

    fun acceptPending(accept: Boolean) {
        val pending = Store.pendingPhone ?: return
        Store.pendingPhone = null
        val l = link
        if (accept && l != null && l.remoteId == pending.first) {
            prefs.edit().putString("paired", pending.first).putString("pairedName", pending.second).apply()
            authorize(l)
        } else l?.close()
        Store.emit(Topic.PAIRING)
    }

    fun forgetPhone() {
        prefs.edit().remove("paired").remove("pairedName").apply()
        link?.close()
    }

    private fun authorize(l: Link) {
        l.authed = true
        Store.connected = true
        Store.transport = l.transport
        Store.phoneName = l.remoteName
        l.send(pkt(Proto.HELLO, "id" to deviceId, "name" to deviceName, "ver" to Proto.VERSION, "paired" to true))
        l.send(pkt(Proto.REFRESH))
        l.send(pkt(Proto.STORE_REQ))
        sendLibrary()
        if (Store.localActive) l.send(pkt(Proto.LOCAL_PLAYING, "on" to true))
        Store.emit(Topic.LINK)
        if (prefs.getBoolean("openOnConnect", false)) LinkService.bringToFront(app)
    }

    // ------------------------------------------------------------------ incoming packets

    override fun onPacket(link: Link, p: Packet) {
        if (p.type == Proto.HELLO) {
            link.remoteId = p.s("id"); link.remoteName = p.s("name", "Telefon")
            val paired = pairedId
            if (paired.isEmpty() || paired == link.remoteId) {
                if (paired.isEmpty()) prefs.edit().putString("paired", link.remoteId).putString("pairedName", link.remoteName).apply()
                authorize(link)
            } else {
                Store.pendingPhone = link.remoteId to link.remoteName
                link.send(pkt(Proto.HELLO, "id" to deviceId, "name" to deviceName, "ver" to Proto.VERSION, "paired" to false))
                Store.emit(Topic.PAIRING)
            }
            return
        }
        if (!link.authed) return
        when (p.type) {
            Proto.NOW_PLAYING -> {
                Store.phoneNp = NowPlaying(
                    title = p.s("title"), artist = p.s("artist"), album = p.s("album"),
                    duration = p.l("dur"), position = p.l("pos"), posAt = SystemClock.elapsedRealtime(),
                    playing = p.b("playing"), speed = p.d("speed", 1.0).toFloat(), pkg = p.s("pkg"), appName = p.s("app"),
                    artKey = p.s("art"), canSeek = p.b("canSeek", true), canSkip = p.b("canSkip", true),
                )
                if (Store.phoneNp.artKey.isEmpty() && Store.phoneArt != null) {
                    Store.phoneArt = null; Store.phoneBackdrop = null; Store.phoneTint = 0xFF3A3A3C.toInt(); Store.emit(Topic.ART)
                }
                // phone started playing something itself -> step aside
                if (Store.phoneNp.playing && p.b("user")) Store.ui { if (Store.localActive) player.deactivate(); AppMedia.deactivate() }
                Store.emit(Topic.NP)
            }
            Proto.ART -> p.body?.let { bytes ->
                val key = p.s("key")
                Bg.run {
                    val bmp = Img.decode(bytes, 512) ?: return@run
                    val back = Img.backdrop(bmp); val tint = Img.dominant(bmp)
                    Store.ui {
                        if (Store.phoneNp.artKey.isNotEmpty() && Store.phoneNp.artKey != key) return@ui
                        Store.phoneArt = bmp; Store.phoneBackdrop = back; Store.phoneTint = tint
                        Store.emit(Topic.ART)
                    }
                }
            }
            Proto.SOURCES -> {
                val out = ArrayList<Source>()
                p.arr("items").forEachObj { out.add(Source(it.str("pkg"), it.str("name"), it.optBoolean("active"), it.optBoolean("playing"))) }
                Store.sources = out; Store.emit(Topic.SOURCES)
            }
            Proto.ICON -> p.body?.let { b -> Img.decode(b, 128)?.let { Store.icons.put(p.s("pkg"), it); Store.emit(Topic.SOURCES) } }
            Proto.QUEUE -> {
                val out = ArrayList<QueueItem>()
                p.arr("items").forEachObj { out.add(QueueItem(it.optLong("id"), it.str("title"), it.str("sub"))) }
                Store.queue = out; Store.queueActive = p.l("active", -1); Store.emit(Topic.QUEUE)
            }
            Proto.LIBRARY -> {
                val out = ArrayList<LibItem>()
                p.arr("items").forEachObj { out.add(LibItem(it.str("id"), it.str("title"), it.str("sub"), it.str("kind"), it.optLong("dur"))) }
                Store.library.put(p.s("req"), p.s("title") to out); Store.emit(Topic.LIBRARY)
            }
            Proto.CALL -> {
                val prev = Store.call
                val c = CallState(p.s("state", "idle"), p.s("number"), p.s("name"), p.s("photo"), p.l("since"))
                if (c.state == "active" && prev.state != "active") Store.callStartedAt = SystemClock.elapsedRealtime() - c.since
                Store.call = c
                if (prev.active != c.active) Store.ui { player.onCallState(c.active) }
                Store.emit(Topic.CALL)
            }
            Proto.CONTACTS -> {
                fun parse(a: JSONArray) = ArrayList<Contact>().also { l -> a.forEachObj { l.add(Contact(it.str("n"), it.str("num"), it.str("lbl"), it.str("ph"))) } }
                Store.favorites = parse(p.arr("fav")); Store.contacts = parse(p.arr("all")); Store.emit(Topic.CONTACTS)
            }
            Proto.PHOTO -> p.body?.let { b -> Img.decode(b, 160)?.let { Store.photos.put(p.s("key"), it); Store.emit(Topic.CONTACTS, Topic.CALL) } }
            Proto.RECENTS -> {
                val out = ArrayList<Recent>()
                p.arr("items").forEachObj { out.add(Recent(it.str("n"), it.str("num"), it.optInt("type"), it.optLong("time"))) }
                Store.recents = out; Store.emit(Topic.RECENTS)
            }
            Proto.STATUS -> {
                Store.status = PhoneStatus(p.i("bat", -1), p.b("chg"), p.i("sig"), p.s("net"), p.s("carrier")); Store.emit(Topic.STATUS)
            }
            Proto.WEATHER -> {
                Store.weather = Weather(p.i("temp"), p.i("code"), p.i("hi"), p.i("lo"), p.s("place"), p.b("day", true), p.d("lat"), p.d("lon"))
                prefs.edit().putFloat("lat", p.d("lat").toFloat()).putFloat("lon", p.d("lon").toFloat()).apply()
                Store.emit(Topic.WEATHER, Topic.THEME)
            }
            Proto.MESSAGE -> {
                val m = Message(p.s("key"), p.s("pkg"), p.s("app"), p.s("title"), p.s("text"), p.l("time", System.currentTimeMillis()), p.b("canReply"))
                synchronized(Store.messages) {
                    Store.messages.removeAll { it.key == m.key }
                    Store.messages.add(0, m)
                    while (Store.messages.size > 40) Store.messages.removeAt(Store.messages.size - 1)
                }
                if (!p.b("silent")) { Store.unread++; MainActivity.banner(m) }
                Store.emit(Topic.MESSAGES)
            }
            Proto.MESSAGE_GONE -> { synchronized(Store.messages) { Store.messages.removeAll { it.key == p.s("key") } }; Store.emit(Topic.MESSAGES) }
            Proto.TOAST -> MainActivity.toast(p.s("text"))
            Proto.X_OFFER -> onOffer(p)
            Proto.X_CHUNK -> onChunk(p)
            Proto.X_END -> onEnd(p)
            Proto.R_LIB_REQ -> sendLibrary()
            Proto.R_DELETE -> {
                val ids = p.arr("ids")
                for (i in 0 until ids.length()) {
                    val id = ids.optLong(i)
                    if (player.current?.id == id) Store.ui { player.deactivate() }
                    db.delete(id)
                }
                sendLibrary(); Store.emit(Topic.OFFLINE)
            }
            Proto.STORE -> AddonStore.onCatalog(p)
            Proto.APP_CHUNK -> AddonStore.onChunk(p)
            Proto.APP_END -> AddonStore.onEnd(p)
            Proto.BYE -> link.close()
        }
    }

    // ------------------------------------------------------------------ actions (UI -> phone)

    fun cmd(action: String, pos: Long = 0) {
        if (Store.localActive) {
            when (action) {
                "play" -> player.play(); "pause" -> player.pause(); "toggle" -> player.toggle()
                "next" -> player.next(); "prev" -> player.prev(); "seek" -> player.seek(pos)
            }
            return
        }
        if (Store.appActive) { AppMedia.command(action, pos); return }
        // optimistic UI: flip play state immediately, phone confirms a moment later
        val np = Store.phoneNp
        when (action) {
            "toggle", "play", "pause" -> {
                val playing = when (action) { "play" -> true; "pause" -> false; else -> !np.playing }
                Store.phoneNp = np.copy(playing = playing, position = np.livePosition(), posAt = SystemClock.elapsedRealtime())
                Store.emit(Topic.NP)
            }
            "seek" -> { Store.phoneNp = np.copy(position = pos, posAt = SystemClock.elapsedRealtime()); Store.emit(Topic.NP) }
        }
        send(pkt(Proto.CMD, "a" to action, "pos" to pos))
    }

    fun usePhone(pkg: String) {
        if (Store.localActive) player.deactivate()
        AppMedia.deactivate()
        send(pkt(Proto.SELECT_SOURCE, "pkg" to pkg))
    }

    fun playOffline(tracks: List<Track>, start: Int, shuffle: Boolean = false) {
        player.playList(tracks, start, shuffle)
    }

    fun requestLibrary(req: String) = send(pkt(Proto.LIB_REQ, "req" to req))
    fun playLibrary(req: String, index: Int) { if (Store.localActive) player.deactivate(); AppMedia.deactivate(); send(pkt(Proto.LIB_PLAY, "req" to req, "index" to index)) }
    fun playQueue(id: Long) = send(pkt(Proto.QUEUE_PLAY, "id" to id))
    fun call(a: String, num: String = "", text: String = "") = send(pkt(Proto.CALL_CMD, "a" to a, "num" to num, "text" to text))
    fun message(key: String, a: String, text: String = "") = send(pkt(Proto.MSG_CMD, "key" to key, "a" to a, "text" to text))

    // ------------------------------------------------------------------ offline library sync

    fun musicDir(): File = (app.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(app.filesDir, "music")).also { it.mkdirs() }

    fun freeBytes(): Long = try { StatFs(musicDir().path).availableBytes } catch (_: Throwable) { 0L }

    fun sendLibrary() {
        Bg.run {
            val items = JSONArray()
            for (t in db.all()) items.put(obj("id" to t.id, "xid" to t.xid, "title" to t.title, "artist" to t.artist, "dur" to t.duration, "size" to t.size))
            send(pkt(Proto.R_LIB, "items" to items, "used" to db.totalBytes(), "free" to freeBytes()))
        }
    }

    private class Incoming(val id: String, val title: String, val artist: String, val album: String, val dur: Long,
                           val size: Long, val ext: String, val part: File, val art: ByteArray?, var raf: RandomAccessFile?, var done: Long)
    private var incoming: Incoming? = null
    private var lastEmit = 0L

    private fun closeIncoming() { incoming?.raf?.let { try { it.close() } catch (_: Throwable) {} }; incoming = null }

    private fun onOffer(p: Packet) {
        closeIncoming()
        val id = p.s("id"); val size = p.l("size")
        if (db.byXid(id) != null) { send(pkt(Proto.X_DONE, "id" to id, "dup" to true)); return }
        if (freeBytes() < size + 64L * 1024 * 1024) {
            send(pkt(Proto.X_FAIL, "id" to id, "reason" to "Brak miejsca na radiu")); return
        }
        val safe = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val part = File(musicDir(), "$safe.part")
        val have = if (part.exists()) part.length().coerceAtMost(size) else 0L
        val raf = RandomAccessFile(part, "rw").apply { setLength(have); seek(have) }
        incoming = Incoming(id, p.s("title"), p.s("artist"), p.s("album"), p.l("dur"), size, p.s("ext", "mp3"), part, p.body, raf, have)
        Store.transfer = Transfer(id, p.s("title"), p.s("artist"), size, have, p.i("queued")); Store.emit(Topic.TRANSFER)
        send(pkt(Proto.X_ACK, "id" to id, "off" to have))
    }

    private fun onChunk(p: Packet) {
        val inc = incoming ?: return
        val body = p.body ?: return
        if (p.s("id") != inc.id) return
        val off = p.l("off")
        if (off != inc.done) { send(pkt(Proto.X_ACK, "id" to inc.id, "off" to inc.done)); return }
        inc.raf?.write(body)
        inc.done += body.size
        val now = SystemClock.elapsedRealtime()
        if (now - lastEmit > 250) {
            lastEmit = now
            Store.transfer = Transfer(inc.id, inc.title, inc.artist, inc.size, inc.done, Store.transfer?.queued ?: 0); Store.emit(Topic.TRANSFER)
        }
    }

    private fun onEnd(p: Packet) {
        val inc = incoming ?: return
        if (p.s("id") != inc.id) return
        try { inc.raf?.fd?.sync() } catch (_: Throwable) {}
        try { inc.raf?.close() } catch (_: Throwable) {}
        inc.raf = null
        incoming = null
        if (inc.part.length() != inc.size) {
            send(pkt(Proto.X_FAIL, "id" to inc.id, "reason" to "Niekompletny plik", "retry" to true)); return
        }
        val base = (inc.artist + " - " + inc.title).replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
        var out = File(musicDir(), "$base.${inc.ext}")
        var n = 1
        while (out.exists()) out = File(musicDir(), "$base ($n).${inc.ext}").also { n++ }
        inc.part.renameTo(out)
        var artPath = ""
        if (inc.art != null && inc.art.isNotEmpty()) {
            val af = File(musicDir(), ".art").apply { mkdirs() }.let { File(it, out.nameWithoutExtension + ".jpg") }
            try { af.writeBytes(inc.art); artPath = af.path } catch (_: Throwable) {}
        }
        db.insert(inc.id, inc.title.ifEmpty { out.nameWithoutExtension }, inc.artist, inc.album, inc.dur, out.path, artPath, inc.size)
        send(pkt(Proto.X_DONE, "id" to inc.id))
        Store.lastReceived = inc.title
        Store.transfer = null
        Store.emit(Topic.TRANSFER, Topic.OFFLINE)
        sendLibrary()
    }

    fun openWifiSettings(ctx: Context) {
        try { ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
    }
}
