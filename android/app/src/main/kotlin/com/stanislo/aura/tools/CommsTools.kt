package com.stanislo.aura.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.serialization.json.JsonObject

/** Kontakty, SMS, polaczenia i e-mail. Akcje wysylkowe zawsze konczy uzytkownik. */
class CommsTools(private val context: Context) {

    fun findContact(args: JsonObject): JsonObject {
        if (!context.hasPermission(Manifest.permission.READ_CONTACTS)) {
            return toolNeedsPermission(Manifest.permission.READ_CONTACTS, "dostep do kontaktow")
        }
        val name = args.str("name") ?: return toolError("Brak imienia szukanej osoby.")
        val matches = lookupContacts(name)
        return if (matches.isEmpty()) {
            toolError("Nie znalazlem kontaktu pasujacego do \"$name\".")
        } else {
            toolResult("status" to "ok", "count" to matches.size, "contacts" to matches.map {
                mapOf("name" to it.name, "number" to it.number)
            })
        }
    }

    fun sendSms(args: JsonObject): JsonObject {
        val recipient = args.str("recipient") ?: return toolError("Brak odbiorcy wiadomosci.")
        val message = args.str("message") ?: return toolError("Brak tresci wiadomosci.")

        val resolved = resolveRecipient(recipient) ?: return contactNotFound(recipient)
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(resolved.number)}"))
            .putExtra("sms_body", message)

        return if (context.launchActivity(intent)) {
            toolOk(
                "Otworzylem aplikacje Wiadomosci z gotowym SMS-em do ${resolved.name}. " +
                    "Powiedz uzytkownikowi, ze wystarczy nacisnac przycisk wyslania.",
                "recipient" to resolved.name,
                "number" to resolved.number,
            )
        } else {
            toolError("Nie znalazlem aplikacji do wysylania SMS-ow.")
        }
    }

    fun callNumber(args: JsonObject): JsonObject {
        val recipient = args.str("recipient") ?: return toolError("Brak numeru lub kontaktu.")
        val resolved = resolveRecipient(recipient) ?: return contactNotFound(recipient)
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(resolved.number)}"))
        return if (context.launchActivity(intent)) {
            toolOk(
                "Otworzylem dialer z numerem do ${resolved.name}. Uzytkownik potwierdza polaczenie.",
                "recipient" to resolved.name,
                "number" to resolved.number,
            )
        } else {
            toolError("Nie znalazlem aplikacji telefonu.")
        }
    }

    fun composeEmail(args: JsonObject): JsonObject {
        val to = args.str("to") ?: return toolError("Brak adresu odbiorcy.")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        args.str("subject")?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
        args.str("body")?.let { intent.putExtra(Intent.EXTRA_TEXT, it) }

        return if (context.launchActivity(intent)) {
            toolOk("Otworzylem klienta poczty z przygotowana wiadomoscia do $to.")
        } else {
            toolError("Nie znalazlem aplikacji pocztowej.")
        }
    }

    // ---------- srodki pomocnicze ----------

    private data class ResolvedContact(val name: String, val number: String)

    private fun resolveRecipient(input: String): ResolvedContact? {
        if (looksLikePhoneNumber(input)) {
            return ResolvedContact(input, input.filter { it.isDigit() || it == '+' })
        }
        if (!context.hasPermission(Manifest.permission.READ_CONTACTS)) return null
        return lookupContacts(input).firstOrNull()
    }

    private fun contactNotFound(recipient: String): JsonObject =
        if (!context.hasPermission(Manifest.permission.READ_CONTACTS)) {
            toolNeedsPermission(Manifest.permission.READ_CONTACTS, "dostep do kontaktow")
        } else {
            toolError("Nie znalazlem kontaktu \"$recipient\". Poproś uzytkownika o numer telefonu.")
        }

    private fun looksLikePhoneNumber(value: String): Boolean {
        val cleaned = value.replace(Regex("[\\s\\-()]"), "")
        return cleaned.matches(Regex("\\+?\\d{3,15}"))
    }

    private fun lookupContacts(name: String): List<ResolvedContact> {
        val uri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
            Uri.encode(name),
        )
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val results = LinkedHashMap<String, ResolvedContact>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext() && results.size < 10) {
                    val displayName = cursor.getString(0) ?: continue
                    val number = cursor.getString(1)?.trim() ?: continue
                    val key = number.filter { it.isDigit() }
                    if (key.isNotEmpty()) results.putIfAbsent(key, ResolvedContact(displayName, number))
                }
            }
        }
        return results.values.toList()
    }
}
