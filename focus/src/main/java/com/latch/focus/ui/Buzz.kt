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

    /** A small tick; [strength] 0..1. */
    fun tick(context: Context, strength: Float) {
        if (!Prefs.haptics.value) return
        val v = vibrator(context) ?: return
        val s = strength.coerceIn(0.3f, 1f)
        runCatching {
            if (v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
                v.vibrate(VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, s).compose())
            } else {
                v.vibrate(VibrationEffect.createOneShot(12, (40 + 200 * s).toInt().coerceAtMost(255)))
            }
        }
    }

    /**
     * One continuous buzz that swells over [durationMs]: bzzzzzzZZZZZZ. It's a waveform of short steps whose
     * amplitude rises on a curve, so it starts faint and ends at full strength. Stop it early with [stop].
     * Needs amplitude control (VibrationEffect.createWaveform with amplitudes); without it, falls back to an even buzz.
     * https://developer.android.com/reference/android/os/VibrationEffect#createWaveform(long[],%20int[],%20int)
     */
    fun ramp(context: Context, durationMs: Int) {
        if (!Prefs.haptics.value) return
        val v = vibrator(context) ?: return
        val steps = durationMs / 50
        val timings = LongArray(steps) { 50L }
        val amps = IntArray(steps) { i ->
            val t = (i + 1f) / steps
            // Starts at ~30%: from 5%, a Galaxy S23 FE played it (vibrator_manager log) but it was too faint to feel.
            (80 + 175 * t * t).toInt().coerceIn(1, 255)
        }
        runCatching {
            if (v.hasAmplitudeControl()) v.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
            else v.vibrate(VibrationEffect.createOneShot(durationMs.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    fun stop(context: Context) {
        runCatching { vibrator(context)?.cancel() }
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
