package com.movit.shared

import com.movit.shared.buildconfig.MovitGeneratedBuildConfig

actual object PlatformInfo {    actual val name: String = "Android"
    actual val supportsInAppSubscription: Boolean = true
    actual val supportsGoogleSignIn: Boolean = true
    // The shell now renders the lab on Android as well. Debug builds get it unconditionally;
    // release builds go through the admin gate in TrainingDebugAccess.
    actual val supportsTrainingDebugLab: Boolean = MovitGeneratedBuildConfig.DEBUG
}
