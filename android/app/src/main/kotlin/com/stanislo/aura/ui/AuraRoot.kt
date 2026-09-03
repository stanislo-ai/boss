package com.stanislo.aura.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stanislo.aura.tools.hasPermission
import com.stanislo.aura.tools.launchActivity

private enum class Screen { ASSISTANT, SETTINGS, LIBRARY }

@Composable
fun AuraRoot(
    viewModel: AssistantViewModel,
    startListeningOnLaunch: Boolean,
    prefilledText: String?,
    /** Zmienia sie przy kazdym nowym intencie, dzieki czemu efekt startowy odpala sie ponownie. */
    launchNonce: Long = 0L,
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()

    var screen by rememberSaveable { mutableStateOf(Screen.ASSISTANT) }
    var input by rememberSaveable { mutableStateOf(prefilledText.orEmpty()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.startListening() }

    // Narzedzie zglosilo brak uprawnienia - pytamy o nie od razu.
    LaunchedEffect(Unit) {
        viewModel.permissionRequests.collect { permission ->
            permissionLauncher.launch(arrayOf(permission))
        }
    }

    LaunchedEffect(launchNonce, startListeningOnLaunch) {
        if (startListeningOnLaunch && context.hasPermission(Manifest.permission.RECORD_AUDIO)) {
            viewModel.startListening()
        }
    }

    LaunchedEffect(launchNonce, prefilledText) {
        if (!prefilledText.isNullOrBlank()) input = prefilledText
    }

    val onMicClick = {
        if (context.hasPermission(Manifest.permission.RECORD_AUDIO)) {
            viewModel.toggleListening()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler(enabled = screen != Screen.ASSISTANT) { screen = Screen.ASSISTANT }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val forward = targetState != Screen.ASSISTANT
            if (forward) {
                (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(260)))
                    .togetherWith(fadeOut(tween(200)))
            } else {
                (fadeIn(tween(260)))
                    .togetherWith(slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(220)))
            }
        },
        label = "screens",
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) { current ->
        when (current) {
            Screen.ASSISTANT -> AssistantScreen(
                messages = messages,
                state = state,
                inputText = input,
                onInputChange = { input = it },
                onSend = {
                    viewModel.sendText(input)
                    input = ""
                },
                onMicClick = onMicClick,
                onSuggestion = { viewModel.sendText(it) },
                onOpenSettings = { screen = Screen.SETTINGS },
                onOpenLibrary = { screen = Screen.LIBRARY },
                onClearChat = viewModel::clearConversation,
                onDismissBanner = viewModel::dismissBanner,
            )

            Screen.SETTINGS -> SettingsScreen(
                settings = settings,
                speechAvailable = remember { viewModel.speech.isAvailable },
                onBack = { screen = Screen.ASSISTANT },
                onApiKeyChange = viewModel::updateApiKey,
                onModelChange = viewModel::updateModel,
                onLanguageChange = viewModel::updateLanguage,
                onSpeakRepliesChange = viewModel::updateSpeakReplies,
                onHandsFreeChange = viewModel::updateHandsFree,
                onAutoSendChange = viewModel::updateAutoSend,
                onOfflineSpeechChange = viewModel::updateOfflineSpeech,
                onDeepThinkingChange = viewModel::updateDeepThinking,
                onTemperatureChange = viewModel::updateTemperature,
                onUserNameChange = viewModel::updateUserName,
                onPersonaChange = viewModel::updatePersona,
                onGrantPermissions = { permissionLauncher.launch(REQUESTED_PERMISSIONS) },
                onOpenAssistantSettings = {
                    context.launchActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                },
                onOpenExactAlarmSettings = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}"),
                        )
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:${context.packageName}"))
                    }
                    context.launchActivity(intent)
                },
                onOpenApiKeyPage = {
                    context.launchActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")),
                    )
                },
            )

            Screen.LIBRARY -> LibraryScreen(
                notes = notes,
                memories = memories,
                reminders = reminders,
                onBack = { screen = Screen.ASSISTANT },
                onDeleteNote = viewModel::deleteNote,
                onDeleteMemory = viewModel::deleteMemory,
            )
        }
    }
}

private val REQUESTED_PERMISSIONS: Array<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    add(Manifest.permission.READ_CALENDAR)
    add(Manifest.permission.WRITE_CALENDAR)
    add(Manifest.permission.READ_CONTACTS)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()
