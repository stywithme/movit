package com.movit.core.training.engine

interface JointEvalInput {
    val code: String
    val state: JointState
    val zoneType: ZoneType
    val isPrimary: Boolean
    val isScorableForRepQuality: Boolean
    val smoothedAngle: Double

    fun toJointStateInfo(): JointStateInfo = JointStateInfo(
        jointCode = code,
        state = state,
        isPrimary = isPrimary,
        currentAngle = smoothedAngle,
        currentZone = zoneType,
    )
}
