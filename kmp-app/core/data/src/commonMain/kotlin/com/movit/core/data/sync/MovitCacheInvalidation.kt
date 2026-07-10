package com.movit.core.data.sync

import kotlin.concurrent.Volatile

/**
 * Lightweight bridge so optimistic / outbox paths can bump UI freshness without
 * holding a hard reference to [MovitSyncOrchestrator] (P2.10).
 *
 * Method named [signal] (not `notify`/`emit`) to avoid JVM Object / Flow clashes.
 */
object MovitCacheInvalidation {
    @Volatile
    var sink: (() -> Unit)? = null

    fun signal() {
        sink?.invoke()
    }
}
