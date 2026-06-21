package com.movit.feature.account

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

private const val TAG = "MovitGoogleSignIn"
private const val WEB_CLIENT_ID = "426489495025-acss2bntct6qgpc1agqif2cf9k2ha9k4.apps.googleusercontent.com"

@Composable
actual fun GoogleSignInHost(
    pending: Boolean,
    onCompleted: (GoogleSignInCredentials?) -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(pending) {
        if (!pending) return@LaunchedEffect
        if (WEB_CLIENT_ID == "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com") {
            onCompleted(null)
            return@LaunchedEffect
        }
        onCompleted(requestGoogleCredentials(context))
    }
}

private suspend fun requestGoogleCredentials(context: android.content.Context): GoogleSignInCredentials? {
    val credentialManager = CredentialManager.create(context)
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(WEB_CLIENT_ID)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    return try {
        val result = credentialManager.getCredential(context, request)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleSignInCredentials(
                    idToken = googleCredential.idToken,
                    googleId = googleCredential.id,
                    email = googleCredential.id,
                    name = googleCredential.displayName ?: "User",
                    avatarUrl = googleCredential.profilePictureUri?.toString(),
                )
            } catch (error: GoogleIdTokenParsingException) {
                Log.e(TAG, "Failed to parse Google ID token", error)
                null
            }
        } else {
            Log.e(TAG, "Unexpected credential type: ${credential::class.java}")
            null
        }
    } catch (error: GetCredentialException) {
        Log.e(TAG, "Google Sign-In failed", error)
        null
    }
}
