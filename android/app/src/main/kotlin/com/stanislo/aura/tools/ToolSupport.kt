package com.stanislo.aura.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray

// ---------- Odczyt argumentow od modelu (model bywa niekonsekwentny w typach) ----------

fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNullSafe()?.takeIf { it.isNotBlank() }

fun JsonObject.int(key: String): Int? {
    val p = this[key] as? JsonPrimitive ?: return null
    return p.intOrNull ?: p.contentOrNullSafe()?.trim()?.toDoubleOrNull()?.toInt()
}

fun JsonObject.double(key: String): Double? {
    val p = this[key] as? JsonPrimitive ?: return null
    return p.contentOrNullSafe()?.trim()?.toDoubleOrNull()
}

fun JsonObject.bool(key: String): Boolean? {
    val p = this[key] as? JsonPrimitive ?: return null
    return p.booleanOrNull ?: when (p.contentOrNullSafe()?.lowercase()?.trim()) {
        "true", "yes", "1", "tak" -> true
        "false", "no", "0", "nie" -> false
        else -> null
    }
}

fun JsonObject.strList(key: String): List<String> {
    val element = this[key] ?: return emptyList()
    return when (element) {
        is JsonArray -> element.jsonArray.mapNotNull { (it as? JsonPrimitive)?.contentOrNullSafe() }
        is JsonPrimitive -> element.contentOrNullSafe()
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        else -> emptyList()
    }
}

private fun JsonPrimitive.contentOrNullSafe(): String? = if (this is JsonNull) null else content

// ---------- Budowanie wyniku dla modelu ----------

fun toolResult(vararg pairs: Pair<String, Any?>): JsonObject =
    JsonObject(pairs.associate { (k, v) -> k to v.toJsonElement() })

fun toolOk(message: String, vararg extra: Pair<String, Any?>): JsonObject =
    JsonObject(
        buildMap {
            put("status", JsonPrimitive("ok"))
            put("message", JsonPrimitive(message))
            extra.forEach { (k, v) -> put(k, v.toJsonElement()) }
        },
    )

fun toolError(message: String, vararg extra: Pair<String, Any?>): JsonObject =
    JsonObject(
        buildMap {
            put("status", JsonPrimitive("error"))
            put("message", JsonPrimitive(message))
            extra.forEach { (k, v) -> put(k, v.toJsonElement()) }
        },
    )

/** Zwracane, gdy narzedzie potrzebuje uprawnienia, ktorego uzytkownik jeszcze nie przyznal. */
fun toolNeedsPermission(permission: String, humanName: String): JsonObject = toolError(
    "Brak uprawnienia: $humanName. Poproszono wlasnie uzytkownika o jego przyznanie - " +
        "powiedz mu krotko, ze musi je zatwierdzic i sprobowac ponownie.",
    "missing_permission" to permission,
)

@Suppress("UNCHECKED_CAST")
fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}

// ---------- Uprawnienia ----------

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

// ---------- Intencje ----------

/**
 * Startuje aktywnosc z kontekstu aplikacji. Zwraca false, gdy nic nie potrafi jej obsluzyc.
 */
fun Context.launchActivity(intent: Intent): Boolean = try {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    true
} catch (e: Exception) {
    false
}

// ---------- Czas ----------

object TimeParsing {

    private val fallbackFormats = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
    )

    /** Akceptuje rozne warianty ISO-8601 oraz kilka popularnych formatow zapasowych. */
    fun parse(value: String?): LocalDateTime? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        runCatching { return LocalDateTime.parse(raw) }
        runCatching { return OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() }
        runCatching { return Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDateTime() }
        runCatching { return LocalDate.parse(raw).atStartOfDay() }
        for (format in fallbackFormats) {
            runCatching { return LocalDateTime.parse(raw, format) }
            runCatching { return LocalDate.parse(raw, format).atStartOfDay() }
        }
        return null
    }

    fun toEpochMillis(dateTime: LocalDateTime): Long =
        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun format(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
