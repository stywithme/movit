package com.movit.core.training.session

import com.movit.core.training.position.AxisMatchResult

enum class SetupAxisStatus {
    PENDING,
    PASSED,
    FAILED,
}

data class SetupAxisStatuses(
    val region: SetupAxisStatus,
    val posture: SetupAxisStatus,
    val direction: SetupAxisStatus,
)

fun resolveSetupAxisStatuses(
    phase: SetupPhase,
    axisMatch: AxisMatchResult?,
): SetupAxisStatuses {
    if (axisMatch == null) {
        return SetupAxisStatuses(
            region = SetupAxisStatus.PENDING,
            posture = SetupAxisStatus.PENDING,
            direction = SetupAxisStatus.PENDING,
        )
    }
    return when (phase) {
        SetupPhase.REGION -> SetupAxisStatuses(
            region = if (axisMatch.regionMatch) SetupAxisStatus.PASSED else SetupAxisStatus.FAILED,
            posture = SetupAxisStatus.PENDING,
            direction = SetupAxisStatus.PENDING,
        )
        SetupPhase.POSTURE -> SetupAxisStatuses(
            region = SetupAxisStatus.PASSED,
            posture = if (axisMatch.postureMatch) SetupAxisStatus.PASSED else SetupAxisStatus.FAILED,
            direction = SetupAxisStatus.PENDING,
        )
        SetupPhase.DIRECTION -> SetupAxisStatuses(
            region = SetupAxisStatus.PASSED,
            posture = SetupAxisStatus.PASSED,
            direction = if (axisMatch.directionMatch) SetupAxisStatus.PASSED else SetupAxisStatus.FAILED,
        )
        SetupPhase.ANGLES -> SetupAxisStatuses(
            region = SetupAxisStatus.PASSED,
            posture = SetupAxisStatus.PASSED,
            direction = SetupAxisStatus.PASSED,
        )
    }
}
