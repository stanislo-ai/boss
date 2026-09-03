package com.stanislo.aura.tools

import android.content.Context
import android.util.Log
import com.stanislo.aura.data.LocalStore
import com.stanislo.aura.system.ReminderScheduler
import kotlinx.serialization.json.JsonObject

/**
 * Jedyne miejsce, w ktorym nazwa funkcji zwrocona przez model zamienia sie
 * w konkretne dzialanie na telefonie.
 */
class ToolRouter(context: Context, store: LocalStore) {

    private val appContext = context.applicationContext
    private val calendar = CalendarTools(appContext)
    private val time = TimeTools(appContext, store, ReminderScheduler(appContext))
    private val personal = PersonalTools(store)
    private val comms = CommsTools(appContext)
    private val apps = AppTools(appContext)
    private val device = DeviceTools(appContext)
    private val weather = WeatherTools(appContext)

    suspend fun execute(name: String, args: JsonObject): JsonObject = try {
        when (name) {
            // Kalendarz
            "create_calendar_event" -> calendar.createEvent(args)
            "list_calendar_events" -> calendar.listEvents(args)
            "delete_calendar_event" -> calendar.deleteEvent(args)

            // Czas
            "set_alarm" -> time.setAlarm(args)
            "set_timer" -> time.setTimer(args)
            "create_reminder" -> time.createReminder(args)
            "list_reminders" -> time.listReminders()
            "cancel_reminder" -> time.cancelReminder(args)
            "get_datetime" -> time.getDateTime()

            // Notatki i pamiec
            "create_note" -> personal.createNote(args)
            "list_notes" -> personal.listNotes(args)
            "delete_note" -> personal.deleteNote(args)
            "remember" -> personal.remember(args)
            "recall_memory" -> personal.recall()
            "forget" -> personal.forget(args)

            // Komunikacja
            "find_contact" -> comms.findContact(args)
            "send_sms" -> comms.sendSms(args)
            "call_number" -> comms.callNumber(args)
            "compose_email" -> comms.composeEmail(args)

            // Aplikacje, internet, mapy
            "open_app" -> apps.openApp(args)
            "list_apps" -> apps.listApps(args)
            "web_search" -> apps.webSearch(args)
            "open_url" -> apps.openUrl(args)
            "navigate_to" -> apps.navigateTo(args)
            "show_on_map" -> apps.showOnMap(args)
            "play_music" -> apps.playMusic(args)
            "share_text" -> apps.shareText(args)
            "copy_to_clipboard" -> apps.copyToClipboard(args)
            "read_clipboard" -> apps.readClipboard()

            // Urzadzenie
            "get_device_status" -> device.status()
            "set_flashlight" -> device.setFlashlight(args)
            "set_volume" -> device.setVolume(args)
            "set_ringer_mode" -> device.setRingerMode(args)
            "media_control" -> device.mediaControl(args)
            "open_system_settings" -> device.openSettings(args)

            // Pogoda i lokalizacja
            "get_weather" -> weather.getWeather(args)
            "get_location" -> weather.getLocation()

            else -> toolError("Nieznane narzedzie: $name")
        }
    } catch (e: Exception) {
        Log.w(TAG, "Blad narzedzia $name", e)
        toolError("Narzedzie $name zakonczylo sie bledem: ${e.message ?: e::class.simpleName}")
    }

    /** Czytelna dla czlowieka nazwa narzedzia - pokazywana jako "chip" pod odpowiedzia. */
    fun displayName(name: String): String = DISPLAY_NAMES[name] ?: name.replace('_', ' ')

    private companion object {
        const val TAG = "ToolRouter"

        val DISPLAY_NAMES = mapOf(
            "create_calendar_event" to "Kalendarz: nowe wydarzenie",
            "list_calendar_events" to "Kalendarz: podglad",
            "delete_calendar_event" to "Kalendarz: usuniecie",
            "set_alarm" to "Budzik",
            "set_timer" to "Minutnik",
            "create_reminder" to "Przypomnienie",
            "list_reminders" to "Przypomnienia",
            "cancel_reminder" to "Anulowanie przypomnienia",
            "get_datetime" to "Data i godzina",
            "create_note" to "Notatka",
            "list_notes" to "Notatki",
            "delete_note" to "Usuniecie notatki",
            "remember" to "Zapamietane",
            "recall_memory" to "Pamiec",
            "forget" to "Zapomniane",
            "find_contact" to "Kontakt",
            "send_sms" to "SMS",
            "call_number" to "Polaczenie",
            "compose_email" to "E-mail",
            "open_app" to "Uruchomienie aplikacji",
            "list_apps" to "Lista aplikacji",
            "web_search" to "Wyszukiwarka",
            "open_url" to "Otwarcie linku",
            "navigate_to" to "Nawigacja",
            "show_on_map" to "Mapa",
            "play_music" to "Muzyka",
            "share_text" to "Udostepnianie",
            "copy_to_clipboard" to "Schowek",
            "read_clipboard" to "Odczyt schowka",
            "get_device_status" to "Stan telefonu",
            "set_flashlight" to "Latarka",
            "set_volume" to "Glosnosc",
            "set_ringer_mode" to "Tryb dzwieku",
            "media_control" to "Odtwarzacz",
            "open_system_settings" to "Ustawienia systemu",
            "get_weather" to "Pogoda",
            "get_location" to "Lokalizacja",
        )
    }
}
