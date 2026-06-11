package com.movit.core.training.boundary

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

actual interface HapticsPort {
    actual fun vibrate(pattern: HapticPattern)
}

class AndroidHapticsPort(
    context: Context,
) : HapticsPort {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun vibrate(pattern: HapticPattern) {
        val v = vibrator ?: return
        val durationMs = when (pattern) {
            HapticPattern.LIGHT -> 30L
            HapticPattern.MEDIUM -> 60L
            HapticPattern.HEAVY -> 120L
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }
}
