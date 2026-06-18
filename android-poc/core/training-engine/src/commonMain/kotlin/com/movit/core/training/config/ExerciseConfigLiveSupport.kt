package com.movit.core.training.config

import com.movit.core.training.engine.JointEvalInput
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.JointStateInfo
import com.movit.core.training.engine.Phase
import com.movit.core.training.engine.PhaseJointConfig
import com.movit.core.training.engine.PhaseTimingConfig
import com.movit.core.training.engine.ZoneType

private data class ConfigJointEval(
    override val code: String,
    override val state: JointState,
    override val zoneType: ZoneType,
    override val smoothedAngle: Double,
    override val isPrimary: Boolean,
    val stateRanges: StateRanges? = null,
    val upStateRanges: StateRanges? = null,
    val downStateRanges: StateRanges? = null,
    val invertIndicator: Boolean = false,
) : JointEvalInput {
    override val isScorableForRepQuality: Boolean = state.isScorableForRepQuality

    override fun toJointStateInfo(): JointStateInfo = JointStateInfo(
        jointCode = code,
        state = state,
        isPrimary = isPrimary,
        currentAngle = smoothedAngle,
        currentZone = zoneType,
        stateRanges = stateRanges,
        upStateRanges = upStateRanges,
        downStateRanges = downStateRanges,
        invertIndicator = invertIndicator,
    )
}

object ExerciseConfigDefaults {
    const val MIN_REP_INTERVAL_MS: Long = 400L
    const val MAX_REP_INTERVAL_MS: Long = 5_000L
    const val MIN_PHASE_DURATION_MS: Long = 100L
}

fun ExerciseConfig.primaryPhaseJointConfigs(variantIndex: Int = 0): List<PhaseJointConfig> =
    getPrimaryJoints(variantIndex).map(::TrackedJointPhaseAdapter)

fun ExerciseConfig.primaryJointCodes(variantIndex: Int = 0): Set<String> =
    getPrimaryJoints(variantIndex).map { it.joint }.toSet()

fun ExerciseConfig.phaseTimingConfig(): PhaseTimingConfig = PhaseTimingConfig(
    minRepIntervalMs = repCountingConfig.getMinRepInterval(ExerciseConfigDefaults.MIN_REP_INTERVAL_MS),
    maxRepIntervalMs = repCountingConfig.getMaxRepInterval(ExerciseConfigDefaults.MAX_REP_INTERVAL_MS),
    minPhaseDurationMs = repCountingConfig.calculateMinPhaseDuration(
        numberOfPhases = 4,
        defaultMinPhaseDuration = ExerciseConfigDefaults.MIN_PHASE_DURATION_MS,
    ),
)

fun ExerciseConfig.evaluatePrimaryJoints(
    angles: Map<String, Double>,
    phase: Phase,
    variantIndex: Int = 0,
): Map<String, JointEvalInput> = buildMap {
    @Suppress("UNUSED_PARAMETER")
    val unusedPhase = phase
    for (joint in getPrimaryJoints(variantIndex)) {
        val angle = angles[joint.joint] ?: continue
        val zone = joint.determineZoneType(angle)
        val state = joint.determineState(angle)
        val stateRanges = when {
            joint.hasStateHoldRange() -> joint.getStateHoldRange()
            zone == ZoneType.UP_ZONE && joint.hasStateUpDownRanges() -> joint.getStateUpRange()
            zone == ZoneType.DOWN_ZONE && joint.hasStateUpDownRanges() -> joint.getStateDownRange()
            else -> null
        }
        put(
            joint.joint,
            ConfigJointEval(
                code = joint.joint,
                state = state,
                zoneType = zone,
                smoothedAngle = angle,
                isPrimary = true,
                stateRanges = stateRanges,
                upStateRanges = if (joint.hasStateUpDownRanges()) joint.getStateUpRange() else stateRanges,
                downStateRanges = if (joint.hasStateUpDownRanges()) joint.getStateDownRange() else stateRanges,
                invertIndicator = joint.invertIndicator,
            ),
        )
    }
}
