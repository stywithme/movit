package com.movit.core.posecapture

import com.movit.core.training.boundary.HapticPattern
import com.movit.core.training.boundary.HapticsPort
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

/**
 * iOS haptics actual (WS-9) — implements [HapticsPort] for Koin / [MovitPoseCaptureIosBindings].
 */
@OptIn(ExperimentalForeignApi::class)
class IosHapticsPort : HapticsPort {
    override fun vibrate(pattern: HapticPattern) {
        val style = when (pattern) {
            HapticPattern.LIGHT -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
            HapticPattern.MEDIUM -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
            HapticPattern.HEAVY -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
        }
        val generator = UIImpactFeedbackGenerator(style = style)
        generator.prepare()
        generator.impactOccurred()
    }

    fun notifySuccess() {
        val generator = UINotificationFeedbackGenerator()
        generator.prepare()
        generator.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
    }
}
