package com.amirmohideen.foldfx.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.amirmohideen.foldfx.FoldRuntime
import com.amirmohideen.foldfx.core.FoldTrace
import kotlin.math.roundToInt

private const val GRAPH_SECONDS = 6f
private const val GRAPH_SPEED_RANGE = 720f
private val AngleLine = Brass
private val SpeedLine = Color(0xFF6FB7E8)

/** Shows exactly how this phone reports its hinge, and builds a report to share. */
@Composable
fun SensorLabScreen(runtime: FoldRuntime, activity: Activity, onBack: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(runtime.now()) }
    var posture by remember { mutableStateOf("Waiting for the window manager…") }
    var report by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        var lastReport = 0L
        while (true) {
            withFrameNanos {
                now = runtime.now()
                if (now - lastReport > 250_000_000L) {
                    report = buildReport(context, runtime, posture)
                    lastReport = now
                }
            }
        }
    }
    LaunchedEffect(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
            val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            posture = fold?.let {
                "${it.state}, ${it.orientation}, occlusion ${it.occlusionType}, separating ${it.isSeparating}, bounds ${it.bounds}"
            } ?: "No fold on this screen (cover screen, or not a foldable)"
        }
    }

    val state = runtime.stateAt(now)
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("←  Back", color = Parchment) }
            Text("Sensor lab", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Brass)
            Text("Fold slowly, fold fast, slam it flat. Then share the report.", color = Muted)
            Text(
                when {
                    runtime.usingGyro ->
                        "This phone's hinge sensor only reports 0°, 90° and 180°, so FoldFX follows the fold " +
                            "with the gyroscope. Try swinging each side in turn."
                    runtime.sensors.hinge != null -> "This phone reports every hinge angle."
                    else -> "This phone has no hinge sensor."
                },
                color = Parchment,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )

            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Reading("Angle", "${"%.1f".format(state.angle)}°", AngleLine)
                Reading("Speed", "${state.velocity.roundToInt()}°/s", SpeedLine)
                Reading("Moving", state.direction.name.lowercase(), Parchment)
            }

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Panel),
            ) {
                drawTrace(runtime.trace, now, state.angle)
            }
            Text(
                "Gold: angle (0–180°).  Blue: speed (±${GRAPH_SPEED_RANGE.roundToInt()}°/s).  Last ${GRAPH_SECONDS.roundToInt()} s.",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { share(context, report) }) {
                    Text("Share report")
                }
                OutlinedButton(onClick = { copy(context, report) }) {
                    Text("Copy")
                }
                OutlinedButton(
                    onClick = {
                        runtime.hingeStats.reset()
                        runtime.gyroStats.reset()
                        runtime.field.reset()
                    },
                ) { Text("Reset") }
            }

            Text(
                report,
                color = Parchment,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun Reading(label: String, value: String, color: Color) {
    Column {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun DrawScope.drawTrace(trace: FoldTrace, now: Long, currentAngle: Float) {
    val w = size.width
    val h = size.height
    for (angle in listOf(0f, 90f, 180f)) {
        val y = h - angle / 180f * h
        drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, y), Offset(w, y), strokeWidth = density)
    }
    val start = now - (GRAPH_SECONDS * 1e9f).toLong()
    val snapshot = trace.since(start)
    if (snapshot.count == 0) return
    fun x(t: Long) = (t - start) / (GRAPH_SECONDS * 1e9f) * w
    val angles = Path()
    val speeds = Path()
    for (i in 0 until snapshot.count) {
        val px = x(snapshot.times[i])
        val angleY = h - snapshot.angles[i] / 180f * h
        val speedY = h / 2f - (snapshot.velocities[i] / GRAPH_SPEED_RANGE).coerceIn(-1f, 1f) * h / 2f
        if (i == 0) {
            angles.moveTo(px, angleY)
            speeds.moveTo(px, speedY)
        } else {
            // Readings only arrive on change, so the angle holds until the next one.
            angles.lineTo(px, h - snapshot.angles[i - 1] / 180f * h)
            angles.lineTo(px, angleY)
            speeds.lineTo(px, speedY)
        }
    }
    angles.lineTo(w, h - currentAngle / 180f * h)
    drawPath(speeds, SpeedLine.copy(alpha = 0.8f), style = Stroke(width = 1.5f * density))
    drawPath(angles, AngleLine, style = Stroke(width = 2f * density))
}

private fun buildReport(context: Context, runtime: FoldRuntime, posture: String) = buildString {
    appendLine("FoldFX sensor report")
    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}")
    appendLine(
        when {
            runtime.usingGyro -> "Following the fold with: gyroscope (hinge reports stops only)"
            runtime.sensors.hinge != null -> "Following the fold with: hinge sensor"
            else -> "Following the fold with: on-screen slider"
        },
    )
    val hinge = runtime.sensors.hinge
    if (hinge == null) {
        appendLine("Hinge sensor: none")
    } else {
        appendLine("Hinge sensor: ${describe(hinge)}")
    }
    val declared = context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
    appendLine("Declares hinge-angle feature: $declared")
    appendLine("Fold posture: $posture")
    val hingeSummary = runtime.hingeStats.summary()
    appendLine("Hinge readings: ${hingeSummary.readings}")
    appendLine("  most per second: ${hingeSummary.peakReadingsPerSecond}")
    appendLine("  gap while moving: median ${ms(hingeSummary.medianIntervalMs)}, 90th percentile ${ms(hingeSummary.p90IntervalMs)}")
    appendLine("  smallest step: ${hingeSummary.smallestStepDegrees?.let { "%.3f°".format(it) } ?: "–"}, whole degrees only: ${hingeSummary.wholeDegreesOnly}")
    appendLine("  angle range: ${deg(hingeSummary.minAngle)} to ${deg(hingeSummary.maxAngle)}")

    val gyro = runtime.sensors.gyroscope
    appendLine("Gyroscope: ${gyro?.let(::describe) ?: "none"}")
    val gyroSummary = runtime.gyroStats.summary()
    appendLine("  readings: ${gyroSummary.readings}, most per second: ${gyroSummary.peakReadingsPerSecond}, fastest fold: ${gyroSummary.fastestDegreesPerSecond.roundToInt()}°/s")
    val tracker = runtime.gyroTracker
    appendLine("  hinge axis: ${tracker.axisName}, direction learned: ${tracker.calibrated}, last swing missed: ${tracker.missedSwing}")
    for (t in tracker.transitions()) {
        appendLine(
            "  stop ${t.from.roundToInt()}→${t.to.roundToInt()}: gyro felt ${t.gyroDegrees.roundToInt()}°, " +
                "estimate was ${t.estimateBefore.roundToInt()}°, still for ${t.quietMs.roundToInt()} ms",
        )
    }

    appendLine("Magnetometer: ${runtime.sensors.magnetometer?.let(::describe) ?: "none"}")
    for (line in runtime.field.summary()) appendLine("  $line")

    appendLine("All sensors:")
    for (sensor in runtime.sensors.all) appendLine("  ${sensor.name} | ${sensor.vendor} | ${sensor.stringType}")
}.trimEnd()

private fun describe(sensor: Sensor) =
    "${sensor.name} by ${sensor.vendor}, resolution ${sensor.resolution}, range ${sensor.maximumRange}, " +
        "min delay ${sensor.minDelay} µs, ${reportingMode(sensor)}, wake-up ${sensor.isWakeUpSensor}"

private fun reportingMode(sensor: Sensor) = when (sensor.reportingMode) {
    Sensor.REPORTING_MODE_CONTINUOUS -> "continuous"
    Sensor.REPORTING_MODE_ON_CHANGE -> "on change"
    Sensor.REPORTING_MODE_ONE_SHOT -> "one shot"
    Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> "special"
    else -> "unknown"
}

private fun ms(value: Float?) = value?.let { "%.1f ms".format(it) } ?: "–"

private fun deg(value: Float?) = value?.let { "%.1f°".format(it) } ?: "–"

private fun share(context: Context, report: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
    context.startActivity(Intent.createChooser(send, "Share sensor report"))
}

private fun copy(context: Context, report: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("FoldFX sensor report", report))
    // Android 13 and later confirm copies on their own.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
    }
}
