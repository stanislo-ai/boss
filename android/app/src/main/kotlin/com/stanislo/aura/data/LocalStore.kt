package com.stanislo.aura.data

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Prosty, odporny na bledy magazyn listy obiektow w pliku JSON.
 * Swiadomie bez bazy danych - dane sa male, a brak generatorow kodu
 * bardzo upraszcza kompilacje projektu.
 */
class JsonListStore<T>(
    private val file: File,
    private val elementSerializer: KSerializer<T>,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(elementSerializer)
    private val mutex = Mutex()

    private val _items = MutableStateFlow<List<T>>(emptyList())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    private val ready = CompletableDeferred<Unit>()

    init {
        scope.launch { load() }
    }

    /** Zawiesza wykonanie do momentu wczytania danych z dysku. */
    suspend fun awaitReady() = ready.await()

    private suspend fun load() = withContext(Dispatchers.IO) {
        runCatching {
            if (file.exists()) {
                _items.value = json.decodeFromString(listSerializer, file.readText())
            }
        }.onFailure { Log.w(TAG, "Nie udalo sie wczytac ${file.name}", it) }
        ready.complete(Unit)
    }

    /** Modyfikuje liste i zapisuje ja na dysku. Zwraca nowa liste. */
    suspend fun update(transform: (List<T>) -> List<T>): List<T> = mutex.withLock {
        val next = transform(_items.value)
        _items.value = next
        withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText(json.encodeToString(listSerializer, next))
                if (!tmp.renameTo(file)) {
                    file.writeText(tmp.readText())
                    tmp.delete()
                }
            }.onFailure { Log.w(TAG, "Nie udalo sie zapisac ${file.name}", it) }
        }
        next
    }

    private companion object {
        const val TAG = "JsonListStore"
    }
}

/**
 * Wszystkie lokalne dane aplikacji w jednym miejscu.
 * Uzywaj wylacznie przez [LocalStore.get] - jedna instancja na proces gwarantuje,
 * ze zapis z odbiornika alarmu nie nadpisze zmian zrobionych w interfejsie.
 */
class LocalStore private constructor(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dir = File(context.applicationContext.filesDir, "aura").apply { mkdirs() }

    val notes = JsonListStore(File(dir, "notes.json"), Note.serializer(), scope)
    val memories = JsonListStore(File(dir, "memories.json"), MemoryItem.serializer(), scope)
    val reminders = JsonListStore(File(dir, "reminders.json"), Reminder.serializer(), scope)
    val history = JsonListStore(File(dir, "history.json"), ChatMessage.serializer(), scope)

    suspend fun appendMessage(message: ChatMessage) {
        history.update { (it + message).takeLast(MAX_HISTORY) }
    }

    suspend fun replaceMessage(message: ChatMessage) {
        history.update { list -> list.map { if (it.id == message.id) message else it } }
    }

    suspend fun clearHistory() {
        history.update { emptyList() }
    }

    companion object {
        private const val MAX_HISTORY = 300

        @Volatile
        private var instance: LocalStore? = null

        fun get(context: Context): LocalStore =
            instance ?: synchronized(this) {
                instance ?: LocalStore(context.applicationContext).also { instance = it }
            }
    }
}
