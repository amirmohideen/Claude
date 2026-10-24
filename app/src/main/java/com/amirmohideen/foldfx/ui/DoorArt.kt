package com.amirmohideen.foldfx.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.amirmohideen.foldfx.core.audio.smoothstep
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val WallTop = Color(0xFF2B231E)
private val WallBottom = Color(0xFF1B1613)
private val FloorNear = Color(0xFF1E150F)
private val FloorFar = Color(0xFF3A2A1D)
private val Casing = Color(0xFF54371F)
private val Jamb = Color(0xFF3B2716)
private val Wood = Color(0xFF8A5530)
private val WoodLight = Color(0xFFA0683C)
private val WoodDark = Color(0xFF5E381C)
private val Beyond = Color(0xFF0B0807)
private val Candle = Color(0xFFFFB347)
private val BrassLight = Color(0xFFFFE6A0)
private val BrassMid = Color(0xFFD9A441)
private val BrassDark = Color(0xFF7A5418)
private val Iron = Color(0xFF2A2420)

/** Door panels as (u0, v0, u1, v1): across from the hinge, and down from the top. */
private val panels = listOf(
    floatArrayOf(0.14f, 0.08f, 0.46f, 0.47f),
    floatArrayOf(0.54f, 0.08f, 0.86f, 0.47f),
    floatArrayOf(0.14f, 0.57f, 0.46f, 0.91f),
    floatArrayOf(0.54f, 0.57f, 0.86f, 0.91f),
)

/**
 * Projects points on a door that swings away from the viewer on a hinge along its left edge.
 * u runs across the door from the hinge (0) to the free edge (1); v runs from the top (0) to the
 * bottom (1); `out` is how far a point stands off the door's face toward the viewer.
 */
private class DoorProjection(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    private val eye: Offset,
    private val focal: Float,
    openDegrees: Float,
) {
    val cosA = cos(openDegrees * PI / 180.0).toFloat()
    val sinA = sin(openDegrees * PI / 180.0).toFloat()

    fun point(u: Float, v: Float, out: Float = 0f): Offset {
        val along = u * width
        val x = left + along * cosA + out * sinA
        val z = along * sinA - out * cosA
        val y = top + v * height
        val s = scale(z)
        return Offset(eye.x + (x - eye.x) * s, eye.y + (y - eye.y) * s)
    }

    /** A point in the doorway at [depth] into the wall, where the door is shut. */
    fun frame(x: Float, y: Float, depth: Float): Offset {
        val s = scale(depth)
        return Offset(eye.x + (x - eye.x) * s, eye.y + (y - eye.y) * s)
    }

    fun scaleAt(u: Float, out: Float = 0f) = scale(u * width * sinA - out * cosA)

    private fun scale(z: Float) = focal / (focal + z)
}

/**
 * Draws a room with a wooden door, open by [openDegrees] (0 shut, up to about 100).
 * [seconds] drives small animations such as blinking.
 */
fun DrawScope.drawDoorScene(openDegrees: Float, seconds: Float) {
    val w = size.width
    val h = size.height
    val doorHeight = min(h * 0.68f, w * 0.62f / 0.45f)
    val doorWidth = doorHeight * 0.45f
    val left = (w - doorWidth) / 2f
    val top = (h - doorHeight) * 0.42f
    val right = left + doorWidth
    val bottom = top + doorHeight
    val open = smoothstep(0f, 60f, openDegrees)
    val door = DoorProjection(
        left, top, doorWidth, doorHeight,
        eye = Offset(w / 2f, top + doorHeight * 0.42f),
        focal = doorWidth * 2.8f,
        openDegrees = openDegrees,
    )

    drawRoom(w, h, bottom)
    drawLightSpill(left, right, bottom, h, open)
    drawDoorway(door, right, bottom, openDegrees, seconds)
    drawLeaf(door)
    drawCasing(left, top, right, bottom, doorWidth)
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
            center = Offset(w / 2f, h / 2f),
            radius = max(w, h) * 0.75f,
        ),
    )
}

private fun DrawScope.drawRoom(w: Float, h: Float, floorY: Float) {
    drawRect(Brush.verticalGradient(listOf(WallTop, WallBottom), endY = floorY), size = Size(w, floorY))
    // Faint wallpaper stripes.
    val stripe = 22f * density
    var x = 0f
    while (x < w) {
        drawRect(Color.White.copy(alpha = 0.025f), topLeft = Offset(x, 0f), size = Size(stripe * 0.35f, floorY))
        x += stripe
    }
    drawRect(
        Brush.verticalGradient(listOf(FloorFar, FloorNear), startY = floorY, endY = h),
        topLeft = Offset(0f, floorY),
        size = Size(w, h - floorY),
    )
    // Floorboards, closer together toward the back.
    for (k in 1..6) {
        val y = floorY + (h - floorY) * (k / 6f) * (k / 6f)
        drawLine(Color.Black.copy(alpha = 0.18f), Offset(0f, y), Offset(w, y), strokeWidth = density)
    }
    drawRect(Casing.copy(alpha = 0.7f), topLeft = Offset(0f, floorY - 9f * density), size = Size(w, 9f * density))
}

private fun DrawScope.drawLightSpill(left: Float, right: Float, floorY: Float, h: Float, open: Float) {
    if (open <= 0f) return
    val spread = (right - left) * 0.6f
    val path = Path().apply {
        moveTo(left, floorY)
        lineTo(right, floorY)
        lineTo(right + spread, h)
        lineTo(left - spread * 0.3f, h)
        close()
    }
    drawPath(
        path,
        Brush.verticalGradient(
            listOf(Candle.copy(alpha = 0.22f * open), Color.Transparent),
            startY = floorY,
            endY = h,
        ),
    )
}

private fun DrawScope.drawDoorway(door: DoorProjection, right: Float, bottom: Float, openDegrees: Float, seconds: Float) {
    val open = smoothstep(0f, 60f, openDegrees)
    val left = door.left
    val top = door.top
    val depth = door.width * 0.14f
    drawRect(Beyond, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
    drawRect(
        Brush.radialGradient(
            listOf(Candle.copy(alpha = 0.45f * open), Color.Transparent),
            center = Offset(left + door.width * 0.65f, top + door.height * 0.78f),
            radius = door.width * 0.9f,
        ),
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
    )
    // The thickness of the wall, seen on the far jamb and the head of the doorway.
    quad(
        door.frame(right, top, 0f), door.frame(right, top, depth),
        door.frame(right, bottom, depth), door.frame(right, bottom, 0f),
        Jamb,
    )
    quad(
        door.frame(left, top, 0f), door.frame(right, top, 0f),
        door.frame(right, top, depth), door.frame(left, top, depth),
        WoodDark,
    )
    // Something in the dark room watches once the door is wide open.
    val eyes = smoothstep(75f, 95f, openDegrees)
    if (eyes > 0.01f && (seconds % 3.4f) > 0.14f) {
        val r = door.width * 0.022f
        val y = top + door.height * 0.36f
        for (dx in floatArrayOf(0.62f, 0.74f)) {
            val c = Offset(left + door.width * dx, y)
            drawOval(Color(0xFFC8E86A).copy(alpha = eyes), topLeft = c - Offset(r * 1.4f, r * 0.7f), size = Size(r * 2.8f, r * 1.4f))
        }
    }
}

private fun DrawScope.drawLeaf(door: DoorProjection) {
    // The door darkens as it turns away from the room's light.
    val light = 0.45f + 0.55f * door.cosA
    quad(
        door.point(0f, 0f), door.point(1f, 0f), door.point(1f, 1f), door.point(0f, 1f),
        Brush.linearGradient(
            listOf(shade(Wood, light * 0.9f), shade(WoodLight, light)),
            start = door.point(0f, 0.5f),
            end = door.point(1f, 0.5f),
        ),
    )
    // Vertical grain.
    for (k in 0 until 16) {
        val u = (k + 0.5f) / 16f
        drawLine(
            shade(WoodDark, light).copy(alpha = 0.22f),
            door.point(u, 0f),
            door.point(u, 1f),
            strokeWidth = density * door.scaleAt(u),
        )
    }
    for (p in panels) drawPanel(door, p, light)
    for (v in floatArrayOf(0.12f, 0.82f)) {
        quad(door.point(0f, v), door.point(0.03f, v), door.point(0.03f, v + 0.06f), door.point(0f, v + 0.06f), Iron)
    }
    // Past 90° the knob's side faces away from the room.
    if (door.cosA > 0.02f) drawKnob(door, light)
}

private fun DrawScope.drawPanel(door: DoorProjection, p: FloatArray, light: Float) {
    val (u0, v0, u1, v1) = p
    val du = 0.035f
    val dv = du * door.width / door.height
    quad(door.point(u0, v0), door.point(u1, v0), door.point(u1, v1), door.point(u0, v1), shade(WoodDark, light))
    quad(
        door.point(u0 + du, v0 + dv), door.point(u1 - du, v0 + dv),
        door.point(u1 - du, v1 - dv), door.point(u0 + du, v1 - dv),
        shade(Wood, light * 1.05f),
    )
    val stroke = density * door.scaleAt(u0)
    drawLine(shade(WoodLight, light * 1.2f), door.point(u0, v1), door.point(u1, v1), strokeWidth = stroke)
    drawLine(shade(WoodLight, light * 1.1f), door.point(u1, v0), door.point(u1, v1), strokeWidth = stroke)
}

private fun DrawScope.drawKnob(door: DoorProjection, light: Float) {
    val u = 0.9f
    val v = 0.52f
    val squash = max(door.cosA, 0.12f)
    val rose = door.point(u, v)
    val roseRadius = door.width * 0.06f * door.scaleAt(u)
    drawOval(
        Brush.radialGradient(listOf(shade(BrassMid, light), shade(BrassDark, light)), center = rose, radius = roseRadius),
        topLeft = Offset(rose.x - roseRadius * squash, rose.y - roseRadius),
        size = Size(2f * roseRadius * squash, 2f * roseRadius),
    )
    val keyhole = door.point(u, 0.585f)
    val keyRadius = roseRadius * 0.22f
    drawOval(
        Color(0xFF120C08),
        topLeft = Offset(keyhole.x - keyRadius * squash, keyhole.y - keyRadius),
        size = Size(2f * keyRadius * squash, 2f * keyRadius),
    )
    drawLine(
        Color(0xFF120C08),
        keyhole,
        keyhole + Offset(0f, keyRadius * 2.4f),
        strokeWidth = keyRadius * squash,
    )
    // The knob stands off the door on a short neck, so it shifts as the door turns.
    val reach = door.width * 0.08f
    val ball = door.point(u, v, out = reach)
    val ballRadius = door.width * 0.05f * door.scaleAt(u, out = reach)
    drawLine(shade(BrassDark, light), rose, ball, strokeWidth = ballRadius * 0.7f)
    drawCircle(
        Brush.radialGradient(
            listOf(BrassLight, shade(BrassMid, light), shade(BrassDark, light * 0.8f)),
            center = ball + Offset(-ballRadius * 0.35f, -ballRadius * 0.35f),
            radius = ballRadius * 1.4f,
        ),
        radius = ballRadius,
        center = ball,
    )
}

private fun DrawScope.drawCasing(left: Float, top: Float, right: Float, bottom: Float, doorWidth: Float) {
    val c = doorWidth * 0.1f
    drawRect(Casing, topLeft = Offset(left - c, top - c), size = Size(c, bottom - top + c))
    drawRect(Casing, topLeft = Offset(right, top - c), size = Size(c, bottom - top + c))
    drawRect(Casing, topLeft = Offset(left - c, top - c), size = Size(right - left + 2f * c, c))
    val edge = density * 1.5f
    val highlight = WoodLight.copy(alpha = 0.35f)
    val shadow = Color.Black.copy(alpha = 0.45f)
    drawLine(highlight, Offset(left - c, top - c), Offset(right + c, top - c), strokeWidth = edge)
    drawLine(highlight, Offset(left - c, top - c), Offset(left - c, bottom), strokeWidth = edge)
    drawLine(shadow, Offset(left, top), Offset(right, top), strokeWidth = edge)
    drawLine(shadow, Offset(left, top), Offset(left, bottom), strokeWidth = edge)
    drawLine(shadow, Offset(right, top), Offset(right, bottom), strokeWidth = edge)
}

private fun DrawScope.quad(a: Offset, b: Offset, c: Offset, d: Offset, color: Color) =
    drawPath(quadPath(a, b, c, d), color)

private fun DrawScope.quad(a: Offset, b: Offset, c: Offset, d: Offset, brush: Brush) =
    drawPath(quadPath(a, b, c, d), brush)

private fun quadPath(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(c.x, c.y)
    lineTo(d.x, d.y)
    close()
}

private fun shade(color: Color, amount: Float) = Color(
    red = (color.red * amount).coerceIn(0f, 1f),
    green = (color.green * amount).coerceIn(0f, 1f),
    blue = (color.blue * amount).coerceIn(0f, 1f),
    alpha = color.alpha,
)
