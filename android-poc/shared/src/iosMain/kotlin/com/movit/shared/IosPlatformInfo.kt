package com.movit.shared

actual object PlatformInfo {
    actual val name: String = "iOS"
    actual val supportsInAppSubscription: Boolean = false
    actual val supportsGoogleSignIn: Boolean = false
}
