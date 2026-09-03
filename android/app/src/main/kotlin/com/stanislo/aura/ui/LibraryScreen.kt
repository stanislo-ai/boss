package com.stanislo.aura.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanislo.aura.data.MemoryItem
import com.stanislo.aura.data.Note
import com.stanislo.aura.data.Reminder
import com.stanislo.aura.tools.TimeParsing
import com.stanislo.aura.ui.theme.AuraInk
import com.stanislo.aura.ui.theme.AuraInkSoft
import com.stanislo.aura.ui.theme.AuraSurface

private enum class LibraryTab(val label: String) {
    NOTES("Notatki"),
    MEMORY("Pamiec"),
    REMINDERS("Przypomnienia"),
}

@Composable
fun LibraryScreen(
    notes: List<Note>,
    memories: List<MemoryItem>,
    reminders: List<Reminder>,
    onBack: () -> Unit,
    onDeleteNote: (String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(LibraryTab.NOTES) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = "Twoje dane", onBack = onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryTab.entries.forEach { entry ->
                val selected = entry == tab
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else AuraInkSoft,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) AuraInk else AuraSurface)
                        .clickable { tab = entry }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }

        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "libraryTab",
            modifier = Modifier.weight(1f),
        ) { current ->
            when (current) {
                LibraryTab.NOTES -> ItemList(
                    empty = notes.isEmpty(),
                    emptyText = "Powiedz \"zapisz notatke...\", a pojawi sie tutaj.",
                ) {
                    items(notes, key = { it.id }) { note ->
                        DataCard(
                            title = note.title,
                            body = note.content,
                            meta = TimeParsing.format(note.createdAt),
                            onDelete = { onDeleteNote(note.id) },
                        )
                    }
                }

                LibraryTab.MEMORY -> ItemList(
                    empty = memories.isEmpty(),
                    emptyText = "Powiedz \"zapamietaj, ze...\", a Aura bedzie o tym pamietac.",
                ) {
                    items(memories, key = { it.id }) { memory ->
                        DataCard(
                            title = memory.key,
                            body = memory.value,
                            meta = TimeParsing.format(memory.createdAt),
                            onDelete = { onDeleteMemory(memory.id) },
                        )
                    }
                }

                LibraryTab.REMINDERS -> {
                    val active = reminders.filter { !it.done }.sortedBy { it.triggerAt }
                    ItemList(
                        empty = active.isEmpty(),
                        emptyText = "Brak zaplanowanych przypomnien.",
                    ) {
                        items(active, key = { it.id }) { reminder ->
                            DataCard(
                                title = reminder.text,
                                body = null,
                                meta = TimeParsing.format(reminder.triggerAt),
                                onDelete = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemList(
    empty: Boolean,
    emptyText: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    if (empty) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = AuraInkSoft,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 22.dp,
                end = 22.dp,
                top = 10.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun DataCard(title: String, body: String?, meta: String, onDelete: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AuraSurface)
            .padding(start = 18.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = AuraInk)
            if (!body.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = AuraInk)
            }
            Spacer(Modifier.height(6.dp))
            Text(meta, style = MaterialTheme.typography.labelSmall, color = AuraInkSoft)
        }
        if (onDelete != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Usun", tint = AuraInkSoft)
            }
        }
    }
}
