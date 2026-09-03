package com.stanislo.aura.data

import kotlinx.serialization.Serializable

enum class ChatRole { USER, ASSISTANT, SYSTEM }

@Serializable
data class ChatMessage(
    val id: String,
    val role: String,
    val text: String,
    val timestamp: Long,
    /** Nazwy narzedzi uzytych przy tworzeniu tej odpowiedzi (do wyswietlenia jako "chipy"). */
    val tools: List<String> = emptyList(),
    val isError: Boolean = false,
) {
    val chatRole: ChatRole
        get() = when (role) {
            "user" -> ChatRole.USER
            "system" -> ChatRole.SYSTEM
            else -> ChatRole.ASSISTANT
        }
}

@Serializable
data class Note(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val pinned: Boolean = false,
)

/** Trwala pamiec asystenta - fakty o uzytkowniku wstrzykiwane do promptu systemowego. */
@Serializable
data class MemoryItem(
    val id: String,
    val key: String,
    val value: String,
    val createdAt: Long,
)

@Serializable
data class Reminder(
    val id: String,
    val text: String,
    val triggerAt: Long,
    val requestCode: Int,
    val done: Boolean = false,
)
