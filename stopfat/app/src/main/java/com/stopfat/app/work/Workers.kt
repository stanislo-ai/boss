package com.stopfat.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stopfat.app.MainActivity
import com.stopfat.app.R
import com.stopfat.app.StopFatApp
import com.stopfat.app.domain.Nutrition
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Periodically samples the hardware step counter so daily steps are kept even when the app is closed. */
class StepWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as StopFatApp
        runCatching { app.container.stepCounter.readOnce() }
        return Result.success()
    }
}

/** Gentle reminders: log meals, drink water, finish fasting. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as StopFatApp
        val c = app.container
        val profile = c.store.current()
        if (!profile.remindersEnabled || !profile.onboarded) return Result.success()
        val now = LocalTime.now()
        val today = LocalDate.now().toEpochDay()

        if (profile.fastingStartMillis > 0) {
            val end = profile.fastingStartMillis + profile.fastingHours * 3_600_000L
            if (System.currentTimeMillis() >= end && System.currentTimeMillis() - end < 3 * 3_600_000L) {
                Notifications.show(applicationContext, 3, "Post zakończony! 🎉", "${profile.fastingHours} h postu za Tobą. Zakończ go w aplikacji i zjedz zbilansowany posiłek.")
                return Result.success()
            }
        }
        if (now.hour < 9 || now.hour >= 21) return Result.success()

        val entries = c.db.food().entriesForDayOnce(today)
        val water = c.db.dayStats().dayOnce(today)?.waterMl ?: 0
        val kcal = entries.sumOf { it.kcal }.toInt()
        val goal = Nutrition.calorieGoal(profile)
        when {
            entries.isEmpty() && now.hour >= 11 ->
                Notifications.show(applicationContext, 1, "Nie zapomnij o dzienniku 🍽️", "Dodaj dzisiejsze posiłki – zdjęcie AI zajmie 5 sekund.")
            water < profile.waterGoalMl * (now.hour - 8) / 13 ->
                Notifications.show(applicationContext, 2, "Czas na wodę 💧", "Dziś: ${water} ml z ${profile.waterGoalMl} ml. Szklanka wody to dobry pomysł!")
            now.hour >= 19 && kcal in 1 until goal - 600 ->
                Notifications.show(applicationContext, 4, "Zostało Ci ${goal - kcal} kcal", "Pamiętaj o kolacji bogatej w białko – zobacz przepisy w StopFat.")
        }
        return Result.success()
    }
}

object Notifications {
    const val CHANNEL = "stopfat_reminders"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "Przypomnienia StopFat", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Przypomnienia o posiłkach, wodzie i poście"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun show(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}

object WorkScheduler {
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "steps",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StepWorker>(15, TimeUnit.MINUTES).build(),
        )
        wm.enqueueUniquePeriodicWork(
            "reminders",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(3, TimeUnit.HOURS).build(),
        )
    }
}
