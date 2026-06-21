package com.movit.feature.account

data class GoogleSignInCredentials(
    val idToken: String,
    val googleId: String,
    val email: String,
    val name: String,
    val avatarUrl: String? = null,
)
