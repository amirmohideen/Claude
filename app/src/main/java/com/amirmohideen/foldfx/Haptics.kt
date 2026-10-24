package com.amirmohideen.foldfx

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    /** A heavy knock, for a door slamming; [strength] from 0 to 1. Safe to call from any thread. */
    fun thud(strength: Float) {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        val scale = strength.coerceIn(0.2f, 1f)
        val effect = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD) ->
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, scale)
                    .compose()
            vibrator.hasAmplitudeControl() -> VibrationEffect.createOneShot(45, (255 * scale).toInt())
            else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        }
        vibrator.vibrate(effect)
    }
}
