package com.movit.feature.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.movit.shared.PlatformInfo

/**
 * iOS Google Sign-In blocker (Phase 05 bootstrap).
 *
 * Native integration requires GoogleSignIn-iOS + URL scheme wiring in iosApp.
 * Until then, [MovitAuthViewModel] surfaces [auth_google_ios_blocker] and the sign-in
 * panel hides the Google CTA when [PlatformInfo.supportsGoogleSignIn] is false.
 */
@Composable
actual fun GoogleSignInHost(
    pending: Boolean,
    onCompleted: (GoogleSignInCredentials?) -> Unit,
) {
    LaunchedEffect(pending) {
        if (pending && !PlatformInfo.supportsGoogleSignIn) {
            onCompleted(null)
        }
    }
}