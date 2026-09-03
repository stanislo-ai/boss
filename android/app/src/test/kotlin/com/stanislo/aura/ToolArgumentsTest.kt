package com.stanislo.aura

import com.stanislo.aura.tools.TimeParsing
import com.stanislo.aura.tools.bool
import com.stanislo.aura.tools.int
import com.stanislo.aura.tools.str
import com.stanislo.aura.tools.strList
import com.stanislo.aura.tools.toolOk
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Model potrafi zwrocic argumenty w roznych typach (liczba jako tekst,
 * lista jako string). Te testy pilnuja, ze i tak je zrozumiemy.
 */
class ToolArgumentsTest {

    private fun args(raw: String) = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun `argumenty tekstowe i liczbowe sa odporne na typ`() {
        val a = args("""{"title":"Dentysta","hour":"9","minute":30,"all_day":"true","empty":"  "}""")

        assertEquals("Dentysta", a.str("title"))
        assertEquals(9, a.int("hour"))
        assertEquals(30, a.int("minute"))
        assertEquals(true, a.bool("all_day"))
        assertNull("puste ciagi traktujemy jak brak wartosci", a.str("empty"))
        assertNull(a.str("nie_ma_takiego"))
    }

    @Test
    fun `null z JSON-a nie udaje wartosci`() {
        val a = args("""{"location":null,"days":null}""")
        assertNull(a.str("location"))
        assertEquals(emptyList<String>(), a.strList("days"))
    }

    @Test
    fun `lista dni dziala jako tablica i jako tekst`() {
        assertEquals(
            listOf("MONDAY", "FRIDAY"),
            args("""{"days":["MONDAY","FRIDAY"]}""").strList("days"),
        )
        assertEquals(
            listOf("MONDAY", "FRIDAY"),
            args("""{"days":"MONDAY, FRIDAY"}""").strList("days"),
        )
    }

    @Test
    fun `parser dat akceptuje warianty zwracane przez model`() {
        val expected = LocalDateTime.of(2026, 9, 4, 15, 30)

        assertEquals(expected, TimeParsing.parse("2026-09-04T15:30:00"))
        assertEquals(expected, TimeParsing.parse("2026-09-04T15:30"))
        assertEquals(expected, TimeParsing.parse("2026-09-04 15:30"))
        assertEquals(expected, TimeParsing.parse("04.09.2026 15:30"))
        assertEquals(
            LocalDateTime.of(2026, 9, 4, 0, 0),
            TimeParsing.parse("2026-09-04"),
        )
        assertNull(TimeParsing.parse("jutro po poludniu"))
        assertNull(TimeParsing.parse(null))
        assertNull(TimeParsing.parse("   "))
    }

    @Test
    fun `wynik narzedzia ma ustalony ksztalt`() {
        val result = toolOk("Dodano wydarzenie.", "event_id" to "42", "all_day" to false)

        assertEquals("ok", result["status"]!!.jsonPrimitive.content)
        assertEquals("Dodano wydarzenie.", result["message"]!!.jsonPrimitive.content)
        assertEquals("42", result["event_id"]!!.jsonPrimitive.content)
        assertEquals("false", result["all_day"]!!.jsonPrimitive.content)
    }
}
