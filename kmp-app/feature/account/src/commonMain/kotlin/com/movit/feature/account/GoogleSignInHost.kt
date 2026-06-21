package com.movit.feature.account

import androidx.compose.runtime.Composable

/**
 * Platform bridge for Credential Manager / future iOS Google Sign-In.
 * Invoked when [MovitAuthUiState.pendingGoogleSignIn] is true.
 */
@Composable
expect fun GoogleSignInHost(
    pending: Boolean,
    onCompleted: (GoogleSignInCredentials?) -> Unit,
)
