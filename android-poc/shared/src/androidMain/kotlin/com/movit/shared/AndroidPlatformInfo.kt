package com.movit.shared

actual object PlatformInfo {
    actual val name: String = "Android"
    actual val supportsInAppSubscription: Boolean = true
    actual val supportsGoogleSignIn: Boolean = true
}
