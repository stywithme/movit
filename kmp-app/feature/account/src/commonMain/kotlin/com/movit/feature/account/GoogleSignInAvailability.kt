package com.movit.feature.account

/** True when the platform Google Sign-In bridge is wired and configured (iOS OAuth client ID, etc.). */
expect fun isGoogleSignInBridgeAvailable(): Boolean
