package com.stanislo.aura.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import com.stanislo.aura.data.LocalStore
import com.stanislo.aura.data.Reminder
import com.stanislo.aura.system.ReminderScheduler
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlinx.serialization.json.JsonObject

/** Budziki, minutniki, przypomnienia i informacje o czasie. */
class TimeTools(
    private val context: Context,
    private val store: LocalStore,
    private val scheduler: ReminderScheduler,
) {

    fun setAlarm(args: JsonObject): JsonObject {
        val hour = args.int("hour") ?: return toolError("Brak godziny budzika.")
        val minute = args.int("minute") ?: 0
        if (hour !in 0..23 || minute !in 0..59) return toolError("Godzina poza zakresem.")

        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        args.str("label")?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }

        val days = args.strList("days").mapNotNull { dayToCalendar(it) }
        if (days.isNotEmpty()) {
            intent.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, ArrayList(days))
        }

        return if (context.launchActivity(intent)) {
            toolOk(String.format(Locale.getDefault(), "Ustawiono budzik na %02d:%02d.", hour, minute))
        } else {
            toolError("Nie znalazlem aplikacji Zegar, ktora obsluguje budziki.")
        }
    }

    fun setTimer(args: JsonObject): JsonObject {
        val seconds = args.int("seconds") ?: return toolError("Brak dlugosci odliczania.")
        if (seconds <= 0) return toolError("Dlugosc minutnika musi byc dodatnia.")

        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        args.str("label")?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }

        return if (context.launchActivity(intent)) {
            toolOk("Uruchomiono minutnik na ${humanDuration(seconds)}.")
        } else {
            toolError("Nie znalazlem aplikacji Zegar, ktora obsluguje minutniki.")
        }
    }

    suspend fun createReminder(args: JsonObject): JsonObject {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            return toolNeedsPermission(Manifest.permission.POST_NOTIFICATIONS, "wyswietlanie powiadomien")
        }

        val text = args.str("text") ?: return toolError("Brak tresci przypomnienia.")
        val at = TimeParsing.parse(args.str("time"))
            ?: return toolError("Nie rozpoznano terminu. Podaj go w formacie 2026-09-04T15:30:00.")
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) {
            return toolError("Podany termin jest w przeszlosci. Poproś uzytkownika o termin w przyszlosci.")
        }

        val reminder = Reminder(
            id = UUID.randomUUID().toString(),
            text = text,
            triggerAt = triggerAt,
            requestCode = abs(UUID.randomUUID().hashCode()),
        )
        val exact = scheduler.schedule(reminder)
        store.reminders.update { it + reminder }

        val note = if (exact) {
            ""
        } else {
            " Uwaga: system nie pozwala na alarm co do minuty - powiadomienie moze pojawic sie z niewielkim opoznieniem. " +
                "Mozna to zmienic w Ustawieniach Androida > Aplikacje > Aura > Alarmy i przypomnienia."
        }
        return toolOk(
            "Ustawiono przypomnienie na ${TimeParsing.format(triggerAt)}.$note",
            "reminder_id" to reminder.id,
        )
    }

    suspend fun listReminders(): JsonObject {
        store.reminders.awaitReady()
        val now = System.currentTimeMillis()
        val active = store.reminders.items.value
            .filter { !it.done && it.triggerAt > now }
            .sortedBy { it.triggerAt }
            .map {
                mapOf(
                    "reminder_id" to it.id,
                    "text" to it.text,
                    "time" to TimeParsing.format(it.triggerAt),
                )
            }
        return toolResult("status" to "ok", "count" to active.size, "reminders" to active)
    }

    suspend fun cancelReminder(args: JsonObject): JsonObject {
        store.reminders.awaitReady()
        val id = args.str("reminder_id") ?: return toolError("Brak identyfikatora przypomnienia.")
        val reminder = store.reminders.items.value.firstOrNull { it.id == id }
            ?: return toolError("Nie znaleziono przypomnienia o tym identyfikatorze.")
        scheduler.cancel(reminder)
        store.reminders.update { list -> list.filterNot { it.id == id } }
        return toolOk("Anulowano przypomnienie: ${reminder.text}")
    }

    fun getDateTime(): JsonObject {
        val now = LocalDateTime.now()
        val locale = Locale.getDefault()
        return toolResult(
            "status" to "ok",
            "iso" to now.toString(),
            "date" to now.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)),
            "time" to now.format(DateTimeFormatter.ofPattern("HH:mm", locale)),
            "day_of_week" to now.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale),
            "week_of_year" to now.get(WeekFields.of(locale).weekOfWeekBasedYear()),
            "timezone" to ZoneId.systemDefault().id,
        )
    }

    private fun dayToCalendar(day: String): Int? = when (day.trim().uppercase(Locale.ROOT)) {
        "MONDAY", "PONIEDZIALEK", "PONIEDZIAŁEK" -> Calendar.MONDAY
        "TUESDAY", "WTOREK" -> Calendar.TUESDAY
        "WEDNESDAY", "SRODA", "ŚRODA" -> Calendar.WEDNESDAY
        "THURSDAY", "CZWARTEK" -> Calendar.THURSDAY
        "FRIDAY", "PIATEK", "PIĄTEK" -> Calendar.FRIDAY
        "SATURDAY", "SOBOTA" -> Calendar.SATURDAY
        "SUNDAY", "NIEDZIELA" -> Calendar.SUNDAY
        else -> null
    }

    private fun humanDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return buildList {
            if (h > 0) add("$h h")
            if (m > 0) add("$m min")
            if (s > 0 && h == 0) add("$s s")
        }.joinToString(" ").ifEmpty { "$seconds s" }
    }
}
