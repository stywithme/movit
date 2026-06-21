package com.movit.feature.training

import com.movit.shared.AppResult

/**
 * Session-scoped upload / planned-write outcomes for UX and logging.
 *
 * Outbox enqueue success means "pending sync" (offline-safe). Failure usually means missing auth.
 */
data class TrainingSessionWriteStatus(
    val outboxPendingCount: Int = 0,
    val enqueueFailureCount: Int = 0,
    val lastEnqueueError: String? = null,
    val plannedCompleteEnqueued: Boolean = false,
    val userNoticeKey: String? = null,
) {
    val hasPendingSync: Boolean get() = outboxPendingCount > 0
    val hasEnqueueFailure: Boolean get() = enqueueFailureCount > 0
}

class TrainingSessionWriteDiagnostics {
    private var outboxPendingCount = 0
    private var enqueueFailureCount = 0
    private var lastEnqueueError: String? = null
    private var plannedCompleteEnqueued = false

    fun recordEnqueue(result: AppResult<String>, kind: WriteKind) {
        when (result) {
            is AppResult.Success -> {
                outboxPendingCount++
                if (kind == WriteKind.PLANNED_COMPLETE) {
                    plannedCompleteEnqueued = true
                }
            }
            is AppResult.Failure -> {
                enqueueFailureCount++
                lastEnqueueError = result.message
            }
        }
    }

    fun snapshot(): TrainingSessionWriteStatus = TrainingSessionWriteStatus(
        outboxPendingCount = outboxPendingCount,
        enqueueFailureCount = enqueueFailureCount,
        lastEnqueueError = lastEnqueueError,
        plannedCompleteEnqueued = plannedCompleteEnqueued,
        userNoticeKey = resolveUserNoticeKey(),
    )

    private fun resolveUserNoticeKey(): String? = when {
        enqueueFailureCount > 0 -> USER_NOTICE_UPLOAD_FAILED
        outboxPendingCount > 0 -> USER_NOTICE_UPLOAD_PENDING
        else -> null
    }

    enum class WriteKind {
        EXECUTION_UPLOAD,
        PLANNED_COMPLETE,
        PLANNED_START,
    }

    companion object {
        const val USER_NOTICE_UPLOAD_PENDING = "training_upload_outbox_pending"
        const val USER_NOTICE_UPLOAD_FAILED = "training_upload_enqueue_failed"
    }
}
