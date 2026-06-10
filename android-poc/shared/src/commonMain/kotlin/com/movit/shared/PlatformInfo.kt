package com.movit.shared

expect object PlatformInfo {
    val name: String

    /** In-app subscription purchase UI (Google Play / StoreKit bridge). */
    val supportsInAppSubscription: Boolean
}
