package com.stanislo.aura.tools

import android.Manifest
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.getSystemService
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Pogoda z bezplatnego Open-Meteo (bez klucza API) oraz lokalizacja urzadzenia.
 */
class WeatherTools(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun getWeather(args: JsonObject): JsonObject {
        val days = (args.int("days") ?: 3).coerceIn(1, 7)
        val requestedPlace = args.str("location")

        val point = if (requestedPlace != null) {
            geocode(requestedPlace) ?: return toolError("Nie znalazlem miejscowosci \"$requestedPlace\".")
        } else {
            if (!context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) &&
                !context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            ) {
                return toolNeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION, "dostep do lokalizacji")
            }
            val location = currentLocation()
                ?: return toolError(
                    "Nie mam biezacej lokalizacji. Poproś uzytkownika o podanie miasta " +
                        "albo o wlaczenie lokalizacji.",
                )
            GeoPoint(location.latitude, location.longitude, describeLocation(location) ?: "Twoja lokalizacja")
        }

        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${point.latitude}&longitude=${point.longitude}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
            "&timezone=auto&forecast_days=$days"

        val body = fetchJson(url) ?: return toolError("Serwis pogodowy nie odpowiedzial. Sprobuj ponownie za chwile.")

        val current = body["current"]?.jsonObject
        val daily = body["daily"]?.jsonObject

        val forecast = mutableListOf<Map<String, Any?>>()
        if (daily != null) {
            val dates = daily["time"]?.jsonArray.orEmpty()
            for (index in dates.indices) {
                forecast += mapOf(
                    "date" to dates.text(index),
                    "summary" to weatherCodeText(daily["weather_code"]?.jsonArray.orEmpty().number(index)?.toInt()),
                    "temp_max_c" to daily["temperature_2m_max"]?.jsonArray.orEmpty().number(index),
                    "temp_min_c" to daily["temperature_2m_min"]?.jsonArray.orEmpty().number(index),
                    "precipitation_probability_percent" to
                        daily["precipitation_probability_max"]?.jsonArray.orEmpty().number(index),
                    "sunrise" to daily["sunrise"]?.jsonArray.orEmpty().text(index),
                    "sunset" to daily["sunset"]?.jsonArray.orEmpty().text(index),
                )
            }
        }

        return toolResult(
            "status" to "ok",
            "location" to point.name,
            "current" to mapOf(
                "summary" to weatherCodeText(current?.get("weather_code")?.numberOrNull()?.toInt()),
                "temperature_c" to current?.get("temperature_2m")?.numberOrNull(),
                "feels_like_c" to current?.get("apparent_temperature")?.numberOrNull(),
                "humidity_percent" to current?.get("relative_humidity_2m")?.numberOrNull(),
                "precipitation_mm" to current?.get("precipitation")?.numberOrNull(),
                "wind_kmh" to current?.get("wind_speed_10m")?.numberOrNull(),
            ),
            "forecast" to forecast,
            "source" to "Open-Meteo",
        )
    }

    suspend fun getLocation(): JsonObject {
        if (!context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) &&
            !context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            return toolNeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION, "dostep do lokalizacji")
        }
        val location = currentLocation()
            ?: return toolError("Nie udalo sie ustalic lokalizacji. Sprawdz, czy lokalizacja jest wlaczona.")
        return toolResult(
            "status" to "ok",
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "accuracy_m" to location.accuracy,
            "address" to describeLocation(location),
        )
    }

    // ---------- srodki pomocnicze ----------

    private data class GeoPoint(val latitude: Double, val longitude: Double, val name: String)

    private suspend fun geocode(place: String): GeoPoint? {
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=${java.net.URLEncoder.encode(place, "UTF-8")}&count=1&language=pl&format=json"
        val body = fetchJson(url) ?: return null
        val first = body["results"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val latitude = first["latitude"]?.numberOrNull() ?: return null
        val longitude = first["longitude"]?.numberOrNull() ?: return null
        val name = listOfNotNull(
            (first["name"] as? JsonPrimitive)?.content,
            (first["admin1"] as? JsonPrimitive)?.content,
            (first["country"] as? JsonPrimitive)?.content,
        ).distinct().joinToString(", ")
        return GeoPoint(latitude, longitude, name.ifEmpty { place })
    }

    private suspend fun fetchJson(url: String): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val text = response.body?.string() ?: return@use null
                json.parseToJsonElement(text).jsonObject
            }
        }.getOrNull()
    }

    @Suppress("MissingPermission")
    private suspend fun currentLocation(): Location? {
        if (!context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) &&
            !context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            return null
        }
        val manager = context.getSystemService<LocationManager>() ?: return null

        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }

        val cached = providers
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        // Swieza lokalizacja z ostatnich 10 minut wystarczy.
        if (cached != null && System.currentTimeMillis() - cached.time < 10 * 60 * 1000L) return cached

        val fresh = withTimeoutOrNull(12_000L) { requestFreshLocation(manager, providers) }
        return fresh ?: cached
    }

    @Suppress("MissingPermission")
    private suspend fun requestFreshLocation(
        manager: LocationManager,
        providers: List<String>,
    ): Location? = suspendCancellableCoroutine { continuation ->
        val provider = providers.firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val signal = CancellationSignal()
        val executor = Executors.newSingleThreadExecutor()
        continuation.invokeOnCancellation {
            runCatching { signal.cancel() }
            executor.shutdown()
        }
        runCatching {
            manager.getCurrentLocation(provider, signal, executor) { location ->
                if (continuation.isActive) continuation.resume(location)
                executor.shutdown()
            }
        }.onFailure {
            if (continuation.isActive) continuation.resume(null)
            executor.shutdown()
        }
    }

    private suspend fun describeLocation(location: Location): String? = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            val addresses: List<Address> = Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                .orEmpty()
            addresses.firstOrNull()?.let { address ->
                listOfNotNull(address.locality ?: address.subAdminArea, address.countryName)
                    .joinToString(", ")
                    .ifEmpty { null }
            }
        }.getOrNull()
    }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

    private fun JsonArray.text(index: Int): String? =
        (getOrNull(index) as? JsonPrimitive)?.content

    private fun JsonArray.number(index: Int): Double? =
        (getOrNull(index) as? JsonPrimitive)?.content?.toDoubleOrNull()

    private fun kotlinx.serialization.json.JsonElement.numberOrNull(): Double? =
        (this as? JsonPrimitive)?.content?.toDoubleOrNull()

    /** Kody WMO uzywane przez Open-Meteo. */
    private fun weatherCodeText(code: Int?): String = when (code) {
        0 -> "bezchmurnie"
        1 -> "przewaznie slonecznie"
        2 -> "czesciowe zachmurzenie"
        3 -> "pochmurno"
        45, 48 -> "mgla"
        51, 53, 55 -> "mzawka"
        56, 57 -> "marznaca mzawka"
        61 -> "slaby deszcz"
        63 -> "deszcz"
        65 -> "silny deszcz"
        66, 67 -> "marznacy deszcz"
        71 -> "slaby snieg"
        73 -> "snieg"
        75 -> "intensywny snieg"
        77 -> "krupa sniezna"
        80, 81, 82 -> "przelotne opady"
        85, 86 -> "przelotny snieg"
        95 -> "burza"
        96, 99 -> "burza z gradem"
        else -> "brak danych"
    }
}
