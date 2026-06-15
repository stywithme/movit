package com.movit.feature.trainingdebug

/**
 * Port for elbow correction diagnostics from [com.movit.core.training.geometry.ElbowAngleEstimator].
 * Agent 1 (Phase D2) will wire a real implementation; until then the analyzer uses [NoOp].
 */
fun interface ElbowDiagnosticsPort {
    fun snapshotForJoint(jointCode: String): ElbowDiagnosticsSnapshot?

    companion object {
        val NoOp = ElbowDiagnosticsPort { null }
    }
}
