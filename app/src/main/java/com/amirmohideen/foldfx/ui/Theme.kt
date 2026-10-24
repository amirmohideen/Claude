package com.amirmohideen.foldfx.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brass = Color(0xFFD9A441)
val Ink = Color(0xFF14110F)
val Panel = Color(0xFF221C18)
val Parchment = Color(0xFFEDE3D6)
val Muted = Color(0xFFA89A8A)

private val colors = darkColorScheme(
    primary = Brass,
    onPrimary = Ink,
    background = Ink,
    onBackground = Parchment,
    surface = Panel,
    onSurface = Parchment,
    surfaceVariant = Color(0xFF2C251F),
    onSurfaceVariant = Muted,
)

@Composable
fun FoldFxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
