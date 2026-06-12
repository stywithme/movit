package com.movit.core.data.outbox

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.network.MovitClock

/** Retention helpers for durable outbox rows (F7). */
object OutboxMaintenance {
    const val SUCCEEDED_RETENTION_DAYS = 7

    suspend fun purgeCompletedOlderThanRetention(localStore: MovitLocalStore): Int {
        val cutoff = MovitClock.nowEpochMs() - SUCCEEDED_RETENTION_DAYS * MS_PER_DAY
        return localStore.purgeSucceededOutboxOlderThan(cutoff)
    }

    private const val MS_PER_DAY = 24L * 60 * 60 * 1000
}
