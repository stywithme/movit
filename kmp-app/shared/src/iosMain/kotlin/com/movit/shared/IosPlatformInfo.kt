package com.movit.shared

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@OptIn(ExperimentalNativeApi::class)
actual object PlatformInfo {
    actual val name: String = "iOS"
    actual val supportsInAppSubscription: Boolean = true
    actual val supportsGoogleSignIn: Boolean = true
    actual val supportsTrainingDebugLab: Boolean = Platform.isDebugBinary
}
