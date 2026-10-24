package com.amirmohideen.foldfx.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amirmohideen.foldfx.Access
import com.amirmohideen.foldfx.Experience
import com.amirmohideen.foldfx.FoldRuntime
import com.amirmohideen.foldfx.Screen
import com.amirmohideen.foldfx.experiences

@Composable
fun HomeScreen(runtime: FoldRuntime, onOpen: (Screen) -> Unit) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = insets.calculateTopPadding() + 24.dp,
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(bottom = 8.dp)) {
                    Text("FoldFX", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Brass)
                    Text("Fold your phone. Feel things.", color = Muted)
                }
            }
            if (runtime.sensors.hinge == null) {
                item {
                    Notice(
                        "This phone doesn't report its hinge angle, so FoldFX can't feel it fold. " +
                            "You can still try the door with an on-screen slider.",
                    )
                }
            }
            items(experiences) { experience ->
                ExperienceCard(experience, onClick = { experience.screen?.let(onOpen) })
            }
            item {
                Text(
                    "🔒 Premium experiences unlock with a one-time purchase when FoldFX launches.",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            item {
                ExperienceCard(
                    Experience(
                        "Sensor lab",
                        "See exactly how your phone reports folding, and copy a report.",
                        "📈",
                        Access.FREE,
                        Screen.LAB,
                    ),
                    onClick = { onOpen(Screen.LAB) },
                )
            }
        }
    }
}

@Composable
private fun ExperienceCard(experience: Experience, onClick: () -> Unit) {
    val ready = experience.screen != null
    Card(
        onClick = onClick,
        enabled = ready,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(experience.emoji, fontSize = 32.sp, modifier = Modifier.alpha(if (ready) 1f else 0.5f))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        experience.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = if (ready) Parchment else Muted,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Badge(experience, ready)
                }
                Text(experience.tagline, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun Badge(experience: Experience, ready: Boolean) {
    val label = when {
        !ready && experience.access == Access.PREMIUM -> "🔒 Soon"
        !ready -> "Soon"
        experience.access == Access.FREE -> "Free"
        else -> "🔒"
    }
    Text(label, color = if (ready) Brass else Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun Notice(text: String) {
    Card(
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(text, color = Parchment, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
    }
}
