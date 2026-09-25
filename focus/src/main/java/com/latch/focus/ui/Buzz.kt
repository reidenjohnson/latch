package com.latch.focus.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.latch.focus.engine.Prefs

/**
 * Haptics for the hold-to-latch build-up. Uses composition primitives where the phone supports them (they feel
 * crisp and can be scaled), with plain one-shots as a fallback. Respects the Haptics setting.
 * https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects#composition
 */
object Buzz {
    private fun vibrator(context: Context): Vibrator? =
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    /** A small tick; [strength] 0..1 grows as the hold goes on. */
    fun tick(context: Context, strength: Float) {
        if (!Prefs.haptics.value) return
        val v = vibrator(context) ?: return
        val s = strength.coerceIn(0.05f, 1f)
        runCatching {
            if (v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
                v.vibrate(VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, s).compose())
            } else {
                v.vibrate(VibrationEffect.createOneShot(12, (40 + 200 * s).toInt().coerceAtMost(255)))
            }
        }
    }

    /** The payoff when the hold completes: a heavy thud. */
    fun thud(context: Context) {
        if (!Prefs.haptics.value) return
        val v = vibrator(context) ?: return
        runCatching {
            if (v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
                v.vibrate(VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f).compose())
            } else {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            }
        }
    }
}
