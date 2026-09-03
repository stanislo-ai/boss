package com.stanislo.aura

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanislo.aura.ui.AssistantViewModel
import com.stanislo.aura.ui.AuraRoot
import com.stanislo.aura.ui.theme.AuraTheme

class MainActivity : ComponentActivity() {

    private var launchState by mutableStateOf(LaunchState())

    override fun onCreate(savedInstanceState: Bundle?) {
        // Interfejs jest zawsze jasny, wiec wymuszamy ciemne ikony pasków systemowych.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        launchState = readIntent(intent)

        setContent {
            AuraTheme {
                val viewModel: AssistantViewModel = viewModel()
                val current = launchState
                AuraRoot(
                    viewModel = viewModel,
                    startListeningOnLaunch = current.startListening,
                    prefilledText = current.prefilledText,
                    launchNonce = current.nonce,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchState = readIntent(intent)
    }

    private fun readIntent(intent: Intent?): LaunchState {
        if (intent == null) return LaunchState()
        val shared = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        val voiceLaunch = intent.action == Intent.ACTION_ASSIST ||
            intent.action == Intent.ACTION_VOICE_COMMAND ||
            intent.getBooleanExtra(EXTRA_START_LISTENING, false)

        return LaunchState(
            // Nowy obiekt przy kazdym wywolaniu, zeby LaunchedEffect zareagowal ponownie.
            startListening = voiceLaunch,
            prefilledText = shared,
            nonce = System.currentTimeMillis(),
        )
    }

    private data class LaunchState(
        val startListening: Boolean = false,
        val prefilledText: String? = null,
        val nonce: Long = 0L,
    )

    companion object {
        const val EXTRA_START_LISTENING = "com.stanislo.aura.START_LISTENING"
    }
}
