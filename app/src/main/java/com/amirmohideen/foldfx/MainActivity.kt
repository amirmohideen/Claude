package com.amirmohideen.foldfx

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.amirmohideen.foldfx.ui.DoorScreen
import com.amirmohideen.foldfx.ui.FoldFxTheme
import com.amirmohideen.foldfx.ui.HomeScreen
import com.amirmohideen.foldfx.ui.SensorLabScreen

class MainActivity : ComponentActivity() {
    private val runtime get() = (application as FoldFxApplication).runtime

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Volume keys control the sound effects rather than the ringer.
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent {
            FoldFxTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                val home = { screen = Screen.HOME }
                if (screen != Screen.HOME) BackHandler(onBack = home)
                when (screen) {
                    Screen.HOME -> HomeScreen(runtime, onOpen = { screen = it })
                    Screen.DOOR -> DoorScreen(runtime, onBack = home)
                    Screen.LAB -> SensorLabScreen(runtime, this, onBack = home)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runtime.onForeground()
    }

    override fun onStop() {
        super.onStop()
        runtime.onBackground()
    }
}
