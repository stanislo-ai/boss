package com.stopfat.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Barcode lookup in the free Open Food Facts database (millions of products, incl. Polish ones). */
class OpenFoodFacts(private val http: OkHttpClient = defaultClient) {

    sealed class Lookup {
        data class Found(val food: Food, val brand: String, val servingGrams: Double?) : Lookup()
        data object NotFound : Lookup()
        data class Error(val message: String) : Lookup()
    }

    suspend fun lookup(barcode: String): Lookup = withContext(Dispatchers.IO) {
        val code = barcode.filter { it.isDigit() }
        if (code.length < 6) return@withContext Lookup.Error("Nieprawidłowy kod kreskowy")
        val request = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,product_name_pl,brands,nutriments,serving_quantity,quantity")
            .header("User-Agent", "StopFat/1.0 (Android)")
            .build()
        try {
            http.newCall(request).execute().use { resp ->
                if (resp.code == 404) return@withContext Lookup.NotFound
                if (!resp.isSuccessful) return@withContext Lookup.Error("Serwer bazy produktów odpowiedział błędem ${resp.code}")
                val body = resp.body?.string() ?: return@withContext Lookup.NotFound
                parse(code, body)
            }
        } catch (e: IOException) {
            Lookup.Error("Brak połączenia z internetem")
        }
    }

    companion object {
        private val defaultClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }

        fun parse(code: String, body: String): Lookup {
            val root = try { JSONObject(body) } catch (e: Exception) { return Lookup.Error("Nieprawidłowa odpowiedź serwera") }
            if (root.optInt("status", 0) != 1) return Lookup.NotFound
            val p = root.optJSONObject("product") ?: return Lookup.NotFound
            val n = p.optJSONObject("nutriments") ?: JSONObject()
            val name = p.optString("product_name_pl").ifBlank { p.optString("product_name") }.trim()
            var kcal = n.optDouble("energy-kcal_100g", Double.NaN)
            if (kcal.isNaN()) {
                val kj = n.optDouble("energy_100g", Double.NaN)
                kcal = if (kj.isNaN()) Double.NaN else kj / 4.184
            }
            if (name.isEmpty() && kcal.isNaN()) return Lookup.NotFound
            fun nut(key: String) = n.optDouble(key, 0.0).let { if (it.isNaN() || it < 0) 0.0 else it }
            val serving = p.optDouble("serving_quantity", Double.NaN).takeIf { !it.isNaN() && it > 0 }
            val food = Food(
                id = "off_$code",
                name = name.ifEmpty { "Produkt $code" },
                kcal = if (kcal.isNaN()) 0.0 else kcal,
                protein = nut("proteins_100g"),
                carbs = nut("carbohydrates_100g"),
                fat = nut("fat_100g"),
                category = "Zeskanowane",
                unitGrams = serving ?: 100.0,
                unitName = if (serving != null) "porcja" else "100 g",
                barcode = code,
            )
            return Lookup.Found(food, p.optString("brands").split(",").firstOrNull()?.trim().orEmpty(), serving)
        }
    }
}
