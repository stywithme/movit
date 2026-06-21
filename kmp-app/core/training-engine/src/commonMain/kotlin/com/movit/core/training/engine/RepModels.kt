package com.movit.core.training.engine

enum class ErrorType {
    TOO_HIGH,
    TOO_LOW,
}

data class JointError(
    val jointCode: String,
    val errorType: ErrorType,
    val actualAngle: Double,
    val expectedMin: Double,
    val expectedMax: Double,
    val state: JointState = JointState.WARNING,
    val isPrimary: Boolean = true,
)

data class RepResult(
    val repNumber: Int,
    val score: Float,
    val worstState: JointState,
    val isCounted: Boolean,
    val isInvalidated: Boolean = false,
    val errors: List<JointError> = emptyList(),
    /** Full ERROR-severity position check snapshots for this rep (Legacy parity). */
    val positionErrors: List<RepPositionErrorSnapshot> = emptyList(),
    /** Additive check-id list; derived from [positionErrors] when populated from the pipeline. */
    val positionErrorCheckIds: List<String> = emptyList(),
    val positionWarningCount: Int = 0,
    val positionTipCount: Int = 0,
    val phaseTimings: Map<String, Long> = emptyMap(),
    val timestamp: Long = 0L,
) {
    fun getTotalErrorCount(): Int = errors.size + positionErrors.size
}

fun List<RepResult>.positionErrorRepCount(): Int = count { it.positionErrors.isNotEmpty() }
