package com.stanislo.aura.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanislo.aura.data.ChatMessage
import com.stanislo.aura.ui.components.InputBar
import com.stanislo.aura.ui.components.MessageBubble
import com.stanislo.aura.ui.components.ThinkingDots
import com.stanislo.aura.ui.components.VoiceOrb
import com.stanislo.aura.ui.theme.AuraInk
import com.stanislo.aura.ui.theme.AuraInkSoft
import com.stanislo.aura.ui.theme.AuraOutline
import com.stanislo.aura.ui.theme.AuraSurface

private val SUGGESTIONS = listOf(
    "Dodaj spotkanie z Anna jutro o 15",
    "Jaka bedzie pogoda w weekend?",
    "Przypomnij mi za godzine o praniu",
    "Co mam dzisiaj w kalendarzu?",
    "Zapisz notatke z lista zakupow",
)

@Composable
fun AssistantScreen(
    messages: List<ChatMessage>,
    state: AssistantUiState,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onSuggestion: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onClearChat: () -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, state.phase) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .imePadding(),
    ) {
        TopBar(
            hasMessages = messages.isNotEmpty(),
            onOpenSettings = onOpenSettings,
            onOpenLibrary = onOpenLibrary,
            onClearChat = onClearChat,
        )

        AnimatedVisibility(
            visible = state.banner != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Banner(text = state.banner.orEmpty(), onDismiss = onDismissBanner)
        }

        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                EmptyState(state = state, onSuggestion = onSuggestion)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 22.dp,
                        end = 22.dp,
                        top = 8.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        // Kolumna daje AnimatedVisibility potrzebny ColumnScope,
                        // dzieki czemu kazda nowa wiadomosc plynnie wjezdza od dolu.
                        Column {
                            AnimatedVisibility(
                                visibleState = remember(message.id) {
                                    MutableTransitionState(false).apply { targetState = true }
                                },
                                enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 4 },
                                exit = fadeOut(tween(160)),
                            ) {
                                MessageBubble(message)
                            }
                        }
                    }
                }
            }
        }

        StatusStrip(state = state)

        InputBar(
            value = inputText,
            onValueChange = onInputChange,
            onSend = onSend,
            onMicClick = onMicClick,
            phase = state.phase,
            amplitude = state.amplitude,
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun TopBar(
    hasMessages: Boolean,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onClearChat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 14.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Aura",
            style = MaterialTheme.typography.titleLarge,
            color = AuraInk,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(visible = hasMessages, enter = fadeIn(), exit = fadeOut()) {
            IconAction(Icons.Rounded.DeleteSweep, "Wyczysc rozmowe", onClearChat)
        }
        IconAction(Icons.Rounded.Description, "Notatki i pamiec", onOpenLibrary)
        IconAction(Icons.Rounded.Tune, "Ustawienia", onOpenSettings)
    }
}

@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = AuraInkSoft)
    }
}

@Composable
private fun EmptyState(state: AssistantUiState, onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VoiceOrb(
            phase = state.phase,
            amplitude = state.amplitude,
            modifier = Modifier.size(230.dp),
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Czym sie dzis zajmiemy?",
            style = MaterialTheme.typography.headlineSmall,
            color = AuraInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Dotknij mikrofonu i po prostu powiedz, czego potrzebujesz.",
            style = MaterialTheme.typography.bodyMedium,
            color = AuraInkSoft,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(30.dp))
        SuggestionColumn(onSuggestion)
    }
}

@Composable
private fun SuggestionColumn(onSuggestion: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SUGGESTIONS.take(3).forEachIndexed { index, suggestion ->
            var visible by rememberSaveable(suggestion) { mutableStateOf(false) }
            LaunchedEffect(suggestion) { visible = true }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400, delayMillis = 120 * index)) +
                    slideInVertically(tween(400, delayMillis = 120 * index)) { it / 3 },
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuraInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, AuraOutline, RoundedCornerShape(50))
                        .clickable { onSuggestion(suggestion) }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusStrip(state: AssistantUiState) {
    val visible = state.phase != AssistantPhase.IDLE || state.partialTranscript.isNotBlank()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + expandVertically(tween(220)),
        exit = fadeOut(tween(180)) + shrinkVertically(tween(180)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VoiceOrb(
                phase = state.phase,
                amplitude = state.amplitude,
                modifier = Modifier.size(42.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.activeTool ?: when (state.phase) {
                        AssistantPhase.LISTENING -> "Slucham..."
                        AssistantPhase.THINKING -> "Mysle..."
                        AssistantPhase.SPEAKING -> "Mowie..."
                        AssistantPhase.IDLE -> "Gotowe"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AuraInkSoft,
                )
                if (state.partialTranscript.isNotBlank()) {
                    Text(
                        text = state.partialTranscript,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AuraInk,
                        maxLines = 2,
                    )
                }
            }
            if (state.phase == AssistantPhase.THINKING) {
                ThinkingDots()
            }
        }
    }
}

@Composable
private fun Banner(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AuraSurface)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = AuraInk,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "OK",
            style = MaterialTheme.typography.labelLarge,
            color = AuraInkSoft,
        )
    }
}
