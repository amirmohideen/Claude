package com.amirmohideen.foldfx.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amirmohideen.foldfx.FoldRuntime
import com.amirmohideen.foldfx.core.FoldEvent
import com.amirmohideen.foldfx.core.HingeEnd
import com.amirmohideen.foldfx.core.audio.DoorSoundScene
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** The phone flat is the door shut; folding swings it open. */
private fun doorOpening(hingeAngle: Float) = ((180f - hingeAngle - FLAT_SLACK) * 1.03f).coerceIn(0f, MAX_OPEN)

// Hinge sensors may never quite read 180 when flat.
private const val FLAT_SLACK = 3f
private const val MAX_OPEN = 100f

@Composable
fun DoorScreen(runtime: FoldRuntime, onBack: () -> Unit) {
    val scene = remember { DoorSoundScene(runtime::state) }
    val pendingSlam = remember { AtomicReference<Float?>(null) }
    var angle by remember { mutableFloatStateOf(runtime.state().angle) }
    var speed by remember { mutableFloatStateOf(0f) }
    var shake by remember { mutableFloatStateOf(0f) }
    var seconds by remember { mutableFloatStateOf(0f) }
    var moved by remember { mutableStateOf(false) }
    var sliderAngle by remember { mutableFloatStateOf(180f) }
    val hasSensor = runtime.hinge.sensor != null

    val view = LocalView.current
    DisposableEffect(scene) {
        view.keepScreenOn = true
        val stopListening = runtime.addListener { event ->
            scene.onEvent(event)
            if (event is FoldEvent.Slam && event.end == HingeEnd.FLAT) {
                val strength = DoorSoundScene.slamIntensity(event.speed)
                runtime.haptics.thud(strength)
                pendingSlam.set(strength)
            }
        }
        runtime.setScene(scene)
        onDispose {
            runtime.setScene(null)
            stopListening()
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { frame ->
                val dt = if (last == 0L) 0f else ((frame - last) / 1e9f).coerceAtMost(0.1f)
                last = frame
                val state = runtime.state()
                // Ease toward the reading so a sensor that reports in coarse steps still moves smoothly.
                angle += (state.angle - angle) * (1f - exp(-dt / 0.035f))
                speed = state.speed
                if (state.speed > 5f) moved = true
                pendingSlam.getAndSet(null)?.let { shake = maxOf(shake, 0.4f + 0.6f * it) }
                shake *= exp(-dt / 0.12f)
                seconds += dt
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Canvas(Modifier.fillMaxSize()) {
            val jolt = shake * 10f * density * sin(seconds * 70f)
            translate(left = jolt) { drawDoorScene(doorOpening(angle), seconds) }
        }
        TextButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(8.dp)) {
            Text("←  Back", color = Parchment)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!moved) {
                Text(
                    if (hasSensor) {
                        "Fold slowly for a long, loud creak.\nFold fast for a quick squeak.\nOpen it flat fast to slam the door."
                    } else {
                        "No hinge sensor here, so drag the slider to swing the door."
                    },
                    color = Parchment,
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            if (!hasSensor) {
                Slider(
                    value = sliderAngle,
                    onValueChange = {
                        sliderAngle = it
                        runtime.feed(runtime.now(), it)
                    },
                    valueRange = 0f..180f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("${angle.roundToInt()}°  ·  ${speed.roundToInt()}°/s", color = Muted, fontSize = 12.sp)
        }
    }
}
