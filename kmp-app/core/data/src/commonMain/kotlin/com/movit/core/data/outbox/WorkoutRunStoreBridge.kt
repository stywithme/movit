package com.movit.core.data.outbox

/**
 * feature:library registers [clearMemory] from [com.movit.feature.library.WorkoutRunStore]
 * so logout / account-switch can drop in-memory open runs without a core→feature dependency.
 */
object WorkoutRunStoreBridge {
    var clearMemory: (() -> Unit)? = null

    fun clearMemoryIfRegistered() {
        clearMemory?.invoke()
    }
}
