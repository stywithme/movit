package com.movit.shared

actual object PlatformInfo {    actual val name: String = "Android"
    actual val supportsInAppSubscription: Boolean = true
    actual val supportsGoogleSignIn: Boolean = true
    // WP-14: shell androidMain stub calls onBack(); real lab is iOS-only until shell gains androidTarget + debugImplementation.
    actual val supportsTrainingDebugLab: Boolean = false
}
