package com.movit.shared

import com.movit.shared.buildconfig.MovitGeneratedBuildConfig

actual object PlatformInfo {
    actual val name: String = "Android"
    actual val supportsInAppSubscription: Boolean = true
    actual val supportsGoogleSignIn: Boolean = true
    actual val supportsTrainingDebugLab: Boolean = MovitGeneratedBuildConfig.DEBUG
}
