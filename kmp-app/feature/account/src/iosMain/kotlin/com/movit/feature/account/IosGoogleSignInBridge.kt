package com.movit.feature.account

/**
 * Swift `MovitGoogleSignInBridge` implements this protocol (GoogleSignIn-iOS via CocoaPods).
 * Registered via [installIosGoogleSignInBridge] from `iosApp` before auth UI is shown.
 */
interface IosGoogleSignInBridge {
    val isAvailable: Boolean

    fun signIn(handler: IosGoogleSignInResultHandler)
}

data class IosGoogleSignInCredentials(
    val idToken: String,
    val googleId: String,
    val email: String,
    val name: String,
    val avatarUrl: String? = null,
)

/** Async callback — Swift calls [onCompleted] when Google Sign-In finishes. */
interface IosGoogleSignInResultHandler {
    fun onCompleted(credentials: IosGoogleSignInCredentials?)
}

object IosGoogleSignInBridgeRegistry {
    private var bridge: IosGoogleSignInBridge? = null

    fun current(): IosGoogleSignInBridge? = bridge

    internal fun install(bridge: IosGoogleSignInBridge?) {
        this.bridge = bridge
    }
}
