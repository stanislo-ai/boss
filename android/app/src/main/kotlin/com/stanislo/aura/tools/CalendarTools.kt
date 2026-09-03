package com.stanislo.aura.tools

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.serialization.json.JsonObject

/** Dodawanie, odczyt i usuwanie wydarzen w kalendarzu urzadzenia. */
class CalendarTools(private val context: Context) {

    fun createEvent(args: JsonObject): JsonObject {
        if (!context.hasPermission(Manifest.permission.WRITE_CALENDAR)) {
            return toolNeedsPermission(Manifest.permission.WRITE_CALENDAR, "zapis w kalendarzu")
        }

        val title = args.str("title") ?: return toolError("Brak tytulu wydarzenia.")
        val start = TimeParsing.parse(args.str("start_time"))
            ?: return toolError("Nie rozpoznano daty poczatku. Podaj ja w formacie 2026-09-04T15:30:00.")
        val allDay = args.bool("all_day") ?: false
        val end = TimeParsing.parse(args.str("end_time"))
            ?: if (allDay) start.plusDays(1) else start.plusHours(1)

        val calendar = findWritableCalendar()
            ?: return createViaIntent(args, title, start, end, allDay)

        val zone = ZoneId.systemDefault()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendar.id)
            put(CalendarContract.Events.TITLE, title)
            args.str("description")?.let { put(CalendarContract.Events.DESCRIPTION, it) }
            args.str("location")?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
            if (allDay) {
                // Wydarzenia caldniowe musza byc zapisane jako polnoc UTC.
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                put(CalendarContract.Events.DTSTART, utcMidnight(start))
                put(CalendarContract.Events.DTEND, utcMidnight(end.toLocalDate().atStartOfDay()))
            } else {
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.DTSTART, start.atZone(zone).toInstant().toEpochMilli())
                put(CalendarContract.Events.DTEND, end.atZone(zone).toInstant().toEpochMilli())
            }
        }

        val uri = try {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        } catch (e: SecurityException) {
            return toolNeedsPermission(Manifest.permission.WRITE_CALENDAR, "zapis w kalendarzu")
        } catch (e: Exception) {
            return toolError("Nie udalo sie zapisac wydarzenia: ${e.message}")
        } ?: return createViaIntent(args, title, start, end, allDay)

        val eventId = ContentUris.parseId(uri)
        val reminderMinutes = args.int("reminder_minutes") ?: 15
        if (reminderMinutes > 0 && !allDay) addReminder(eventId, reminderMinutes)

        return toolOk(
            "Dodano wydarzenie do kalendarza \"${calendar.name}\".",
            "event_id" to eventId.toString(),
            "title" to title,
            "calendar" to calendar.name,
            "start" to start.toString(),
            "end" to end.toString(),
            "all_day" to allDay,
        )
    }

    fun listEvents(args: JsonObject): JsonObject {
        if (!context.hasPermission(Manifest.permission.READ_CALENDAR)) {
            return toolNeedsPermission(Manifest.permission.READ_CALENDAR, "odczyt kalendarza")
        }

        val daysAhead = (args.int("days_ahead") ?: 1).coerceIn(0, 365)
        val daysBack = (args.int("days_back") ?: 0).coerceIn(0, 365)
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        val from = now.toLocalDate().minusDays(daysBack.toLong()).atStartOfDay()
            .atZone(zone).toInstant().toEpochMilli()
        val to = now.toLocalDate().plusDays(daysAhead.toLong()).atTime(23, 59, 59)
            .atZone(zone).toInstant().toEpochMilli()

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, from)
        ContentUris.appendId(builder, to)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )

        val events = mutableListOf<Map<String, Any?>>()
        try {
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext() && events.size < 60) {
                    events += mapOf(
                        "event_id" to cursor.getLong(0).toString(),
                        "title" to (cursor.getString(1) ?: "(bez tytulu)"),
                        "start" to TimeParsing.format(cursor.getLong(2)),
                        "end" to TimeParsing.format(cursor.getLong(3)),
                        "all_day" to (cursor.getInt(4) == 1),
                        "location" to cursor.getString(5),
                        "calendar" to cursor.getString(6),
                    )
                }
            }
        } catch (e: SecurityException) {
            return toolNeedsPermission(Manifest.permission.READ_CALENDAR, "odczyt kalendarza")
        } catch (e: Exception) {
            return toolError("Nie udalo sie odczytac kalendarza: ${e.message}")
        }

        return toolResult(
            "status" to "ok",
            "range_from" to TimeParsing.format(from),
            "range_to" to TimeParsing.format(to),
            "count" to events.size,
            "events" to events,
        )
    }

    fun deleteEvent(args: JsonObject): JsonObject {
        if (!context.hasPermission(Manifest.permission.WRITE_CALENDAR)) {
            return toolNeedsPermission(Manifest.permission.WRITE_CALENDAR, "zapis w kalendarzu")
        }
        val id = args.str("event_id")?.toLongOrNull()
            ?: return toolError("Niepoprawny identyfikator wydarzenia.")
        return try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
            val removed = context.contentResolver.delete(uri, null, null)
            if (removed > 0) toolOk("Usunieto wydarzenie z kalendarza.")
            else toolError("Nie znaleziono wydarzenia o tym identyfikatorze.")
        } catch (e: Exception) {
            toolError("Nie udalo sie usunac wydarzenia: ${e.message}")
        }
    }

    // ---------- srodki pomocnicze ----------

    private data class CalendarInfo(val id: Long, val name: String, val primary: Boolean)

    private fun findWritableCalendar(): CalendarInfo? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"

        val found = mutableListOf<CalendarInfo>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    found += CalendarInfo(
                        id = cursor.getLong(0),
                        name = cursor.getString(1) ?: "Kalendarz",
                        primary = cursor.getInt(2) == 1,
                    )
                }
            }
        }
        return found.firstOrNull { it.primary } ?: found.firstOrNull()
    }

    private fun addReminder(eventId: Long, minutes: Int) {
        runCatching {
            val values = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
        }
    }

    /** Zapasowa sciezka: brak kalendarza do zapisu - otwieramy aplikacje Kalendarz z wypelnionym formularzem. */
    private fun createViaIntent(
        args: JsonObject,
        title: String,
        start: LocalDateTime,
        end: LocalDateTime,
        allDay: Boolean,
    ): JsonObject {
        val zone = ZoneId.systemDefault()
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.atZone(zone).toInstant().toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.atZone(zone).toInstant().toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
        args.str("location")?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        args.str("description")?.let { intent.putExtra(CalendarContract.Events.DESCRIPTION, it) }

        return if (context.launchActivity(intent)) {
            toolOk("Nie znalazlem kalendarza z prawem zapisu, wiec otworzylem aplikacje Kalendarz z gotowym formularzem - wystarczy zapisac.")
        } else {
            toolError("Na urzadzeniu nie ma kalendarza, w ktorym mozna zapisac wydarzenie.")
        }
    }

    private fun utcMidnight(dateTime: LocalDateTime): Long =
        dateTime.toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
}
