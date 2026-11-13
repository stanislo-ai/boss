package pl.boss.drive.phone

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Permission groups shown as a checklist in the app. */
object Perms {
    val NOTIF = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
    val BT = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()
    val PHONE = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS)
    val CALL_LOG = arrayOf(Manifest.permission.READ_CALL_LOG)
    val CONTACTS = arrayOf(Manifest.permission.READ_CONTACTS)
    val SMS = arrayOf(Manifest.permission.SEND_SMS)
    val AUDIO = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    val LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val BG_LOCATION = if (Build.VERSION.SDK_INT >= 29) arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else emptyArray()

    fun has(c: Context, perms: Array<String>) = perms.all { c.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    fun notificationAccess(c: Context): Boolean {
        val flat = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(c, NotifListener::class.java).flattenToString()
        return flat.split(':').any { it == me || it.startsWith(c.packageName + "/") }
    }

    fun batteryUnrestricted(c: Context) = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)

    fun notificationsAllowed(c: Context) = (c.getSystemService(NotificationManager::class.java)).areNotificationsEnabled()
}
