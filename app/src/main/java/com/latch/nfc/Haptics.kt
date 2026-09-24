package com.latch.nfc

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    fun success() = play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
    fun tick() = play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    fun error() = play(VibrationEffect.createWaveform(longArrayOf(0, 90, 70, 90), -1))

    private fun play(effect: VibrationEffect) {
        runCatching { vibrator?.vibrate(effect) }
    }
}
