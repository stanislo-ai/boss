package com.stanislo.aura.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.stanislo.aura.ai.ModelOption
import com.stanislo.aura.data.AuraSettings
import com.stanislo.aura.ui.components.pressable
import com.stanislo.aura.ui.theme.AuraAccent
import com.stanislo.aura.ui.theme.AuraIcons
import com.stanislo.aura.ui.theme.AuraInk
import com.stanislo.aura.ui.theme.AuraInkFaint
import com.stanislo.aura.ui.theme.AuraInkSoft
import com.stanislo.aura.ui.theme.AuraOutline
import com.stanislo.aura.ui.theme.AuraSurface

@Composable
fun SettingsScreen(
    settings: AuraSettings,
    models: List<ModelOption>,
    modelsLoading: Boolean,
    speechAvailable: Boolean,
    onBack: () -> Unit,
    onRefreshModels: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onSpeakRepliesChange: (Boolean) -> Unit,
    onHandsFreeChange: (Boolean) -> Unit,
    onAutoSendChange: (Boolean) -> Unit,
    onOfflineSpeechChange: (Boolean) -> Unit,
    onDeepThinkingChange: (Boolean) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onUserNameChange: (String) -> Unit,
    onPersonaChange: (String) -> Unit,
    onGrantPermissions: () -> Unit,
    onOpenAssistantSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenApiKeyPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .imePadding(),
    ) {
        ScreenHeader(title = "Ustawienia", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding(),
        ) {
            SectionTitle("Klucz Gemini API")
            var revealed by remember { mutableStateOf(false) }
            InlineTextField(
                value = settings.apiKey,
                onValueChange = onApiKeyChange,
                placeholder = "Wklej klucz z Google AI Studio",
                visualTransformation = if (revealed) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation('•')
                },
            )
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTextAction(
                    icon = if (revealed) AuraIcons.EyeOff else AuraIcons.Eye,
                    text = if (revealed) "Ukryj" else "Pokaż",
                ) { revealed = !revealed }
                IconTextAction(icon = AuraIcons.Sparkle, text = "Pobierz klucz") { onOpenApiKeyPage() }
            }
            Hint(
                if (settings.isConfigured) {
                    "Klucz jest zapisany tylko na tym telefonie i nie trafia do kopii zapasowej."
                } else {
                    "Bez klucza asystent nie odpowie. Klucz w planie bezpłatnym pobierzesz w Google AI Studio."
                },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { SectionTitle("Model") }
                AnimatedVisibility(visible = modelsLoading, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = "odświeżam...",
                        style = MaterialTheme.typography.labelSmall,
                        color = AuraInkFaint,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                IconAction(AuraIcons.Refresh, "Odśwież listę modeli", onRefreshModels)
            }
            models.forEach { option ->
                ChoiceRow(
                    title = option.label + if (option.recommended) "  ·  zalecany" else "",
                    subtitle = option.description,
                    selected = settings.model == option.id,
                    onClick = { onModelChange(option.id) },
                )
            }
            if (models.none { it.id == settings.model }) {
                Hint(
                    "Zapisany model \"${settings.model}\" nie ma go na powyższej liście. " +
                        "Jeśli przestanie działać, Aura sama przełączy się na dostępny.",
                )
            }

            SectionTitle("Język rozmowy")
            AuraSettings.LANGUAGES.forEach { (tag, name) ->
                ChoiceRow(
                    title = name,
                    subtitle = tag,
                    selected = settings.language == tag,
                    onClick = { onLanguageChange(tag) },
                )
            }

            SectionTitle("Głos")
            ToggleRow(
                title = "Czytaj odpowiedzi na głos",
                subtitle = "Synteza mowy po każdej odpowiedzi",
                checked = settings.speakReplies,
                onCheckedChange = onSpeakRepliesChange,
            )
            ToggleRow(
                title = "Tryb rozmowy",
                subtitle = "Po przeczytaniu odpowiedzi mikrofon włącza się sam",
                checked = settings.handsFree,
                onCheckedChange = onHandsFreeChange,
            )
            ToggleRow(
                title = "Wysyłaj od razu po dyktowaniu",
                subtitle = "Wyłącz, jeśli wolisz poprawiać tekst przed wysłaniem",
                checked = settings.autoSendVoice,
                onCheckedChange = onAutoSendChange,
            )
            ToggleRow(
                title = "Rozpoznawanie mowy offline",
                subtitle = "Szybsze i prywatniejsze, wymaga pobranego pakietu językowego",
                checked = settings.preferOfflineSpeech,
                onCheckedChange = onOfflineSpeechChange,
            )
            if (!speechAvailable) {
                Hint("Na tym urządzeniu nie wykryto modułu rozpoznawania mowy — działa tylko wpisywanie tekstu.")
            }

            SectionTitle("Zachowanie modelu")
            ToggleRow(
                title = "Tryb głębokiego myślenia",
                subtitle = "Dokładniejsze odpowiedzi kosztem szybkości i większego zużycia limitu",
                checked = settings.deepThinking,
                onCheckedChange = onDeepThinkingChange,
            )
            Text(
                text = "Kreatywność: ${String.format(java.util.Locale.US, "%.1f", settings.temperature)}",
                style = MaterialTheme.typography.bodyMedium,
                color = AuraInk,
                modifier = Modifier.padding(top = 14.dp),
            )
            Slider(
                value = settings.temperature,
                onValueChange = onTemperatureChange,
                valueRange = 0f..1.5f,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = AuraInk,
                    activeTrackColor = AuraInk,
                    inactiveTrackColor = AuraOutline,
                ),
            )

            SectionTitle("Personalizacja")
            InlineTextField(
                value = settings.userName,
                onValueChange = onUserNameChange,
                placeholder = "Jak mam się do Ciebie zwracać?",
            )
            Spacer(Modifier.height(10.dp))
            InlineTextField(
                value = settings.persona,
                onValueChange = onPersonaChange,
                placeholder = "Dodatkowe instrukcje, np. mów do mnie na Ty i bądź zwięzły",
                minLines = 3,
            )

            SectionTitle("Uprawnienia i integracje")
            ActionRow(
                title = "Przyznaj uprawnienia",
                subtitle = "Mikrofon, kalendarz, kontakty, lokalizacja, powiadomienia",
                onClick = onGrantPermissions,
            )
            ActionRow(
                title = "Ustaw Aurę jako domyślnego asystenta",
                subtitle = "Uruchamianie gestem lub przyciskiem zasilania",
                onClick = onOpenAssistantSettings,
            )
            ActionRow(
                title = "Zezwól na dokładne alarmy",
                subtitle = "Potrzebne, by przypomnienia działały co do minuty",
                onClick = onOpenExactAlarmSettings,
            )

            SectionTitle("O aplikacji")
            Hint(
                "Aura łączy się bezpośrednio z Gemini API Google. Rozmowy, notatki i pamięć " +
                    "zapisywane są wyłącznie w pamięci telefonu. Pogoda pochodzi z Open-Meteo.",
            )
            Spacer(Modifier.height(36.dp))
        }
    }
}

// ---------- Wspoldzielone elementy ekranow ----------

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 18.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .pressable(scaleDown = 0.86f, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AuraIcons.ChevronLeft,
                contentDescription = "Wróć",
                tint = AuraInk,
                modifier = Modifier.size(21.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = AuraInk,
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp),
        )
        action?.invoke()
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = AuraInkSoft,
        modifier = Modifier.padding(top = 30.dp, bottom = 12.dp),
    )
}

@Composable
fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = AuraInkSoft,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun IconTextAction(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .pressable(scaleDown = 0.93f, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = AuraAccent, modifier = Modifier.size(15.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = AuraAccent)
    }
}

@Composable
fun InlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, AuraOutline, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = AuraInkSoft.copy(alpha = 0.7f),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyMedium)
                .copy(color = AuraInk),
            cursorBrush = SolidColor(AuraAccent),
            minLines = minLines,
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .pressable(scaleDown = 0.985f) { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = AuraInk)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AuraInkSoft)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AuraInk,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = AuraOutline,
                uncheckedBorderColor = AuraOutline,
            ),
        )
    }
}

@Composable
fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) AuraSurface else Color.Transparent)
            .pressable(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = AuraInk)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AuraInkSoft)
        }
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.6f),
            exit = fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.6f),
        ) {
            Icon(
                imageVector = AuraIcons.Check,
                contentDescription = null,
                tint = AuraInk,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .pressable(scaleDown = 0.98f, onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = AuraInk)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AuraInkSoft)
    }
}
