package com.stanislo.aura.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.stanislo.aura.data.AuraSettings
import com.stanislo.aura.ui.theme.AuraAccent
import com.stanislo.aura.ui.theme.AuraInk
import com.stanislo.aura.ui.theme.AuraInkSoft
import com.stanislo.aura.ui.theme.AuraOutline
import com.stanislo.aura.ui.theme.AuraSurface

@Composable
fun SettingsScreen(
    settings: AuraSettings,
    speechAvailable: Boolean,
    onBack: () -> Unit,
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
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                TextAction(if (revealed) "Ukryj" else "Pokaz") { revealed = !revealed }
                TextAction("Pobierz klucz") { onOpenApiKeyPage() }
            }
            Hint(
                if (settings.isConfigured) {
                    "Klucz jest zapisany tylko na tym telefonie i nie trafia do kopii zapasowej."
                } else {
                    "Bez klucza asystent nie odpowie. Klucz w planie bezplatnym pobierzesz w Google AI Studio."
                },
            )

            SectionTitle("Model")
            AuraSettings.AVAILABLE_MODELS.forEach { (id, description) ->
                ChoiceRow(
                    title = id,
                    subtitle = description,
                    selected = settings.model == id,
                    onClick = { onModelChange(id) },
                )
            }

            SectionTitle("Jezyk rozmowy")
            AuraSettings.LANGUAGES.forEach { (tag, name) ->
                ChoiceRow(
                    title = name,
                    subtitle = tag,
                    selected = settings.language == tag,
                    onClick = { onLanguageChange(tag) },
                )
            }

            SectionTitle("Glos")
            ToggleRow(
                title = "Czytaj odpowiedzi na glos",
                subtitle = "Synteza mowy po kazdej odpowiedzi",
                checked = settings.speakReplies,
                onCheckedChange = onSpeakRepliesChange,
            )
            ToggleRow(
                title = "Tryb rozmowy",
                subtitle = "Po przeczytaniu odpowiedzi mikrofon wlacza sie sam",
                checked = settings.handsFree,
                onCheckedChange = onHandsFreeChange,
            )
            ToggleRow(
                title = "Wysylaj od razu po dyktowaniu",
                subtitle = "Wylacz, jesli wolisz poprawiac tekst przed wyslaniem",
                checked = settings.autoSendVoice,
                onCheckedChange = onAutoSendChange,
            )
            ToggleRow(
                title = "Rozpoznawanie mowy offline",
                subtitle = "Szybsze i prywatniejsze, wymaga pobranego pakietu jezykowego",
                checked = settings.preferOfflineSpeech,
                onCheckedChange = onOfflineSpeechChange,
            )
            if (!speechAvailable) {
                Hint("Na tym urzadzeniu nie wykryto modulu rozpoznawania mowy - dziala tylko wpisywanie tekstu.")
            }

            SectionTitle("Zachowanie modelu")
            ToggleRow(
                title = "Tryb glebokiego myslenia",
                subtitle = "Dokladniejsze odpowiedzi kosztem szybkosci i wiekszego zuzycia limitu",
                checked = settings.deepThinking,
                onCheckedChange = onDeepThinkingChange,
            )
            Text(
                text = "Kreatywnosc: ${String.format(java.util.Locale.US, "%.1f", settings.temperature)}",
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
                placeholder = "Jak mam sie do Ciebie zwracac?",
            )
            Spacer(Modifier.height(10.dp))
            InlineTextField(
                value = settings.persona,
                onValueChange = onPersonaChange,
                placeholder = "Dodatkowe instrukcje, np. mow do mnie na Ty i badz zwiezly",
                minLines = 3,
            )

            SectionTitle("Uprawnienia i integracje")
            ActionRow(
                title = "Przyznaj uprawnienia",
                subtitle = "Mikrofon, kalendarz, kontakty, lokalizacja, powiadomienia",
                onClick = onGrantPermissions,
            )
            ActionRow(
                title = "Ustaw Aure jako domyslnego asystenta",
                subtitle = "Uruchamianie gestem lub przyciskiem zasilania",
                onClick = onOpenAssistantSettings,
            )
            ActionRow(
                title = "Zezwol na dokladne alarmy",
                subtitle = "Potrzebne, by przypomnienia dzialaly co do minuty",
                onClick = onOpenExactAlarmSettings,
            )

            SectionTitle("O aplikacji")
            Hint(
                "Aura laczy sie bezposrednio z Gemini API Google. Rozmowy, notatki i pamiec " +
                    "zapisywane sa wylacznie w pamieci telefonu. Pogoda pochodzi z Open-Meteo.",
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
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Wroc", tint = AuraInk)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
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
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = AuraAccent,
        modifier = Modifier.clickable(onClick = onClick),
    )
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
            .clickable { onCheckedChange(!checked) }
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
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = AuraInk)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AuraInkSoft)
        }
        AnimatedVisibility(visible = selected, enter = fadeIn(), exit = fadeOut()) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = AuraInk)
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
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = AuraInk)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AuraInkSoft)
    }
}
