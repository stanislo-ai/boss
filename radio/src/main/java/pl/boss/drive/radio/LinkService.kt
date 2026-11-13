package pl.boss.drive.radio

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import pl.boss.drive.core.net.Discovery
import pl.boss.drive.core.net.Link
import pl.boss.drive.core.net.Proto
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Always-on (but very light) service on the head unit:
 *  - TCP server + UDP beacon for the phone on Wi-Fi/hotspot
 *  - RFCOMM server as Bluetooth fallback (if the unit exposes a standard BT stack)
 *  - reacts to ACC-on / screen-on to bring the UI up
 */
class LinkService : Service() {
    @Volatile private var running = false
    private var server: ServerSocket? = null
    private var btServer: BluetoothServerSocket? = null
    private var announcer: Discovery.Announcer? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var screenOffAt = 0L

    private val screenRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> screenOffAt = SystemClock.elapsedRealtime()
                Intent.ACTION_SCREEN_ON -> {
                    // head units "sleep" with ACC off instead of rebooting: treat a long screen-off as ignition cycle
                    if (screenOffAt > 0 && SystemClock.elapsedRealtime() - screenOffAt > 45_000 && Hub.prefs.getBoolean("openOnBoot", true)) bringToFront(c)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        running = true
        try {
            multicast = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("bossdrive").apply { setReferenceCounted(false); acquire() }
        } catch (_: Throwable) {}
        announcer = Discovery.Announcer(Hub.deviceId) { Hub.deviceName }.also { it.start() }
        Thread({ tcpLoop() }, "tcp-server").apply { isDaemon = true }.start()
        Thread({ btLoop() }, "bt-server").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
        registerReceiver(screenRx, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
        // stop shouting beacons once we have a link; resume when it drops
        Store.on(Topic.LINK) { announcer?.paused = Store.connected && Hub.link?.isWifi == true }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun tcpLoop() {
        while (running) {
            try {
                val ss = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(Proto.PORT_TCP)) }
                server = ss
                while (running) {
                    val s = ss.accept()
                    s.tcpNoDelay = true
                    s.soTimeout = 16_000
                    s.keepAlive = true
                    try { s.sendBufferSize = 256 * 1024; s.receiveBufferSize = 256 * 1024 } catch (_: Throwable) {}
                    Hub.attach(Link("wifi", s.inetAddress.hostAddress ?: "", s.getInputStream(), s.getOutputStream(), s, Hub))
                }
            } catch (e: Throwable) {
                if (running) { Log.w(TAG, "tcp: ${e.message}"); sleep(2000) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun btLoop() {
        while (running) {
            try {
                val ad = BluetoothAdapter.getDefaultAdapter()
                if (ad == null || !ad.isEnabled) { sleep(15_000); continue }
                val ss = ad.listenUsingInsecureRfcommWithServiceRecord(Proto.BT_NAME, Proto.BT_UUID)
                btServer = ss
                while (running) {
                    val s = ss.accept()
                    Hub.attach(Link("bt", s.remoteDevice?.name ?: "", s.inputStream, s.outputStream, s, Hub))
                }
            } catch (e: Throwable) {
                // Many head units route BT through a vendor module without a standard stack: just retry slowly
                sleep(20_000)
            }
        }
    }

    private fun sleep(ms: Long) = try { Thread.sleep(ms) } catch (_: InterruptedException) {}

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("link", "Połączenie z telefonem", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "link")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Boss Drive")
            .setContentText("Gotowe do połączenia z telefonem")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running = false
        announcer?.stop()
        try { server?.close() } catch (_: Throwable) {}
        try { btServer?.close() } catch (_: Throwable) {}
        try { unregisterReceiver(screenRx) } catch (_: Throwable) {}
        multicast?.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LinkService"

        fun start(c: Context) {
            try { c.startForegroundService(Intent(c, LinkService::class.java)) } catch (e: Throwable) { Log.w(TAG, "start: ${e.message}") }
        }

        fun bringToFront(c: Context) {
            if (MainActivity.visible) return
            try {
                c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            } catch (_: Throwable) {}
        }
    }
}

/** Ignition / boot: start the service and (optionally) show the UI. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        LinkService.start(c)
        val prefs = c.getSharedPreferences("radio", Context.MODE_PRIVATE)
        if (prefs.getBoolean("openOnBoot", true)) {
            // give the launcher a moment so we land on top of it
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ LinkService.bringToFront(c) }, 2500)
        }
    }
}
