package com.stopfat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.stopfat.app.ui.MainViewModel
import com.stopfat.app.ui.StopFatRoot
import com.stopfat.app.ui.theme.StopFatTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(0xCC070B1F.toInt()),
        )
        super.onCreate(savedInstanceState)
        setContent {
            StopFatTheme {
                StopFatRoot(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.container.stepCounter.startLive(lifecycleScope)
    }

    override fun onStop() {
        vm.container.stepCounter.stopLive()
        super.onStop()
    }
}
