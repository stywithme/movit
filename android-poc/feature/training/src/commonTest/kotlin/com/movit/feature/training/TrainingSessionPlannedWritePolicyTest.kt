package com.movit.feature.training

import com.movit.shared.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrainingSessionPlannedWritePolicyTest {
    @Test
    fun shouldNotEnqueueLegacyReportAfterComplete() {
        assertFalse(TrainingSessionPlannedWritePolicy.shouldEnqueueLegacyReportAfterComplete())
    }

    @Test
    fun canonicalEndpoint_isComplete() {
        assertEquals("complete", TrainingSessionPlannedWritePolicy.CANONICAL_COMPLETE_ENDPOINT)
    }
}

class TrainingSessionWriteDiagnosticsTest {
    @Test
    fun enqueueSuccess_incrementsPendingCount() {
        val diagnostics = TrainingSessionWriteDiagnostics()
        diagnostics.recordEnqueue(AppResult.Success("op-1"), TrainingSessionWriteDiagnostics.WriteKind.EXECUTION_UPLOAD)
        val status = diagnostics.snapshot()
        assertEquals(1, status.outboxPendingCount)
        assertEquals(TrainingSessionWriteDiagnostics.USER_NOTICE_UPLOAD_PENDING, status.userNoticeKey)
    }

    @Test
    fun enqueueFailure_surfacesErrorAndNotice() {
        val diagnostics = TrainingSessionWriteDiagnostics()
        diagnostics.recordEnqueue(
            AppResult.Failure("Sign in to upload workout data."),
            TrainingSessionWriteDiagnostics.WriteKind.EXECUTION_UPLOAD,
        )
        val status = diagnostics.snapshot()
        assertEquals(1, status.enqueueFailureCount)
        assertEquals("Sign in to upload workout data.", status.lastEnqueueError)
        assertEquals(TrainingSessionWriteDiagnostics.USER_NOTICE_UPLOAD_FAILED, status.userNoticeKey)
    }

    @Test
    fun plannedComplete_setsPlannedCompleteEnqueued() {
        val diagnostics = TrainingSessionWriteDiagnostics()
        diagnostics.recordEnqueue(AppResult.Success("pw-complete"), TrainingSessionWriteDiagnostics.WriteKind.PLANNED_COMPLETE)
        assertTrue(diagnostics.snapshot().plannedCompleteEnqueued)
    }

    @Test
    fun plannedStartFailure_incrementsFailureCount() {
        val diagnostics = TrainingSessionWriteDiagnostics()
        diagnostics.recordEnqueue(
            AppResult.Failure("auth required"),
            TrainingSessionWriteDiagnostics.WriteKind.PLANNED_START,
        )
        val status = diagnostics.snapshot()
        assertEquals(1, status.enqueueFailureCount)
        assertEquals("auth required", status.lastEnqueueError)
        assertEquals(TrainingSessionWriteDiagnostics.USER_NOTICE_UPLOAD_FAILED, status.userNoticeKey)
    }
}

class TrainingSessionLifecyclePolicyTest {
    @Test
    fun onHostPaused_returnsNullWhenNotTraining() {
        assertEquals(
            null,
            TrainingSessionLifecyclePolicy.onHostPaused(wasTraining = false, nowMs = 1_000L),
        )
    }

    @Test
    fun onHostResumed_withinTimeout_resumes() {
        val snapshot = PhasePauseSnapshot(pausedAtMs = 1_000L, wasTraining = true, phaseMaxContinueTimeMs = 60_000L)
        assertEquals(
            PhaseResumeAction.RESUMED,
            TrainingSessionLifecyclePolicy.onHostResumed(snapshot, nowMs = 30_000L),
        )
    }

    @Test
    fun onHostResumed_afterTimeout_restartsPhase() {
        val snapshot = PhasePauseSnapshot(pausedAtMs = 1_000L, wasTraining = true, phaseMaxContinueTimeMs = 5_000L)
        assertEquals(
            PhaseResumeAction.PHASE_RESTARTED_TIMEOUT,
            TrainingSessionLifecyclePolicy.onHostResumed(snapshot, nowMs = 10_000L),
        )
    }

    @Test
    fun onHostResumed_whenCannotContinue_restartsPhase() {
        val snapshot = PhasePauseSnapshot(pausedAtMs = 1_000L, wasTraining = true, phaseCanContinue = false)
        assertEquals(
            PhaseResumeAction.PHASE_RESTARTED_NO_CONTINUE,
            TrainingSessionLifecyclePolicy.onHostResumed(snapshot, nowMs = 2_000L),
        )
    }
}
