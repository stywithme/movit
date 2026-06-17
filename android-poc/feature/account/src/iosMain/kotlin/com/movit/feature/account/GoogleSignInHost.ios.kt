package com.movit.feature.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * iOS Google Sign-In via Swift `MovitGoogleSignInBridge` (GoogleSignIn-iOS CocoaPod).
 * Requires iOS OAuth client + URL scheme in `iosApp/Info.plist` — see `iosApp/README.md`.
 */
@Composable
actual fun GoogleSignInHost(
    pending: Boolean,
    onCompleted: (GoogleSignInCredentials?) -> Unit,
) {
    LaunchedEffect(pending) {
        if (!pending) return@LaunchedEffect
        val bridge = IosGoogleSignInBridgeRegistry.current()
        if (bridge == null || !bridge.isAvailable) {
            onCompleted(null)
            return@LaunchedEffect
        }
        val credentials = suspendCancellableCoroutine { cont ->
            bridge.signIn(
                object : IosGoogleSignInResultHandler {
                    override fun onCompleted(credentials: IosGoogleSignInCredentials?) {
                        if (cont.isActive) {
                            cont.resume(
                                credentials?.let {
                                    GoogleSignInCredentials(
                                        idToken = it.idToken,
                                        googleId = it.googleId,
                                        email = it.email,
                                        name = it.name,
                                        avatarUrl = it.avatarUrl,
                                    )
                                },
                            )
                        }
                    }
                },
            )
        }
        onCompleted(credentials)
    }
}
