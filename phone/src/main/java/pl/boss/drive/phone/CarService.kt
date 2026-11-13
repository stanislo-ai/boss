package pl.boss.drive.phone

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import pl.boss.drive.phone.ui.MainActivity

/**
 * Runs while you're in the car. Started automatically when the car's Bluetooth connects,
 * stops a little after it disconnects (so a quick stop at the shop doesn't drop anything).
 */
class CarService : Service() {
    private var multicast: WifiManager.MulticastLock? = null
    private var wake: PowerManager.WakeLock? = null
    private val unsub = ArrayList<() -> Unit>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        CarLink.init(this)
        running = true
        val n = notification()
        if (Build.VERSION.SDK_INT >= 29) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (Build.VERSION.SDK_INT >= 30) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            try { startForeground(1, n, type) } catch (_: Throwable) { startForeground(1, n) }
        } else startForeground(1, n)
        multicast = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createMulticastLock("bossdrive").apply {
            setReferenceCounted(false); acquire()
        }
        // keep the CPU awake for the socket while the screen is off in the car (released when we stop)
        wake = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bossdrive:car").apply {
            setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L)
        }
        CarLink.start()
        unsub += Events.on(Ev.LINK) { refreshNotification() }
        unsub += Events.on(Ev.TRANSFERS) { refreshNotification() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_CAR_GONE -> CarLink.handler.postDelayed(stopIfIdle, 90_000)
            else -> CarLink.handler.removeCallbacks(stopIfIdle)
        }
        return START_STICKY
    }

    private val stopIfIdle = Runnable {
        if (CarLink.prefs.getBoolean("alwaysOn", false)) return@Runnable
        if (CarLink.transfers.current == null) stopSelf()
    }

    private var lastText = ""
    private fun refreshNotification() {
        val n = notification()
        val t = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (t == lastText) return
        lastText = t
        getSystemService(NotificationManager::class.java).notify(1, n)
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("car", "Połączenie z autem", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val text = when {
            CarLink.connected && CarLink.transfers.current != null -> "Wysyłam do auta: ${CarLink.transfers.current?.title}"
            CarLink.connected -> "Połączono z: ${CarLink.radioName}"
            CarLink.waitingApproval -> "Zatwierdź połączenie na ekranie radia"
            else -> "Szukam radia… (włącz hotspot w telefonie)"
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, CarService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val hotspot = PendingIntent.getActivity(this, 2, Intent(Settings.ACTION_WIRELESS_SETTINGS), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "car")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Boss Drive")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { if (!CarLink.connected) addAction(Notification.Action.Builder(null, "Hotspot", hotspot).build()) }
            .addAction(Notification.Action.Builder(null, "Rozłącz", stop).build())
            .build()
    }

    override fun onDestroy() {
        unsub.forEach { it() }
        running = false
        CarLink.stop()
        multicast?.release()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "stop"
        const val ACTION_CAR_GONE = "carGone"
        @Volatile var running = false

        fun start(c: Context, action: String? = null) {
            try { c.startForegroundService(Intent(c, CarService::class.java).setAction(action)) } catch (_: Throwable) {}
        }
    }
}

/**
 * Car detection: the head unit pairs with the phone for calls (hands-free), so its Bluetooth
 * connecting = "I'm in the car". The first time, any car-audio/hands-free device counts and
 * gets remembered once the radio app answers.
 */
class BtReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(c: Context, i: Intent) {
        val dev: BluetoothDevice = (if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)) ?: return
        CarLink.init(c)
        val prefs = CarLink.prefs
        if (!prefs.getBoolean("autoStart", true)) return
        val car = prefs.getString("carBt", "") ?: ""
        val isCar = if (car.isNotEmpty()) dev.address == car else try {
            val cls = dev.bluetoothClass?.deviceClass
            cls == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO || cls == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
        } catch (_: Throwable) { false }
        if (!isCar) return
        when (i.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                if (car.isEmpty()) prefs.edit().putString("carBtCandidate", dev.address).putString("carBtName", try { dev.name } catch (_: Throwable) { "" }).apply()
                CarService.start(c)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                CarLink.status.saveParking()
                if (CarService.running) CarService.start(c, CarService.ACTION_CAR_GONE)
            }
        }
    }
}
