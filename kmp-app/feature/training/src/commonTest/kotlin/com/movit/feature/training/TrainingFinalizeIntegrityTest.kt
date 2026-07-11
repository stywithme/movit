package com.movit.feature.training

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WP-05 / G-01: finalize on [TrainingFinalizeScope] must survive VM-scope cancel;
 * detach waits for finalizeInFlight.
 */
class TrainingFinalizeIntegrityTest {

    @Test
    fun finishRace_finalizeSurvivesVmCancel_enqueuesEveryTime_x100() = runBlocking {
        repeat(100) { i ->
            val outbox = mutableListOf<String>()
            val vmJob = SupervisorJob()
            val vmScope = CoroutineScope(vmJob + Dispatchers.Unconfined)

            val started = CompletableDeferred<Unit>()
            val finalizeJob = TrainingFinalizeScope.launch {
                started.await()
                // Simulate awaitPendingCaptures + build + enqueue after Finish/onCleared.
                delay(1)
                outbox += "upload-$i"
            }

            // Old bug path: work on viewModelScope would die here.
            vmScope.launch {
                started.await()
                delay(1)
                // would have enqueued — cancelled instead
            }

            started.complete(Unit)
            // FinishClicked → navigate → onCleared cancels viewModelScope mid-finalize.
            vmJob.cancel()
            finalizeJob.join()

            assertEquals(listOf("upload-$i"), outbox)
            assertFalse(vmScope.isActive)
        }
    }

    @Test
    fun detachAfterFinalize_waitsForInFlightJob() = runBlocking {
        var detached = false
        val gate = FinalizeDetachGate()
        val release = CompletableDeferred<Unit>()

        val job = TrainingFinalizeScope.launch {
            release.await()
        }
        gate.track(job)

        gate.detachWhenSafe { detached = true }
        yield()
        assertFalse(detached, "detach must wait while finalizeInFlight is active")

        release.complete(Unit)
        job.join()
        // invokeOnCompletion may run after join on some dispatchers — spin briefly.
        var spins = 0
        while (!detached && spins < 50) {
            yield()
            spins++
        }
        assertTrue(detached)
    }

    @Test
    fun detachWhenNoFinalize_runsImmediately() {
        var detached = false
        FinalizeDetachGate().detachWhenSafe { detached = true }
        assertTrue(detached)
    }
}

/**
 * Mirrors [TrainingSessionViewModel.detachWriteHooksAfterFinalize] for unit testing (WP-05).
 */
internal class FinalizeDetachGate {
    @Volatile
    private var finalizeInFlight: Job? = null

    fun track(job: Job) {
        finalizeInFlight = job
        job.invokeOnCompletion {
            if (finalizeInFlight === job) finalizeInFlight = null
        }
    }

    fun detachWhenSafe(detach: () -> Unit) {
        val job = finalizeInFlight
        if (job == null || !job.isActive) {
            detach()
        } else {
            job.invokeOnCompletion { detach() }
        }
    }
}
