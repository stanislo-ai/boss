package com.stanislo.aura.system

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.stanislo.aura.MainActivity
import com.stanislo.aura.R
import com.stanislo.aura.data.LocalStore
import com.stanislo.aura.data.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

const val REMINDER_CHANNEL_ID = "aura_reminders"

fun Context.ensureReminderChannel() {
    val manager = getSystemService<NotificationManager>() ?: return
    if (manager.getNotificationChannel(REMINDER_CHANNEL_ID) != null) return
    val channel = NotificationChannel(
        REMINDER_CHANNEL_ID,
        getString(R.string.reminder_channel_name),
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = getString(R.string.reminder_channel_desc)
        enableVibration(true)
    }
    manager.createNotificationChannel(channel)
}

/** Planowanie i anulowanie przypomnien przez systemowy AlarmManager. */
class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService<AlarmManager>()

    /** @return true, jesli udalo sie zaplanowac alarm dokladny; false gdy tylko przyblizony. */
    fun schedule(reminder: Reminder): Boolean {
        val manager = alarmManager ?: return false
        val pendingIntent = pendingIntentFor(reminder, mutable = false)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        return try {
            if (exact) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerAt,
                    pendingIntent,
                )
                true
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerAt,
                    pendingIntent,
                )
                false
            }
        } catch (e: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pendingIntent)
            false
        }
    }

    fun cancel(reminder: Reminder) {
        alarmManager?.cancel(pendingIntentFor(reminder, mutable = false))
    }

    private fun pendingIntentFor(reminder: Reminder, mutable: Boolean): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ID, reminder.id)
            putExtra(EXTRA_TEXT, reminder.text)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, reminder.requestCode, intent, flags)
    }

    companion object {
        const val ACTION_FIRE = "com.stanislo.aura.REMINDER_FIRE"
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_TEXT = "reminder_text"
    }
}

/** Odbiera alarm i pokazuje powiadomienie. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(ReminderScheduler.EXTRA_TEXT) ?: return
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
        context.ensureReminderChannel()

        val openApp = PendingIntent.getActivity(
            context,
            id.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = Notification.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()

        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        }

        // Oznacz przypomnienie jako wykonane.
        val pending = goAsync()
        val store = LocalStore.get(context)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                store.reminders.awaitReady()
                store.reminders.update { list ->
                    list.map { if (it.id == id) it.copy(done = true) else it }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Po restarcie telefonu alarmy sa kasowane - odtwarzamy je z zapisanej listy. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pending = goAsync()
        val appContext = context.applicationContext
        val store = LocalStore.get(appContext)
        val scheduler = ReminderScheduler(appContext)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                appContext.ensureReminderChannel()
                store.reminders.awaitReady()
                val now = System.currentTimeMillis()
                store.reminders.items.value
                    .filter { !it.done && it.triggerAt > now }
                    .forEach { scheduler.schedule(it) }
            } finally {
                pending.finish()
            }
        }
    }
}
