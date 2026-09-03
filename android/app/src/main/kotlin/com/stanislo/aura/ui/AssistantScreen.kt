package com.stanislo.aura.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanislo.aura.data.ChatMessage
import com.stanislo.aura.data.ChatRole
import com.stanislo.aura.ui.components.AmbientParticles
import com.stanislo.aura.ui.components.InputBar
import com.stanislo.aura.ui.components.MessageBubble
import com.stanislo.aura.ui.components.ThinkingDots
import com.stanislo.aura.ui.components.VoiceOrb
import com.stanislo.aura.ui.components.pressable
import com.stanislo.aura.ui.theme.AuraIcons
import com.stanislo.aura.ui.theme.AuraInk
import com.stanislo.aura.ui.theme.AuraInkFaint
import com.stanislo.aura.ui.theme.AuraInkSoft
import com.stanislo.aura.ui.theme.AuraOutline
import com.stanislo.aura.ui.theme.AuraSurface

private val SUGGESTIONS = listOf(
    "Dodaj spotkanie z Anną jutro o 15",
    "Jaka będzie pogoda w weekend?",
    "Przypomnij mi za godzinę o praniu",
    "Co mam dzisiaj w kalendarzu?",
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
    val lastAssistantId = remember(messages) {
        messages.lastOrNull { it.chatRole == ChatRole.ASSISTANT }?.id
    }

    LaunchedEffect(messages.size, state.phase) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.White)) {
        // Ledwo widoczny pyl w tle - dodaje bieli glebi.
        AmbientParticles(
            modifier = Modifier.fillMaxSize(),
            intensity = if (messages.isEmpty()) 1f else 0.55f,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
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
                enter = fadeIn(tween(220)) + expandVertically(tween(260)),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(200)),
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
                        contentPadding = PaddingValues(
                            start = 24.dp,
                            end = 24.dp,
                            top = 10.dp,
                            bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            // Kolumna daje AnimatedVisibility potrzebny ColumnScope,
                            // dzieki czemu kazda nowa wiadomosc plynnie wjezdza od dolu.
                            Column {
                                AnimatedVisibility(
                                    visibleState = remember(message.id) {
                                        MutableTransitionState(false).apply { targetState = true }
                                    },
                                    enter = fadeIn(tween(340)) +
                                        slideInVertically(tween(380)) { it / 4 },
                                    exit = fadeOut(tween(160)),
                                ) {
                                    MessageBubble(
                                        message = message,
                                        animateReveal = message.id == lastAssistantId,
                                    )
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
            .padding(start = 26.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Aura",
            style = MaterialTheme.typography.headlineSmall,
            color = AuraInk,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(
            visible = hasMessages,
            enter = fadeIn(tween(240)) + androidx.compose.animation.scaleIn(tween(240), 0.8f),
            exit = fadeOut(tween(160)) + androidx.compose.animation.scaleOut(tween(160), 0.8f),
        ) {
            IconAction(AuraIcons.Trash, "Wyczyść rozmowę", onClearChat)
        }
        IconAction(AuraIcons.Document, "Notatki i pamięć", onOpenLibrary)
        IconAction(AuraIcons.Sliders, "Ustawienia", onOpenSettings)
    }
}

@Composable
internal fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50))
            .pressable(scaleDown = 0.86f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = AuraInkSoft,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun EmptyState(state: AssistantUiState, onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VoiceOrb(
            phase = state.phase,
            amplitude = state.amplitude,
            modifier = Modifier.size(250.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Czym się dziś zajmiemy?",
            style = MaterialTheme.typography.headlineMedium,
            color = AuraInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Dotknij mikrofonu i powiedz, czego potrzebujesz.",
            style = MaterialTheme.typography.bodyMedium,
            color = AuraInkSoft,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        SuggestionColumn(onSuggestion)
    }
}

@Composable
private fun SuggestionColumn(onSuggestion: (String) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SUGGESTIONS.take(3).forEachIndexed { index, suggestion ->
            // Podpowiedzi wchodza kaskadowo, jedna po drugiej.
            var visible by remember(suggestion) { mutableStateOf(false) }
            LaunchedEffect(suggestion) { visible = true }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(460, delayMillis = 140 * index)) +
                    slideInVertically(tween(520, delayMillis = 140 * index)) { it / 2 },
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuraInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, AuraOutline, RoundedCornerShape(50))
                        .pressable { onSuggestion(suggestion) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
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
        enter = fadeIn(tween(240)) + expandVertically(tween(280)),
        exit = fadeOut(tween(180)) + shrinkVertically(tween(220)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VoiceOrb(
                phase = state.phase,
                amplitude = state.amplitude,
                particleCount = 18,
                modifier = Modifier.size(44.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                val label = state.activeTool ?: when (state.phase) {
                    AssistantPhase.LISTENING -> "Słucham..."
                    AssistantPhase.THINKING -> "Myślę..."
                    AssistantPhase.SPEAKING -> "Mówię..."
                    AssistantPhase.IDLE -> "Gotowe"
                }
                // Etykieta zmienia sie z przenikaniem, nie skokowo.
                AnimatedContent(
                    targetState = label,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
                    label = "statusLabel",
                ) { current ->
                    Text(
                        text = current,
                        style = MaterialTheme.typography.labelSmall,
                        color = AuraInkFaint,
                    )
                }
                if (state.partialTranscript.isNotBlank()) {
                    Text(
                        text = state.partialTranscript,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AuraInk,
                        maxLines = 2,
                    )
                }
            }
            AnimatedVisibility(
                visible = state.phase == AssistantPhase.THINKING,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(160)),
            ) {
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
            .clip(RoundedCornerShape(20.dp))
            .background(AuraSurface)
            .pressable(scaleDown = 0.98f, onClick = onDismiss)
            .padding(horizontal = 20.dp, vertical = 15.dp),
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
