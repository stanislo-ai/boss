package com.stanislo.aura.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.getSystemService
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.JsonObject

/** Uruchamianie aplikacji, wyszukiwarka, mapy, muzyka, schowek i udostepnianie. */
class AppTools(private val context: Context) {

    private val packageManager: PackageManager get() = context.packageManager

    fun openApp(args: JsonObject): JsonObject {
        val requested = args.str("app_name") ?: return toolError("Brak nazwy aplikacji.")
        val apps = launchableApps()
        val match = bestMatch(requested, apps)
            ?: return toolError(
                "Nie znalazlem aplikacji \"$requested\". Uzyj list_apps, aby sprawdzic dokladne nazwy.",
            )

        val intent = packageManager.getLaunchIntentForPackage(match.packageName)
            ?: return toolError("Aplikacji \"${match.label}\" nie da sie uruchomic z zewnatrz.")

        return if (context.launchActivity(intent)) {
            toolOk("Uruchomilem aplikacje ${match.label}.", "package" to match.packageName)
        } else {
            toolError("Nie udalo sie uruchomic aplikacji ${match.label}.")
        }
    }

    fun listApps(args: JsonObject): JsonObject {
        val query = args.str("query")?.let { normalize(it) }
        val apps = launchableApps()
            .filter { query == null || normalize(it.label).contains(query) }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .take(120)
            .map { mapOf("name" to it.label, "package" to it.packageName) }
        return toolResult("status" to "ok", "count" to apps.size, "apps" to apps)
    }

    fun webSearch(args: JsonObject): JsonObject {
        val query = args.str("query") ?: return toolError("Brak zapytania.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
        return if (context.launchActivity(intent)) {
            toolOk("Otworzylem wyniki wyszukiwania dla \"$query\".")
        } else {
            toolError("Nie znalazlem przegladarki.")
        }
    }

    fun openUrl(args: JsonObject): JsonObject {
        val raw = args.str("url") ?: return toolError("Brak adresu URL.")
        val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        return if (context.launchActivity(intent)) {
            toolOk("Otworzylem $url.")
        } else {
            toolError("Nie znalazlem przegladarki, ktora otworzy ten adres.")
        }
    }

    fun navigateTo(args: JsonObject): JsonObject {
        val destination = args.str("destination") ?: return toolError("Brak celu nawigacji.")
        val mode = when (args.str("mode")) {
            "walking" -> "w"
            "bicycling" -> "b"
            "transit" -> "r"
            else -> "d"
        }
        val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=$mode")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (context.launchActivity(intent)) {
            return toolOk("Uruchomilem nawigacje do: $destination.")
        }
        // Zapasowo: zwykla mapa.
        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(destination)}"))
        return if (context.launchActivity(fallback)) {
            toolOk("Nie mam aplikacji nawigacji, wiec otworzylem mape z celem: $destination.")
        } else {
            toolError("Na urzadzeniu nie ma aplikacji map.")
        }
    }

    fun showOnMap(args: JsonObject): JsonObject {
        val query = args.str("query") ?: return toolError("Brak zapytania do mapy.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}"))
        return if (context.launchActivity(intent)) {
            toolOk("Pokazalem na mapie: $query.")
        } else {
            toolError("Na urzadzeniu nie ma aplikacji map.")
        }
    }

    fun playMusic(args: JsonObject): JsonObject {
        val query = args.str("query") ?: return toolError("Brak zapytania muzycznego.")
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .putExtra(android.app.SearchManager.QUERY, query)
        return if (context.launchActivity(intent)) {
            toolOk("Wlaczam muzyke: $query.")
        } else {
            toolError("Zadna aplikacja muzyczna nie obsluguje wyszukiwania glosowego. Sprobuj open_app.")
        }
    }

    fun shareText(args: JsonObject): JsonObject {
        val text = args.str("text") ?: return toolError("Brak tekstu do udostepnienia.")
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        val chooser = Intent.createChooser(send, null)
        return if (context.launchActivity(chooser)) {
            toolOk("Otworzylem okno udostepniania.")
        } else {
            toolError("Nie udalo sie otworzyc okna udostepniania.")
        }
    }

    fun copyToClipboard(args: JsonObject): JsonObject {
        val text = args.str("text") ?: return toolError("Brak tekstu do skopiowania.")
        val clipboard = context.getSystemService<ClipboardManager>()
            ?: return toolError("Schowek jest niedostepny.")
        clipboard.setPrimaryClip(ClipData.newPlainText("Aura", text))
        return toolOk("Skopiowano do schowka.")
    }

    fun readClipboard(): JsonObject {
        val clipboard = context.getSystemService<ClipboardManager>()
            ?: return toolError("Schowek jest niedostepny.")
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        return if (text.isNullOrBlank()) {
            toolError("Schowek jest pusty (albo Android zablokowal odczyt, gdy aplikacja nie jest na wierzchu).")
        } else {
            toolResult("status" to "ok", "clipboard" to text)
        }
    }

    // ---------- srodki pomocnicze ----------

    private data class AppEntry(val label: String, val packageName: String)

    private fun launchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = runCatching {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }.getOrElse { emptyList() }

        return resolved
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = runCatching { info.loadLabel(packageManager).toString() }
                    .getOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                AppEntry(label, pkg)
            }
            .distinctBy { it.packageName }
    }

    private fun bestMatch(requested: String, apps: List<AppEntry>): AppEntry? {
        val needle = normalize(requested)
        if (needle.isEmpty()) return null
        return apps.firstOrNull { normalize(it.label) == needle }
            ?: apps.firstOrNull { normalize(it.label).startsWith(needle) }
            ?: apps.firstOrNull { normalize(it.label).contains(needle) }
            ?: apps.firstOrNull { needle.contains(normalize(it.label)) }
            ?: apps.firstOrNull { normalize(it.packageName).contains(needle) }
    }

    /** Male litery bez znakow diakrytycznych i spacji - odporne porownywanie nazw. */
    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]"), "")
}
