package com.movit.core.training.blueprint

import com.movit.core.training.engine.CountingMethod
import com.movit.core.training.engine.JointEvalInput
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.Phase
import com.movit.core.training.engine.PhaseJointConfig
import com.movit.core.training.engine.PhaseTimingConfig
import com.movit.core.training.engine.ZoneType

data class ExerciseBlueprint(
    val slug: String,
    val displayName: String,
    val countingMethod: CountingMethod = CountingMethod.UP_DOWN,
    val primaryJoints: List<BlueprintJointConfig>,
    val defaultTargetReps: Int = 12,
    val minRepIntervalMs: Long = 800L,
    val minPhaseDurationMs: Long = 120L,
) {
    val primaryJointCodes: Set<String> = primaryJoints.map { it.jointCode }.toSet()

    fun phaseJointConfigs(): List<PhaseJointConfig> = primaryJoints

    fun timingConfig(): PhaseTimingConfig = PhaseTimingConfig(
        minRepIntervalMs = minRepIntervalMs,
        maxRepIntervalMs = 60_000L,
        minPhaseDurationMs = minPhaseDurationMs,
    )

    fun evaluateJoints(
        angles: Map<String, Double>,
        phase: Phase,
    ): Map<String, JointEvalInput> = buildMap {
        for (joint in primaryJoints) {
            val angle = angles[joint.jointCode] ?: continue
            val zone = zoneFor(angle, joint, phase)
            val state = stateFor(angle, joint, zone)
            put(
                joint.jointCode,
                SimpleJointEval(
                    code = joint.jointCode,
                    state = state,
                    zoneType = zone,
                    smoothedAngle = angle,
                ),
            )
        }
    }

    private fun zoneFor(angle: Double, joint: BlueprintJointConfig, phase: Phase): ZoneType {
        val inUp = angle >= joint.upMin && angle <= joint.upMax
        val inDown = angle >= joint.downMin && angle <= joint.downMax
        return when {
            inUp -> ZoneType.UP_ZONE
            inDown -> ZoneType.DOWN_ZONE
            phase == Phase.IDLE -> ZoneType.TRANSITION
            angle > joint.upMin -> ZoneType.UP_ZONE
            angle < joint.downMax -> ZoneType.DOWN_ZONE
            else -> ZoneType.TRANSITION
        }
    }

    private fun stateFor(angle: Double, joint: BlueprintJointConfig, zone: ZoneType): JointState = when (zone) {
        ZoneType.UP_ZONE -> {
            val center = (joint.upMin + joint.upMax) / 2.0
            val spread = (joint.upMax - joint.upMin) / 2.0
            qualityFromDistance(angle, center, spread)
        }
        ZoneType.DOWN_ZONE -> {
            val center = (joint.downMin + joint.downMax) / 2.0
            val spread = (joint.downMax - joint.downMin) / 2.0
            qualityFromDistance(angle, center, spread)
        }
        ZoneType.TRANSITION -> JointState.TRANSITION
    }

    private fun qualityFromDistance(angle: Double, center: Double, spread: Double): JointState {
        val delta = kotlin.math.abs(angle - center)
        return when {
            delta <= spread * 0.35 -> JointState.PERFECT
            delta <= spread * 0.65 -> JointState.NORMAL
            delta <= spread * 0.9 -> JointState.PAD
            delta <= spread * 1.2 -> JointState.WARNING
            else -> JointState.DANGER
        }
    }
}

private data class SimpleJointEval(
    override val code: String,
    override val state: JointState,
    override val zoneType: ZoneType,
    override val smoothedAngle: Double,
) : JointEvalInput {
    override val isPrimary: Boolean = true
    override val isScorableForRepQuality: Boolean = state.isScorableForRepQuality
}
