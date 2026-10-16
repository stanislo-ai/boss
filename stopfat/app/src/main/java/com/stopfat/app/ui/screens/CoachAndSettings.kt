package com.stopfat.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stopfat.app.BuildConfig
import com.stopfat.app.data.AI_MODELS
import com.stopfat.app.data.AiProvider
import com.stopfat.app.data.GEMINI_MODELS
import com.stopfat.app.data.ActivityLevel
import com.stopfat.app.data.DietType
import com.stopfat.app.data.Sex
import com.stopfat.app.domain.Nutrition
import com.stopfat.app.ui.MainViewModel
import com.stopfat.app.ui.Nav
import com.stopfat.app.ui.Routes
import com.stopfat.app.ui.components.GlassButton
import com.stopfat.app.ui.components.GlassCard
import com.stopfat.app.ui.components.GlassIconButton
import com.stopfat.app.ui.components.GlassPill
import com.stopfat.app.ui.components.GlassTextField
import com.stopfat.app.ui.components.MarkdownText
import com.stopfat.app.ui.components.ScreenHeader
import com.stopfat.app.ui.components.SectionTitle
import com.stopfat.app.ui.theme.Glass
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- coach

@Composable
fun CoachScreen(vm: MainViewModel, nav: Nav) {
    val profile by vm.profile.collectAsState()
    val messages by vm.chat.collectAsState()
    val busy by vm.chatBusy.collectAsState()
    val error by vm.chatError.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, busy) {
        val total = messages.size + (if (busy) 1 else 0) + 2
        listState.animateScrollToItem(total - 1)
    }
    val suggestions = listOf(
        "Oceń mój dzisiejszy dzień",
        "Co zjeść na kolację, żeby zmieścić się w limicie?",
        "Jak przełamać zastój wagi?",
        "Ułóż mi jadłospis na jutro",
        "Ile białka powinienem jeść?",
        "Mam ochotę na słodkie – co zrobić?",
    )

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 18.dp),
    ) {
        ScreenHeader("Coach AI", if (profile.hasAi) "Dietetyk i trener w kieszeni" else "Wymaga klucza API") {
            if (messages.isNotEmpty()) GlassIconButton(Icons.Rounded.DeleteSweep, "Wyczyść czat") { vm.clearChat() }
        }
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth(), corner = 22.dp) {
                    Text("👋 Cześć${if (profile.name.isNotBlank()) ", ${profile.name}" else ""}!", color = Glass.Text, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Znam Twój cel, dzisiejszy dziennik, kroki i trening. Pytaj o jedzenie, treningi, motywację – odpowiem konkretnie.",
                        color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall,
                    )
                    if (!profile.hasAi) {
                        Spacer(Modifier.height(10.dp))
                        GlassButton("Dodaj klucz API", Modifier.fillMaxWidth(), icon = Icons.Rounded.Key, secondary = true) { nav.go(Routes.SETTINGS) }
                    }
                }
            }
            items(messages, key = { it.id }) { m -> ChatBubble(m.fromUser, m.text) }
            if (busy) item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    CircularProgressIndicator(Modifier.height(18.dp).widthIn(max = 18.dp), color = Glass.Teal, strokeWidth = 2.dp)
                    Spacer(Modifier.padding(4.dp))
                    Text("Coach pisze…", color = Glass.TextSecondary)
                }
            }
            item { if (error != null) ErrorBanner(error) else Spacer(Modifier.height(4.dp)) }
        }
        if (messages.isEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { s -> GlassPill(s, false) { vm.sendChat(s) } }
            }
        }
        Row(Modifier.padding(top = 6.dp, bottom = 92.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassTextField(input, { input = it }, "Napisz wiadomość", Modifier.weight(1f), singleLine = false)
            Spacer(Modifier.padding(4.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (input.isNotBlank() && !busy) Glass.accent else Brush.linearGradient(listOf(Glass.Fill, Glass.Fill))),
            ) {
                IconButton(onClick = { vm.sendChat(input); input = "" }, enabled = input.isNotBlank() && !busy) {
                    Icon(Icons.AutoMirrored.Rounded.Send, "Wyślij", tint = if (input.isNotBlank()) Color.White else Glass.TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(fromUser: Boolean, text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
        val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = if (fromUser) 22.dp else 6.dp, bottomEnd = if (fromUser) 6.dp else 22.dp)
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(
                    if (fromUser) Glass.accent
                    else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.65f))),
                )
                .border(1.dp, Color.White, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (fromUser) Text(text, color = Color.White) else MarkdownText(text)
        }
    }
}

// ---------------------------------------------------------------- settings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, nav: Nav) {
    val context = LocalContext.current
    val profile by vm.profile.collectAsState()
    val savedBaseUrl by vm.baseUrl.collectAsState()
    val scope = rememberCoroutineScope()

    var name by remember(profile) { mutableStateOf(profile.name) }
    var sex by remember(profile) { mutableStateOf(profile.sex) }
    var age by remember(profile) { mutableStateOf(profile.age.toString()) }
    var height by remember(profile) { mutableStateOf(profile.heightCm.toString()) }
    var activity by remember(profile) { mutableStateOf(profile.activity) }
    var diet by remember(profile) { mutableStateOf(profile.diet) }
    var stepGoal by remember(profile) { mutableStateOf(profile.stepGoal.toFloat()) }
    var water by remember(profile) { mutableStateOf(profile.waterGoalMl.toString()) }
    var kcalOverride by remember(profile) { mutableStateOf(if (profile.calorieGoalOverride > 0) profile.calorieGoalOverride.toString() else "") }
    var apiKey by remember(profile) { mutableStateOf(profile.apiKey) }
    var model by remember(profile) { mutableStateOf(profile.aiModel) }
    var provider by remember(profile) { mutableStateOf(profile.aiProvider) }
    var geminiKey by remember(profile) { mutableStateOf(profile.geminiKey) }
    var geminiModel by remember(profile) { mutableStateOf(profile.geminiModel) }
    var insulin by remember(profile) { mutableStateOf(profile.insulinResistance) }
    var fetchedModels by remember { mutableStateOf(emptyList<String>()) }
    var loadingModels by remember { mutableStateOf(false) }
    var reminders by remember(profile) { mutableStateOf(profile.remindersEnabled) }
    var baseUrl by remember(savedBaseUrl) { mutableStateOf(savedBaseUrl) }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    fun current() = profile.copy(
        name = name.trim(),
        sex = sex,
        age = age.toIntOrNull()?.coerceIn(12, 100) ?: profile.age,
        heightCm = height.toIntOrNull()?.coerceIn(120, 230) ?: profile.heightCm,
        activity = activity,
        diet = diet,
        stepGoal = (stepGoal.toInt() / 500) * 500,
        waterGoalMl = water.toIntOrNull()?.coerceIn(500, 6000) ?: profile.waterGoalMl,
        calorieGoalOverride = kcalOverride.toIntOrNull()?.takeIf { it in 800..6000 } ?: 0,
        apiKey = apiKey.trim(),
        aiModel = model,
        remindersEnabled = reminders,
        aiProvider = provider,
        geminiKey = geminiKey.trim(),
        geminiModel = geminiModel,
        insulinResistance = insulin,
    )

    Page {
        ScreenHeader("Ustawienia", onBack = nav::back)

        SectionTitle("🤖 Połączenie z AI")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Dostawca AI", color = Glass.TextSecondary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.forEach { p ->
                    GlassPill(if (p == AiProvider.GEMINI) "✨ Gemini (darmowe)" else "Claude", provider == p, Modifier.weight(1f)) { provider = p; testResult = null }
                }
            }
            Spacer(Modifier.height(10.dp))
            val isGemini = provider == AiProvider.GEMINI
            Text(
                if (isGemini) "Google Gemini ma darmowy limit zapytań. Klucz utworzysz w 1 minutę w Google AI Studio (konto Google) → „Get API key”."
                else "Claude (Anthropic) – bardzo dokładny, płatny wg zużycia. Klucz utworzysz na console.anthropic.com → API Keys.",
                color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.OutlinedTextField(
                value = if (isGemini) geminiKey else apiKey,
                onValueChange = { v -> if (isGemini) geminiKey = v.trim() else apiKey = v.trim(); testResult = null },
                label = { Text(if (isGemini) "Klucz Gemini API" else "Klucz Claude API") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Pokaż klucz", tint = Glass.TextSecondary)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = com.stopfat.app.ui.components.glassFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            Text("Model", color = Glass.TextSecondary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            if (isGemini) {
                val options = (GEMINI_MODELS.map { it.first } + fetchedModels + geminiModel).distinct()
                options.forEach { id ->
                    val label = GEMINI_MODELS.firstOrNull { it.first == id }?.second ?: id
                    GlassPill(label, geminiModel == id, Modifier.fillMaxWidth().padding(vertical = 3.dp)) { geminiModel = id }
                }
                Spacer(Modifier.height(6.dp))
                GlassPill(if (loadingModels) "Pobieram listę…" else "↻ Pobierz modele dostępne dla klucza", false) {
                    if (geminiKey.isNotBlank() && !loadingModels) {
                        loadingModels = true
                        scope.launch {
                            try {
                                vm.setBaseUrl(baseUrl).join()
                                fetchedModels = vm.container.geminiModels(geminiKey)
                                testResult = "✅ Dostępne modele Gemini: ${fetchedModels.size}"
                            } catch (e: Exception) {
                                testResult = "❌ ${e.message}"
                            } finally {
                                loadingModels = false
                            }
                        }
                    }
                }
            } else {
                AI_MODELS.forEach { (id, label) ->
                    GlassPill(label, model == id, Modifier.fillMaxWidth().padding(vertical = 3.dp)) { model = id }
                }
            }
            Spacer(Modifier.height(10.dp))
            GlassTextField(baseUrl, { baseUrl = it }, "Własny adres API (opcjonalnie, proxy)", placeholder = if (isGemini) "https://generativelanguage.googleapis.com" else "https://api.anthropic.com")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Testuj", Modifier.weight(1f), secondary = true, loading = testing, enabled = (if (isGemini) geminiKey else apiKey).isNotBlank()) {
                    testing = true
                    testResult = null
                    scope.launch {
                        vm.saveProfile(current()).join()
                        vm.setBaseUrl(baseUrl).join()
                        testResult = try {
                            val ai = vm.ai() ?: throw IllegalStateException("Brak klucza")
                            ai.coach(current(), "test połączenia", emptyList(), "Odpowiedz jednym krótkim zdaniem po polsku, że połączenie działa.")
                            "✅ Połączono z ${ai.providerName} – wszystko działa!"
                        } catch (e: Exception) {
                            "❌ ${e.message}"
                        } finally {
                            testing = false
                        }
                    }
                }
                GlassButton("Pobierz klucz", Modifier.weight(1f), secondary = true) {
                    val url = if (isGemini) "https://aistudio.google.com/apikey" else "https://console.anthropic.com/settings/keys"
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
            }
            testResult?.let { Text(it, color = if (it.startsWith("✅")) Glass.TealText else Glass.AmberText, modifier = Modifier.padding(top = 10.dp)) }
        }

        SectionTitle("🩺 Insulinooporność")
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mam insulinooporność / stan przedcukrzycowy", color = Glass.Text, style = MaterialTheme.typography.titleSmall)
                    Text("Dieta o niskim IG, oznaczenia IG i ładunku glikemicznego, AI dostosowane do IO.", color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Switch(insulin, { on -> insulin = on; if (on && diet == DietType.BALANCED) diet = DietType.LOW_GI }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Glass.Violet))
            }
        }

        SectionTitle("👤 Profil")
        GlassCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassTextField(name, { name = it }, "Imię")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Sex.entries.forEach { GlassPill(it.label, sex == it, Modifier.weight(1f)) { sex = it } } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(age, { age = it.filter(Char::isDigit).take(3) }, "Wiek", Modifier.weight(1f), KeyboardType.Number)
                    GlassTextField(height, { height = it.filter(Char::isDigit).take(3) }, "Wzrost", Modifier.weight(1f), KeyboardType.Number, suffix = "cm")
                }
                Text("Wagę i cel zmienisz w zakładce Postęp → Analiza.", color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall)
                Text("Aktywność", color = Glass.TextSecondary, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityLevel.entries.forEach { GlassPill(it.label, activity == it) { activity = it } }
                }
                Text("Dieta", color = Glass.TextSecondary, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DietType.entries.forEach { GlassPill(it.label, diet == it) { diet = it } }
                }
            }
        }

        SectionTitle("🎯 Cele dzienne")
        GlassCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val auto = Nutrition.calorieGoal(current().copy(calorieGoalOverride = 0))
                GlassTextField(kcalOverride, { kcalOverride = it.filter(Char::isDigit).take(4) }, "Własny limit kalorii (puste = automatycznie $auto)", keyboardType = KeyboardType.Number, suffix = "kcal")
                GlassTextField(water, { water = it.filter(Char::isDigit).take(4) }, "Cel wody", keyboardType = KeyboardType.Number, suffix = "ml")
                Text("Cel kroków: ${((stepGoal.toInt() / 500) * 500).thousands()}", color = Glass.Text)
                Slider(stepGoal, { stepGoal = it }, valueRange = 3000f..25000f, colors = SliderDefaults.colors(thumbColor = Glass.Violet, activeTrackColor = Glass.Violet, inactiveTrackColor = Glass.FillStrong))
            }
        }

        SectionTitle("🔔 Powiadomienia")
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Przypomnienia", color = Glass.Text, style = MaterialTheme.typography.titleSmall)
                    Text("Posiłki, woda i koniec postu", color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Switch(reminders, { reminders = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Glass.Violet))
            }
            if (Build.VERSION.SDK_INT >= 33) {
                Spacer(Modifier.height(8.dp))
                GlassPill("Ustawienia powiadomień systemu", false) {
                    runCatching {
                        context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        GlassButton(if (saved) "Zapisano ✓" else "Zapisz ustawienia", Modifier.fillMaxWidth()) {
            vm.saveProfile(current())
            vm.setBaseUrl(baseUrl)
            saved = true
            nav.back()
        }
        Spacer(Modifier.height(18.dp))
        Text("StopFat ${BuildConfig.VERSION_NAME} · dane przechowywane lokalnie na telefonie", color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("Baza kodów kreskowych: Open Food Facts (ODbL)", color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
