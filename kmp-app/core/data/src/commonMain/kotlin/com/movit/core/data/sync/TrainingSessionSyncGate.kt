package com.movit.core.data.sync

import kotlin.concurrent.Volatile

/**
 * P2.11: while a training session is active, full catalog refresh is deferred.
 * Delta sync + outbox replay remain allowed.
 */
object TrainingSessionSyncGate {
    @Volatile
    var trainingSessionActive: Boolean = false
}
