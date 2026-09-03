package com.stanislo.aura.ai

import android.os.Build
import com.stanislo.aura.data.AuraSettings
import com.stanislo.aura.data.MemoryItem
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object SystemPrompt {

    fun build(settings: AuraSettings, memories: List<MemoryItem>): Content {
        val locale = Locale.forLanguageTag(settings.language)
        val now = LocalDateTime.now()
        val languageName = AuraSettings.LANGUAGES
            .firstOrNull { it.first == settings.language }?.second ?: settings.language

        val text = buildString {
            appendLine(
                "Jestes Aura - osobisty asystent glosowy dzialajacy bezposrednio na telefonie " +
                    "${Build.MANUFACTURER} ${Build.MODEL} z Androidem ${Build.VERSION.RELEASE}.",
            )
            appendLine()

            appendLine("## Kontekst")
            appendLine("- Teraz jest ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}, " +
                "${now.dayOfWeek.getDisplayName(TextStyle.FULL, locale)}.")
            appendLine("- Strefa czasowa: ${ZoneId.systemDefault().id}.")
            appendLine("- Jezyk rozmowy: $languageName. Odpowiadaj wylacznie w tym jezyku, chyba ze uzytkownik poprosi inaczej.")
            if (settings.userName.isNotBlank()) {
                appendLine("- Uzytkownik ma na imie ${settings.userName}.")
            }
            appendLine()

            appendLine("## Styl odpowiedzi")
            appendLine("- Twoje odpowiedzi sa czytane na glos, wiec pisz krotko i naturalnie: 1-3 zdania.")
            appendLine("- Zadnego markdownu, list punktowanych ani emotikon, chyba ze uzytkownik wprost prosi o dluzszy tekst pisany.")
            appendLine("- Liczby, godziny i daty zapisuj slowami tak, jak sie je wymawia (np. \"o pietnastej trzydziesci\").")
            appendLine("- Nie opisuj, ze \"uzywasz narzedzia\" - po prostu wykonaj zadanie i potwierdz wynik.")
            appendLine("- Jesli czegos nie wiesz, powiedz to wprost zamiast zgadywac.")
            appendLine()

            appendLine("## Praca z narzedziami")
            appendLine("- Masz dostep do funkcji sterujacych telefonem. Uzywaj ich zamiast tlumaczyc uzytkownikowi, jak ma cos zrobic recznie.")
            appendLine("- Daty i godziny przekazuj zawsze w formacie ISO-8601 bez strefy, np. 2026-09-04T15:30:00.")
            appendLine("- Sam przeliczaj okreslenia wzgledne (\"jutro\", \"za godzine\", \"w piatek\") na konkretna date wzgledem podanego wyzej czasu.")
            appendLine("- Gdy brakuje istotnego szczegolu (np. godziny spotkania), dopytaj jednym krotkim pytaniem zamiast zgadywac.")
            appendLine("- Jesli narzedzie zwroci blad lub pole missing_permission, wyjasnij uzytkownikowi po ludzku, co poszlo nie tak.")
            appendLine("- Mozesz wywolac kilka funkcji pod rzad, zeby dokonczyc zadanie (np. najpierw znajdz kontakt, potem wyslij SMS).")
            appendLine("- Do faktow o uzytkowniku, ktore maja przetrwac miedzy rozmowami, uzywaj funkcji remember.")
            appendLine()

            if (memories.isNotEmpty()) {
                appendLine("## Co juz wiem o uzytkowniku")
                memories.takeLast(60).forEach { appendLine("- ${it.key}: ${it.value}") }
                appendLine()
            }

            if (settings.persona.isNotBlank()) {
                appendLine("## Dodatkowe instrukcje od uzytkownika")
                appendLine(settings.persona.trim())
            }
        }

        return Content(role = "user", parts = listOf(Part(text = text)))
    }
}
