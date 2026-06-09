package com.movit.core.training.engine

data class JointStateInfo(
    val jointCode: String,
    val state: JointState,
    val isPrimary: Boolean,
    val currentAngle: Double = 0.0,
    val currentZone: ZoneType = ZoneType.TRANSITION,
)
