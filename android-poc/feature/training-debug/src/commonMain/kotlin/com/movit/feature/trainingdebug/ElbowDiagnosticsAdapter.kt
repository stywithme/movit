package com.movit.feature.trainingdebug

import com.movit.core.training.geometry.ElbowCorrectionDiagnostics
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.PoseLandmarkIndices

/**
 * Maps [PoseFrameAssembler.lastElbowDiagnostics] into debug UI snapshots.
 */
object PoseFrameAssemblerElbowDiagnostics : ElbowDiagnosticsPort {
    override fun snapshotForJoint(jointCode: String): ElbowDiagnosticsSnapshot? {
        val side = when (jointCode.lowercase()) {
            "left_elbow" -> 0
            "right_elbow" -> 1
            else -> return null
        }
        val diag = PoseFrameAssembler.lastElbowDiagnostics()[side] ?: return null
        return diag.toSnapshot()
    }

    private fun ElbowCorrectionDiagnostics.toSnapshot() = ElbowDiagnosticsSnapshot(
        facingRatio = facingRatio,
        screenAngle = screenAngle,
        worldAngle = worldAngle,
        maxDzShare = maxDzShare,
        correctionPct = correctionPct,
        outputAngle = outputAngle,
        isHolding = isHolding,
        strategy = strategy.legacyCode,
    )

    fun sideIndexForJoint(jointCode: String): Int? = when (jointCode.lowercase()) {
        "left_elbow" -> PoseLandmarkIndices.LEFT_ELBOW
        "right_elbow" -> PoseLandmarkIndices.RIGHT_ELBOW
        else -> null
    }
}
