package com.movit.feature.training

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * Process-lifetime scope for post-exercise finalize/upload (WP-05 / G-01).
 *
 * Survives [androidx.lifecycle.ViewModel.onCleared] so awaitPendingCaptures +
 * report build + cache + outbox enqueue are not cancelled with [viewModelScope].
 *
 * ponytail: object singleton — training has no Koin module; matches other
 * process scopes (outbox replay, legacy drain). Ceiling: lives for process life.
 */
object TrainingFinalizeScope : CoroutineScope {
    override val coroutineContext: CoroutineContext =
        SupervisorJob() + Dispatchers.Default
}
