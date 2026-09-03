package com.stanislo.aura.tools

import com.stanislo.aura.data.LocalStore
import com.stanislo.aura.data.MemoryItem
import com.stanislo.aura.data.Note
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.json.JsonObject

/** Notatki oraz trwala pamiec asystenta. */
class PersonalTools(private val store: LocalStore) {

    suspend fun createNote(args: JsonObject): JsonObject {
        val title = args.str("title") ?: return toolError("Brak tytulu notatki.")
        val content = args.str("content") ?: return toolError("Brak tresci notatki.")
        val note = Note(
            id = UUID.randomUUID().toString(),
            title = title,
            content = content,
            createdAt = System.currentTimeMillis(),
        )
        store.notes.update { listOf(note) + it }
        return toolOk("Zapisano notatke \"$title\".", "note_id" to note.id)
    }

    suspend fun listNotes(args: JsonObject): JsonObject {
        store.notes.awaitReady()
        val query = args.str("query")?.lowercase(Locale.getDefault())
        val notes = store.notes.items.value
            .filter {
                query == null ||
                    it.title.lowercase(Locale.getDefault()).contains(query) ||
                    it.content.lowercase(Locale.getDefault()).contains(query)
            }
            .take(50)
            .map {
                mapOf(
                    "note_id" to it.id,
                    "title" to it.title,
                    "content" to it.content,
                    "created" to TimeParsing.format(it.createdAt),
                )
            }
        return toolResult("status" to "ok", "count" to notes.size, "notes" to notes)
    }

    suspend fun deleteNote(args: JsonObject): JsonObject {
        store.notes.awaitReady()
        val id = args.str("note_id") ?: return toolError("Brak identyfikatora notatki.")
        val existing = store.notes.items.value.firstOrNull { it.id == id }
            ?: return toolError("Nie znaleziono notatki o tym identyfikatorze.")
        store.notes.update { list -> list.filterNot { it.id == id } }
        return toolOk("Usunieto notatke \"${existing.title}\".")
    }

    suspend fun remember(args: JsonObject): JsonObject {
        val key = args.str("key") ?: return toolError("Brak nazwy zapamietywanego faktu.")
        val value = args.str("value") ?: return toolError("Brak tresci faktu.")
        store.memories.awaitReady()
        val item = MemoryItem(
            id = UUID.randomUUID().toString(),
            key = key,
            value = value,
            createdAt = System.currentTimeMillis(),
        )
        store.memories.update { list ->
            list.filterNot { it.key.equals(key, ignoreCase = true) } + item
        }
        return toolOk("Zapamietalem: $key = $value")
    }

    suspend fun recall(): JsonObject {
        store.memories.awaitReady()
        val memories = store.memories.items.value.map {
            mapOf("key" to it.key, "value" to it.value)
        }
        return toolResult("status" to "ok", "count" to memories.size, "memories" to memories)
    }

    suspend fun forget(args: JsonObject): JsonObject {
        store.memories.awaitReady()
        val key = args.str("key") ?: return toolError("Brak nazwy faktu do usuniecia.")
        val existing = store.memories.items.value.filter { it.key.equals(key, ignoreCase = true) }
        if (existing.isEmpty()) return toolError("Nie mam zapamietanego faktu o nazwie \"$key\".")
        store.memories.update { list -> list.filterNot { it.key.equals(key, ignoreCase = true) } }
        return toolOk("Usunalem z pamieci: $key")
    }
}
