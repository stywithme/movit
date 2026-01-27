package com.trainingvalidator.poc.network

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * GoogleSignInHelper
 *
 * Helper class for Google Sign-In using Credential Manager.
 * Uses the modern Credential Manager API (replaces deprecated GoogleSignInClient).
 */
object GoogleSignInHelper {

    private const val TAG = "GoogleSignInHelper"

    /**
     * Google Web Client ID
     * 
     * This is the Web Client ID from Google Cloud Console.
     * You need to create OAuth 2.0 credentials in Google Cloud Console:
     * 1. Go to https://console.cloud.google.com/
     * 2. Create/select a project
     * 3. Go to APIs & Services > Credentials
     * 4. Create OAuth 2.0 Client ID (Web application type)
     * 5. Copy the Client ID here
     * 
     * Also create an Android OAuth client for your app's SHA-1 fingerprint.
     */
    private const val WEB_CLIENT_ID = "426489495025-acss2bntct6qgpc1agqif2cf9k2ha9k4.apps.googleusercontent.com"

    /**
     * Result of Google Sign-In
     */
    data class GoogleSignInResult(
        val idToken: String,
        val googleId: String,
        val email: String,
        val displayName: String,
        val photoUrl: String?
    )

    /**
     * Sign in with Google using Credential Manager
     *
     * @param context Activity context
     * @return GoogleSignInResult on success, null on failure
     */
    suspend fun signIn(context: Context): GoogleSignInResult? {
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
            handleSignInResult(result)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In failed", e)
            null
        }
    }

    /**
     * Handle the credential response
     */
    private fun handleSignInResult(result: GetCredentialResponse): GoogleSignInResult? {
        val credential = result.credential

        return when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        
                        GoogleSignInResult(
                            idToken = googleIdTokenCredential.idToken,
                            googleId = googleIdTokenCredential.id,
                            email = googleIdTokenCredential.id, // ID is the email for Google
                            displayName = googleIdTokenCredential.displayName ?: "User",
                            photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                        )
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Failed to parse Google ID token", e)
                        null
                    }
                } else {
                    Log.e(TAG, "Unexpected credential type: ${credential.type}")
                    null
                }
            }
            else -> {
                Log.e(TAG, "Unexpected credential class: ${credential.javaClass}")
                null
            }
        }
    }

    /**
     * Check if Google Sign-In is properly configured
     */
    fun isConfigured(): Boolean {
        return WEB_CLIENT_ID != "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
    }
}
