package com.movit.core.data.outbox

enum class OutboxOperationType {
    PLANNED_WORKOUT_START,
    PLANNED_WORKOUT_COMPLETE,
    PLANNED_WORKOUT_REPORT,
    PLAN_COMPLETE,
    EXERCISE_PREFERENCE_UPSERT,
    EXERCISE_PREFERENCE_DELETE,
    USER_PROGRAM_OVERRIDE_CREATE,
    USER_PROGRAM_OVERRIDE_DELETE,
    SAVE_DAY_CUSTOMIZATIONS,
    PROGRESSION_MARK_SEEN,
    /** Single exercise execution metrics — POST api/mobile/workout-executions (Phase 07 camera path). */
    WORKOUT_EXECUTION_UPLOAD,
}

enum class OutboxStatus {
    PENDING,
    IN_FLIGHT,
    SUCCEEDED,
    FAILED_PERMANENT,
}

data class OutboxEntry(
    val id: String,
    val type: OutboxOperationType,
    val payload: String,
    val createdAt: Long,
    val attempts: Int,
    val status: OutboxStatus,
    /** Set from [com.movit.core.data.platform.MovitPlatformBindings.userId] at enqueue; null = guest. */
    val ownerUserId: String? = null,
    /** Earliest next retry (P1.2 backoff). Null = eligible immediately. */
    val nextAttemptAtEpochMs: Long? = null,
)

data class OutboxReplayResult(
    val attempted: Int,
    val succeeded: Int,
    val failed: Int,
    val skipped: Int,
)

internal enum class OutboxDispatchOutcome {
    SUCCESS,
    SERVER_WINS,
    /** Network/timeout/5xx — retryable in P1.2. */
    RETRYABLE_NETWORK,
    /** Decode/mapping bug — distinct telemetry; same retry slot until P1.2. */
    RETRYABLE_UNEXPECTED,
    PERMANENT_FAILURE,
}

internal fun parseHttpStatusFromError(message: String?): Int? {
    if (message == null) return null
    val match = HTTP_STATUS_IN_ERROR.find(message) ?: return null
    return match.groupValues[1].toIntOrNull()
}

private val HTTP_STATUS_IN_ERROR = Regex("\\((\\d{3})\\)")
