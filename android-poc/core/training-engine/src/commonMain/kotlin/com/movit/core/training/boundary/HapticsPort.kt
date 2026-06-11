package com.movit.core.training.boundary

enum class HapticPattern {
    LIGHT,
    MEDIUM,
    HEAVY,
}

/**
 * Platform haptic feedback during training (Android Vibrator; iOS UIFeedbackGenerator in WS-9).
 */
expect interface HapticsPort {
    fun vibrate(pattern: HapticPattern)
}
