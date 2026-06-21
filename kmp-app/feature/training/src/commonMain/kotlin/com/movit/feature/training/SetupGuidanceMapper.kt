package com.movit.feature.training

import com.movit.core.training.config.LocalizedText
import com.movit.core.training.session.JointSetupGuidance
import com.movit.core.training.session.SetupAxisStatus
import com.movit.core.training.session.SetupPhase
import com.movit.core.training.session.SetupReadinessResult

enum class SetupAxisStatusUi {
    PENDING,
    PASSED,
    FAILED,
}

data class SetupJointGuidanceUi(
    val jointCode: String,
    val message: String,
    val level: String,
    val direction: String? = null,
    val currentAngle: Double = 0.0,
    val isPrimary: Boolean = true,
)

data class SetupGuidanceUi(
    val phase: String,
    val progressPercent: Int,
    val actionMessage: String?,
    val cameraTip: String?,
    val regionStatus: SetupAxisStatusUi,
    val postureStatus: SetupAxisStatusUi,
    val directionStatus: SetupAxisStatusUi,
    val jointRows: List<SetupJointGuidanceUi>,
    val referenceImageUrl: String?,
    val inStartPose: Boolean,
)

fun SetupReadinessResult.toSetupGuidanceUi(language: String): SetupGuidanceUi {
    val localized = { text: LocalizedText? -> text?.get(language)?.takeIf { it.isNotBlank() } }
    val actionMessage = resolveSetupActionMessage(language, localized)
    return SetupGuidanceUi(
        phase = phase.name,
        progressPercent = progressPercent,
        actionMessage = actionMessage,
        cameraTip = localized(cameraTip),
        regionStatus = axisStatuses.region.toUi(),
        postureStatus = axisStatuses.posture.toUi(),
        directionStatus = axisStatuses.direction.toUi(),
        jointRows = jointGuidanceRows.map { it.toUi(language) },
        referenceImageUrl = referenceImageUrl,
        inStartPose = inStartPose,
    )
}

internal fun SetupReadinessResult.resolveSetupActionMessage(
    language: String,
    localized: (LocalizedText?) -> String? = { it?.get(language)?.takeIf { t -> t.isNotBlank() } },
): String? {
    return when (phase) {
        SetupPhase.ANGLES -> {
            localized(worstJointGuidance?.message)
                ?: if (inStartPose) {
                    anglesReadyMessage(language)
                } else {
                    anglesAdjustMessage(language)
                }
        }
        SetupPhase.REGION -> localized(phaseMessage) ?: localized(cameraTip)
        else -> localized(phaseMessage)
    }
}

private fun JointSetupGuidance.toUi(language: String): SetupJointGuidanceUi =
    SetupJointGuidanceUi(
        jointCode = jointCode,
        message = message.get(language),
        level = level.name,
        direction = direction?.name,
        currentAngle = currentAngle,
        isPrimary = isPrimary,
    )

private fun SetupAxisStatus.toUi(): SetupAxisStatusUi = when (this) {
    SetupAxisStatus.PENDING -> SetupAxisStatusUi.PENDING
    SetupAxisStatus.PASSED -> SetupAxisStatusUi.PASSED
    SetupAxisStatus.FAILED -> SetupAxisStatusUi.FAILED
}

private fun anglesReadyMessage(language: String): String =
    if (language == "ar") "ممتاز — ثبّت الوضعية" else "Great — hold this pose"

private fun anglesAdjustMessage(language: String): String =
    if (language == "ar") "اضبط زوايا المفاصل حسب الإرشاد" else "Adjust your joint angles as guided"
