package com.movit.core.data.outbox

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes legacy [AnalyticsStorage] drain (Android) with new KMP outbox enqueues so the same
 * execution id is not uploaded twice when a pending legacy file is migrated into the outbox.
 */
object LegacyWorkoutSyncGate {
    private val mutex = Mutex()
    private var activeDrains = 0
    private val waiters = mutableListOf<CompletableDeferred<Unit>>()

    /** Platform hook — Android registers [com.movit.host.LegacyWorkoutSyncDrain] from app install. */
    var legacyDrainRunner: (suspend () -> Unit)? = null

    suspend fun awaitBeforeEnqueue() {
        while (true) {
            val wait = mutex.withLock {
                if (activeDrains == 0) return
                CompletableDeferred<Unit>().also { waiters.add(it) }
            }
            wait.await()
        }
    }

    suspend fun runDrain(block: suspend () -> Unit) {
        mutex.withLock { activeDrains++ }
        try {
            block()
        } finally {
            mutex.withLock {
                activeDrains--
                if (activeDrains == 0) {
                    waiters.forEach { it.complete(Unit) }
                    waiters.clear()
                }
            }
        }
    }

    /** Drains legacy pending executions when a platform runner is registered. */
    suspend fun drainLegacyExecutionsIfRegistered() {
        val runner = legacyDrainRunner ?: return
        runDrain { runner() }
    }
}
