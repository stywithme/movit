package com.movit.core.training.boundary

actual interface HapticsPort {
    actual fun vibrate(pattern: HapticPattern)
}

class NoOpHapticsPort : HapticsPort {
    override fun vibrate(pattern: HapticPattern) = Unit
}
