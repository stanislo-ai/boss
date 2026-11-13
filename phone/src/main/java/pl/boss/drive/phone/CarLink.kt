package pl.boss.drive.phone

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import pl.boss.drive.core.net.Discovery
import pl.boss.drive.core.net.Link
import pl.boss.drive.core.net.Packet
import pl.boss.drive.core.net.Proto
import pl.boss.drive.core.net.pkt
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

enum class Ev { LINK, TRANSFERS, RADIO_LIB, NP, PERMS }

/** Tiny main-thread event bus for the phone UI. */
object Events {
    private val main = Handler(Looper.getMainLooper())
    private val subs = HashMap<Ev, MutableList<() -> Unit>>()
    fun on(e: Ev, f: () -> Unit): () -> Unit { subs.getOrPut(e) { ArrayList() }.add(f); return { subs[e]?.remove(f) } }
    fun emit(e: Ev) = main.post { subs[e]?.toList()?.forEach { it() } }
}

/**
 * Phone side of the car connection. Finds the head unit (UDP beacon on hotspot/Wi-Fi,
 * RFCOMM fallback), keeps exactly one authorised link and routes radio requests to bridges.
 */
@SuppressLint("StaticFieldLeak")
object CarLink : Link.Listener {
    private const val TAG = "CarLink"
    lateinit var ctx: Context; private set
    val prefs by lazy { ctx.getSharedPreferences("phone", Context.MODE_PRIVATE) }
    private val thread = HandlerThread("carlink").apply { start() }
    val handler = Handler(thread.looper)

    @Volatile var link: Link? = null; private set
    @Volatile var connected = false; private set
    @Volatile var waitingApproval = false; private set
    @Volatile var radioName = ""; private set
    @Volatile var running = false; private set
    @Volatile var searchingSince = 0L; private set
    @Volatile private var connecting = false
    @Volatile private var btConnecting = false
    private var finder: Discovery.Finder? = null
    @Volatile var lastRadioCmdAt = 0L

    val deviceId: String by lazy {
        prefs.getString("id", null) ?: UUID.randomUUID().toString().take(12).also { prefs.edit().putString("id", it).apply() }
    }
    val deviceName: String get() = try {
        Settings.Global.getString(ctx.contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL
    } catch (_: Throwable) { Build.MODEL }

    lateinit var media: MediaBridge; private set
    lateinit var calls: CallBridge; private set
    lateinit var status: StatusBridge; private set
    lateinit var library: LocalLibrary; private set
    lateinit var transfers: Transfers; private set

    fun init(c: Context) {
        if (::ctx.isInitialized) return
        ctx = c.applicationContext
        media = MediaBridge(ctx)
        calls = CallBridge(ctx)
        status = StatusBridge(ctx)
        library = LocalLibrary(ctx)
        transfers = Transfers(ctx)
    }

    fun start() {
        if (running) return
        running = true
        searchingSince = SystemClock.elapsedRealtime()
        finder = Discovery.Finder { b -> handler.post { onBeacon(b) } }.also { it.start() }
        media.start(); calls.start(); status.start()
        handler.post(watchdog)
        Events.emit(Ev.LINK)
    }

    fun stop() {
        if (!running) return
        running = false
        finder?.stop(); finder = null
        handler.removeCallbacks(watchdog)
        link?.let { it.send(Packet(Proto.BYE)); handler.postDelayed({ it.close() }, 300) }
        media.stop(); calls.stop(); status.stop()
        Events.emit(Ev.LINK)
    }

    /** Periodic: manual IP + Bluetooth fallback when Wi-Fi isn't there. */
    private val watchdog = object : Runnable {
        override fun run() {
            if (!running) return
            val l = link
            if (l == null || !l.isOpen) {
                prefs.getString("manualIp", "")?.takeIf { it.isNotBlank() }?.let { connectTcp(it.trim(), Proto.PORT_TCP) }
                if (SystemClock.elapsedRealtime() - searchingSince > 6000) connectBt()
            } else if (!l.isWifi && SystemClock.elapsedRealtime() - lastWifiTry > 20_000) {
                prefs.getString("lastIp", "")?.takeIf { it.isNotBlank() }?.let { lastWifiTry = SystemClock.elapsedRealtime(); connectTcp(it, Proto.PORT_TCP) }
            }
            handler.postDelayed(this, 5000)
        }
    }
    private var lastWifiTry = 0L

    private fun onBeacon(b: Discovery.Beacon) {
        val cur = link
        if (cur != null && cur.isOpen && cur.isWifi) return
        connectTcp(b.ip, b.port)
    }

    private fun connectTcp(ip: String, port: Int) {
        if (connecting || !running) return
        connecting = true
        Thread({
            try {
                val s = Socket()
                s.connect(InetSocketAddress(ip, port), 4000)
                s.tcpNoDelay = true; s.soTimeout = 16_000; s.keepAlive = true
                try { s.sendBufferSize = 256 * 1024; s.receiveBufferSize = 256 * 1024 } catch (_: Throwable) {}
                prefs.edit().putString("lastIp", ip).apply()
                attach(Link("wifi", ip, s.getInputStream(), s.getOutputStream(), s, this))
            } catch (e: Throwable) {
                Log.i(TAG, "tcp $ip: ${e.message}")
            } finally { connecting = false }
        }, "tcp-connect").start()
    }

    @SuppressLint("MissingPermission")
    private fun connectBt() {
        if (btConnecting || !running || !Perms.has(ctx, Perms.BT)) return
        val addr = prefs.getString("carBt", "") ?: ""
        if (addr.isEmpty()) return
        val ad: BluetoothAdapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
        if (!ad.isEnabled) return
        btConnecting = true
        Thread({
            try {
                val dev = ad.getRemoteDevice(addr)
                val s = dev.createInsecureRfcommSocketToServiceRecord(Proto.BT_UUID)
                try { ad.cancelDiscovery() } catch (_: Throwable) {}
                s.connect()
                attach(Link("bt", dev.name ?: addr, s.inputStream, s.outputStream, s, this))
            } catch (e: Throwable) {
                Log.i(TAG, "bt: ${e.message}")
                try { Thread.sleep(15_000) } catch (_: InterruptedException) {}
            } finally { btConnecting = false }
        }, "bt-connect").start()
    }

    private fun attach(l: Link) {
        handler.post {
            val cur = link
            if (cur != null && cur.isOpen) {
                if (cur.isWifi && !l.isWifi) { l.close(); return@post }
                link = l; cur.close()
            } else link = l
            l.start()
            l.send(pkt(Proto.HELLO, "id" to deviceId, "name" to deviceName, "ver" to Proto.VERSION))
        }
    }

    override fun onClosed(link: Link) {
        handler.post {
            if (this.link === link) {
                this.link = null
                val was = connected
                connected = false; waitingApproval = false
                searchingSince = SystemClock.elapsedRealtime()
                transfers.onLinkLost()
                if (was) Log.i(TAG, "link lost")
                Events.emit(Ev.LINK)
            }
        }
    }

    fun send(p: Packet) { val l = link; if (l != null && l.authed) l.send(p) }

    override fun onPacket(link: Link, p: Packet) {
        if (p.type == Proto.HELLO) {
            radioName = p.s("name", "Radio")
            if (p.b("paired")) {
                link.authed = true; connected = true; waitingApproval = false
                if (prefs.getString("carBt", "").isNullOrEmpty()) prefs.getString("carBtCandidate", null)?.let { prefs.edit().putString("carBt", it).apply() }
                link.remoteName = radioName
                Events.emit(Ev.LINK)
                handler.post { pushAll() }
            } else { waitingApproval = true; Events.emit(Ev.LINK) }
            return
        }
        if (!link.authed) return
        when (p.type) {
            Proto.REFRESH -> handler.post { pushAll() }
            Proto.CMD -> { lastRadioCmdAt = SystemClock.elapsedRealtime(); media.command(p.s("a"), p.l("pos")) }
            Proto.SELECT_SOURCE -> { lastRadioCmdAt = SystemClock.elapsedRealtime(); media.selectSource(p.s("pkg")) }
            Proto.QUEUE_PLAY -> media.playQueueItem(p.l("id"))
            Proto.LIB_REQ -> handler.post { library.answer(p.s("req")) }
            Proto.LIB_PLAY -> { lastRadioCmdAt = SystemClock.elapsedRealtime(); handler.post { library.play(p.s("req"), p.i("index")) } }
            Proto.CALL_CMD -> calls.command(p.s("a"), p.s("num"), p.s("text"))
            Proto.MSG_CMD -> NotifListener.command(ctx, p.s("key"), p.s("a"), p.s("text"))
            Proto.LOCAL_PLAYING -> if (p.b("on")) media.pauseAll()
            Proto.X_ACK, Proto.X_DONE, Proto.X_FAIL -> transfers.onRadio(p)
            Proto.R_LIB -> { transfers.onRadioLibrary(p); Events.emit(Ev.RADIO_LIB) }
            Proto.STORE_REQ -> handler.post { AddonServer.catalog(ctx) }
            Proto.APP_GET -> handler.post { AddonServer.send(ctx, p.s("pkg")) }
            Proto.BYE -> link.close()
        }
    }

    /** Everything the radio needs right after connecting. */
    private fun pushAll() {
        media.pushAll()
        calls.pushAll()
        status.pushAll()
        NotifListener.pushRecent()
        transfers.kick()
    }
}
