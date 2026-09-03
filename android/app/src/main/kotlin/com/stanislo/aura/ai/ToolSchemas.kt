package com.stanislo.aura.ai

/**
 * Deklaracje wszystkich narzedzi, ktore model moze wywolac (Gemini Function Calling).
 * Nazwy musza byc identyczne z tymi obslugiwanymi w [com.stanislo.aura.tools.ToolRouter].
 */
object AuraTools {

    private const val ISO_HINT =
        "Data i godzina lokalna w formacie ISO-8601 bez strefy, np. 2026-09-04T15:30:00."

    val declarations: List<FunctionDeclaration> = listOf(

        // ---------- Kalendarz ----------
        FunctionDeclaration(
            name = "create_calendar_event",
            description = "Dodaje nowe wydarzenie do domyslnego kalendarza uzytkownika. " +
                "Uzywaj zawsze, gdy uzytkownik prosi o zapisanie spotkania, wizyty, terminu lub przypomnienia z konkretna data.",
            parameters = Sch.obj(
                mapOf(
                    "title" to Sch.string("Tytul wydarzenia."),
                    "start_time" to Sch.string("Poczatek wydarzenia. $ISO_HINT"),
                    "end_time" to Sch.string("Koniec wydarzenia. $ISO_HINT Jesli pominiete, przyjmowana jest godzina."),
                    "location" to Sch.string("Miejsce wydarzenia."),
                    "description" to Sch.string("Dodatkowy opis / notatka."),
                    "all_day" to Sch.boolean("true, jesli wydarzenie trwa caly dzien."),
                    "reminder_minutes" to Sch.integer("Ile minut przed wydarzeniem ma sie pojawic powiadomienie. Domyslnie 15."),
                ),
                required = listOf("title", "start_time"),
            ),
        ),
        FunctionDeclaration(
            name = "list_calendar_events",
            description = "Zwraca wydarzenia z kalendarza w podanym zakresie dni. Uzywaj do pytan typu 'co mam dzisiaj', 'jakie mam plany w tym tygodniu'.",
            parameters = Sch.obj(
                mapOf(
                    "days_ahead" to Sch.integer("Ile dni do przodu sprawdzic, liczac od teraz. Domyslnie 1 (dzisiaj)."),
                    "days_back" to Sch.integer("Ile dni wstecz uwzglednic. Domyslnie 0."),
                ),
            ),
        ),
        FunctionDeclaration(
            name = "delete_calendar_event",
            description = "Usuwa wydarzenie z kalendarza na podstawie jego identyfikatora zwroconego przez list_calendar_events.",
            parameters = Sch.obj(
                mapOf("event_id" to Sch.string("Identyfikator wydarzenia.")),
                required = listOf("event_id"),
            ),
        ),

        // ---------- Budzik, minutnik, przypomnienia ----------
        FunctionDeclaration(
            name = "set_alarm",
            description = "Ustawia budzik w systemowej aplikacji Zegar.",
            parameters = Sch.obj(
                mapOf(
                    "hour" to Sch.integer("Godzina 0-23."),
                    "minute" to Sch.integer("Minuta 0-59."),
                    "label" to Sch.string("Etykieta budzika."),
                    "days" to Sch.stringArray(
                        "Dni powtarzania: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY. Pomin dla budzika jednorazowego.",
                    ),
                ),
                required = listOf("hour", "minute"),
            ),
        ),
        FunctionDeclaration(
            name = "set_timer",
            description = "Uruchamia minutnik (odliczanie) w systemowej aplikacji Zegar.",
            parameters = Sch.obj(
                mapOf(
                    "seconds" to Sch.integer("Dlugosc odliczania w sekundach."),
                    "label" to Sch.string("Etykieta minutnika, np. 'jajka'."),
                ),
                required = listOf("seconds"),
            ),
        ),
        FunctionDeclaration(
            name = "create_reminder",
            description = "Tworzy przypomnienie w aplikacji Aura - o podanej godzinie wyswietli sie powiadomienie z tekstem. " +
                "Uzywaj dla krotkich przypomnien bez wpisu w kalendarzu.",
            parameters = Sch.obj(
                mapOf(
                    "text" to Sch.string("Tresc przypomnienia."),
                    "time" to Sch.string("Kiedy przypomniec. $ISO_HINT"),
                ),
                required = listOf("text", "time"),
            ),
        ),
        FunctionDeclaration(
            name = "list_reminders",
            description = "Zwraca liste aktywnych przypomnien utworzonych w aplikacji Aura.",
        ),
        FunctionDeclaration(
            name = "cancel_reminder",
            description = "Anuluje przypomnienie o podanym identyfikatorze.",
            parameters = Sch.obj(
                mapOf("reminder_id" to Sch.string("Identyfikator przypomnienia.")),
                required = listOf("reminder_id"),
            ),
        ),

        // ---------- Notatki ----------
        FunctionDeclaration(
            name = "create_note",
            description = "Zapisuje notatke w aplikacji Aura (listy zakupow, pomysly, dane do zapamietania na pozniej).",
            parameters = Sch.obj(
                mapOf(
                    "title" to Sch.string("Krotki tytul notatki."),
                    "content" to Sch.string("Tresc notatki."),
                ),
                required = listOf("title", "content"),
            ),
        ),
        FunctionDeclaration(
            name = "list_notes",
            description = "Zwraca zapisane notatki, opcjonalnie przefiltrowane fraza.",
            parameters = Sch.obj(
                mapOf("query" to Sch.string("Fraza do wyszukania w tytule lub tresci.")),
            ),
        ),
        FunctionDeclaration(
            name = "delete_note",
            description = "Usuwa notatke o podanym identyfikatorze.",
            parameters = Sch.obj(
                mapOf("note_id" to Sch.string("Identyfikator notatki.")),
                required = listOf("note_id"),
            ),
        ),

        // ---------- Pamiec dlugoterminowa ----------
        FunctionDeclaration(
            name = "remember",
            description = "Zapisuje trwaly fakt o uzytkowniku (imie, preferencje, adres, dieta, rozmiar butow itp.). " +
                "Te fakty sa dolaczane do kazdej przyszlej rozmowy. Uzywaj, gdy uzytkownik mowi 'zapamietaj, ze...'.",
            parameters = Sch.obj(
                mapOf(
                    "key" to Sch.string("Krotka nazwa faktu, np. 'ulubiona kawa'."),
                    "value" to Sch.string("Tresc faktu."),
                ),
                required = listOf("key", "value"),
            ),
        ),
        FunctionDeclaration(
            name = "recall_memory",
            description = "Zwraca wszystkie zapamietane fakty o uzytkowniku.",
        ),
        FunctionDeclaration(
            name = "forget",
            description = "Usuwa zapamietany fakt po nazwie klucza.",
            parameters = Sch.obj(
                mapOf("key" to Sch.string("Nazwa faktu do usuniecia.")),
                required = listOf("key"),
            ),
        ),

        // ---------- Kontakty i komunikacja ----------
        FunctionDeclaration(
            name = "find_contact",
            description = "Wyszukuje kontakt w ksiazce telefonicznej i zwraca numery telefonu.",
            parameters = Sch.obj(
                mapOf("name" to Sch.string("Imie lub nazwisko szukanej osoby.")),
                required = listOf("name"),
            ),
        ),
        FunctionDeclaration(
            name = "send_sms",
            description = "Przygotowuje wiadomosc SMS z gotowa trescia i otwiera aplikacje Wiadomosci. " +
                "Uzytkownik sam klika 'wyslij' - to celowe zabezpieczenie.",
            parameters = Sch.obj(
                mapOf(
                    "recipient" to Sch.string("Numer telefonu lub imie kontaktu."),
                    "message" to Sch.string("Tresc wiadomosci."),
                ),
                required = listOf("recipient", "message"),
            ),
        ),
        FunctionDeclaration(
            name = "call_number",
            description = "Otwiera dialer z wpisanym numerem. Uzytkownik potwierdza polaczenie przyciskiem.",
            parameters = Sch.obj(
                mapOf("recipient" to Sch.string("Numer telefonu lub imie kontaktu.")),
                required = listOf("recipient"),
            ),
        ),
        FunctionDeclaration(
            name = "compose_email",
            description = "Otwiera klienta poczty z przygotowana wiadomoscia.",
            parameters = Sch.obj(
                mapOf(
                    "to" to Sch.string("Adres e-mail odbiorcy."),
                    "subject" to Sch.string("Temat wiadomosci."),
                    "body" to Sch.string("Tresc wiadomosci."),
                ),
                required = listOf("to"),
            ),
        ),

        // ---------- Aplikacje ----------
        FunctionDeclaration(
            name = "open_app",
            description = "Uruchamia zainstalowana aplikacje po jej nazwie widocznej dla uzytkownika (np. 'Spotify', 'Ustawienia').",
            parameters = Sch.obj(
                mapOf("app_name" to Sch.string("Nazwa aplikacji.")),
                required = listOf("app_name"),
            ),
        ),
        FunctionDeclaration(
            name = "list_apps",
            description = "Zwraca liste zainstalowanych aplikacji z ekranem startowym. Uzyj, gdy nie masz pewnosci co do nazwy aplikacji.",
            parameters = Sch.obj(
                mapOf("query" to Sch.string("Opcjonalna fraza filtrujaca nazwy.")),
            ),
        ),

        // ---------- Internet i mapy ----------
        FunctionDeclaration(
            name = "web_search",
            description = "Otwiera wyszukiwarke Google z podanym zapytaniem. Uzywaj tylko, gdy uzytkownik wprost prosi o wyszukanie w internecie.",
            parameters = Sch.obj(
                mapOf("query" to Sch.string("Zapytanie do wyszukiwarki.")),
                required = listOf("query"),
            ),
        ),
        FunctionDeclaration(
            name = "open_url",
            description = "Otwiera podany adres URL w przegladarce.",
            parameters = Sch.obj(
                mapOf("url" to Sch.string("Pelny adres, np. https://example.com")),
                required = listOf("url"),
            ),
        ),
        FunctionDeclaration(
            name = "navigate_to",
            description = "Uruchamia nawigacje Google Maps do podanego miejsca.",
            parameters = Sch.obj(
                mapOf(
                    "destination" to Sch.string("Adres lub nazwa miejsca docelowego."),
                    "mode" to Sch.string("Srodek transportu.", enumValues = listOf("driving", "walking", "bicycling", "transit")),
                ),
                required = listOf("destination"),
            ),
        ),
        FunctionDeclaration(
            name = "show_on_map",
            description = "Pokazuje miejsce lub wyszukuje punkty w okolicy na mapie (np. 'apteka w poblizu').",
            parameters = Sch.obj(
                mapOf("query" to Sch.string("Nazwa miejsca lub kategoria do wyszukania.")),
                required = listOf("query"),
            ),
        ),

        // ---------- Pogoda i lokalizacja ----------
        FunctionDeclaration(
            name = "get_weather",
            description = "Zwraca aktualna pogode i prognoze. Bez podanej miejscowosci uzywa biezacej lokalizacji urzadzenia. " +
                "Dane pochodzza z bezplatnego serwisu Open-Meteo (nie wymaga klucza).",
            parameters = Sch.obj(
                mapOf(
                    "location" to Sch.string("Nazwa miejscowosci. Pomin, aby uzyc biezacej lokalizacji."),
                    "days" to Sch.integer("Liczba dni prognozy, 1-7. Domyslnie 3."),
                ),
            ),
        ),
        FunctionDeclaration(
            name = "get_location",
            description = "Zwraca biezaca lokalizacje urzadzenia wraz z adresem.",
        ),

        // ---------- Urzadzenie ----------
        FunctionDeclaration(
            name = "get_device_status",
            description = "Zwraca stan telefonu: bateria, siec, wolne miejsce, tryb dzwieku, poziomy glosnosci.",
        ),
        FunctionDeclaration(
            name = "set_flashlight",
            description = "Wlacza lub wylacza latarke.",
            parameters = Sch.obj(
                mapOf("on" to Sch.boolean("true = wlacz, false = wylacz.")),
                required = listOf("on"),
            ),
        ),
        FunctionDeclaration(
            name = "set_volume",
            description = "Ustawia glosnosc wybranego strumienia audio.",
            parameters = Sch.obj(
                mapOf(
                    "percent" to Sch.integer("Docelowa glosnosc 0-100."),
                    "stream" to Sch.string(
                        "Strumien audio.",
                        enumValues = listOf("media", "ring", "alarm", "notification", "call"),
                    ),
                ),
                required = listOf("percent"),
            ),
        ),
        FunctionDeclaration(
            name = "set_ringer_mode",
            description = "Przelacza tryb dzwieku telefonu.",
            parameters = Sch.obj(
                mapOf(
                    "mode" to Sch.string("Tryb.", enumValues = listOf("normal", "vibrate", "silent")),
                ),
                required = listOf("mode"),
            ),
        ),
        FunctionDeclaration(
            name = "media_control",
            description = "Steruje odtwarzaczem multimediow (Spotify, YouTube Music itp.).",
            parameters = Sch.obj(
                mapOf(
                    "action" to Sch.string(
                        "Akcja do wykonania.",
                        enumValues = listOf("play_pause", "next", "previous", "stop"),
                    ),
                ),
                required = listOf("action"),
            ),
        ),
        FunctionDeclaration(
            name = "play_music",
            description = "Uruchamia odtwarzanie muzyki pasujacej do zapytania w domyslnej aplikacji muzycznej.",
            parameters = Sch.obj(
                mapOf("query" to Sch.string("Wykonawca, utwor lub album.")),
                required = listOf("query"),
            ),
        ),
        FunctionDeclaration(
            name = "open_system_settings",
            description = "Otwiera konkretny ekran ustawien systemowych. Uzywaj, gdy uzytkownik chce cos wlaczyc, czego aplikacja nie moze zmienic sama (np. Wi-Fi, Bluetooth).",
            parameters = Sch.obj(
                mapOf(
                    "section" to Sch.string(
                        "Ekran ustawien.",
                        enumValues = listOf(
                            "wifi", "bluetooth", "internet", "sound", "display", "battery",
                            "apps", "location", "date", "storage", "nfc", "hotspot",
                            "accessibility", "notifications", "main",
                        ),
                    ),
                ),
                required = listOf("section"),
            ),
        ),

        // ---------- Schowek i udostepnianie ----------
        FunctionDeclaration(
            name = "copy_to_clipboard",
            description = "Kopiuje podany tekst do schowka systemowego.",
            parameters = Sch.obj(
                mapOf("text" to Sch.string("Tekst do skopiowania.")),
                required = listOf("text"),
            ),
        ),
        FunctionDeclaration(
            name = "read_clipboard",
            description = "Odczytuje aktualna zawartosc schowka.",
        ),
        FunctionDeclaration(
            name = "share_text",
            description = "Otwiera systemowe okno udostepniania z podanym tekstem.",
            parameters = Sch.obj(
                mapOf("text" to Sch.string("Tekst do udostepnienia.")),
                required = listOf("text"),
            ),
        ),

        // ---------- Czas ----------
        FunctionDeclaration(
            name = "get_datetime",
            description = "Zwraca dokladna biezaca date, godzine, dzien tygodnia, numer tygodnia i strefe czasowa urzadzenia.",
        ),
    )

    val asTool: Tool = Tool(functionDeclarations = declarations)
}
