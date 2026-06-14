package com.movit.core.training.engine

import com.movit.core.training.config.CheckSeverity
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.PositionCheckType
import com.movit.core.training.position.PositionError
import kotlinx.serialization.Serializable

/**
 * Durable per-rep snapshot of a position check violation (ERROR severity).
 * Mirrors Legacy `PositionError` fields for report and common-error analysis.
 */
@Serializable
data class RepPositionErrorSnapshot(
    val checkId: String,
    val type: PositionCheckType,
    val severity: CheckSeverity,
    val message: LocalizedText,
    val actualValue: Double,
    val threshold: Double,
    val landmark1: String,
    val landmark2: String,
) {
    companion object {
        fun minimalError(checkId: String): RepPositionErrorSnapshot = RepPositionErrorSnapshot(
            checkId = checkId,
            type = PositionCheckType.VERTICAL_COMPARISON,
            severity = CheckSeverity.ERROR,
            message = LocalizedText(),
            actualValue = 0.0,
            threshold = 0.0,
            landmark1 = "",
            landmark2 = "",
        )
    }
}

fun PositionError.toRepSnapshot(): RepPositionErrorSnapshot = RepPositionErrorSnapshot(
    checkId = checkId,
    type = type,
    severity = severity,
    message = message,
    actualValue = actualValue,
    threshold = threshold,
    landmark1 = landmark1,
    landmark2 = landmark2,
)
