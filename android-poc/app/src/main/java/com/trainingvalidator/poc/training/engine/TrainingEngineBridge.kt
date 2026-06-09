package com.trainingvalidator.poc.training.engine

import com.movit.core.training.engine.JointEvalInput
import com.movit.core.training.engine.PhaseJointConfig
import com.movit.core.training.engine.PhaseTimingConfig
import com.trainingvalidator.poc.training.engine.evaluation.JointEval
import com.trainingvalidator.poc.training.models.CountingMethod
import com.trainingvalidator.poc.training.models.ErrorType
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.JointStateInfo
import com.trainingvalidator.poc.training.models.TrackedJoint
import com.trainingvalidator.poc.training.models.ZoneType
import com.movit.core.training.engine.CountingMethod as KmpCountingMethod
import com.movit.core.training.engine.ErrorType as KmpErrorType
import com.movit.core.training.engine.HoldScoreResult as KmpHoldScoreResult
import com.movit.core.training.engine.JointError as KmpJointError
import com.movit.core.training.engine.JointScoreContribution as KmpJointScoreContribution
import com.movit.core.training.engine.JointState as KmpJointState
import com.movit.core.training.engine.JointStateInfo as KmpJointStateInfo
import com.movit.core.training.engine.Phase as KmpPhase
import com.movit.core.training.engine.RepIncompleteReason as KmpRepIncompleteReason
import com.movit.core.training.engine.RepResult as KmpRepResult
import com.movit.core.training.engine.RepScoreResult as KmpRepScoreResult
import com.movit.core.training.engine.ZoneType as KmpZoneType

internal fun JointState.toKmp(): KmpJointState = when (this) {
    JointState.PERFECT -> KmpJointState.PERFECT
    JointState.NORMAL -> KmpJointState.NORMAL
    JointState.PAD -> KmpJointState.PAD
    JointState.WARNING -> KmpJointState.WARNING
    JointState.DANGER -> KmpJointState.DANGER
    JointState.TRANSITION -> KmpJointState.TRANSITION
}

internal fun KmpJointState.toApp(): JointState = when (this) {
    KmpJointState.PERFECT -> JointState.PERFECT
    KmpJointState.NORMAL -> JointState.NORMAL
    KmpJointState.PAD -> JointState.PAD
    KmpJointState.WARNING -> JointState.WARNING
    KmpJointState.DANGER -> JointState.DANGER
    KmpJointState.TRANSITION -> JointState.TRANSITION
}

internal fun ZoneType.toKmp(): KmpZoneType = when (this) {
    ZoneType.UP_ZONE -> KmpZoneType.UP_ZONE
    ZoneType.DOWN_ZONE -> KmpZoneType.DOWN_ZONE
    ZoneType.TRANSITION -> KmpZoneType.TRANSITION
}

internal fun KmpZoneType.toApp(): ZoneType = when (this) {
    KmpZoneType.UP_ZONE -> ZoneType.UP_ZONE
    KmpZoneType.DOWN_ZONE -> ZoneType.DOWN_ZONE
    KmpZoneType.TRANSITION -> ZoneType.TRANSITION
}

internal fun Phase.toKmp(): KmpPhase = when (this) {
    Phase.IDLE -> KmpPhase.IDLE
    Phase.START -> KmpPhase.START
    Phase.DOWN -> KmpPhase.DOWN
    Phase.BOTTOM -> KmpPhase.BOTTOM
    Phase.UP -> KmpPhase.UP
    Phase.COUNT -> KmpPhase.COUNT
}

internal fun KmpPhase.toApp(): Phase = when (this) {
    KmpPhase.IDLE -> Phase.IDLE
    KmpPhase.START -> Phase.START
    KmpPhase.DOWN -> Phase.DOWN
    KmpPhase.BOTTOM -> Phase.BOTTOM
    KmpPhase.UP -> Phase.UP
    KmpPhase.COUNT -> Phase.COUNT
}

internal fun CountingMethod.toKmp(): KmpCountingMethod = when (this) {
    CountingMethod.UP_DOWN -> KmpCountingMethod.UP_DOWN
    CountingMethod.HOLD -> KmpCountingMethod.HOLD
}

internal fun KmpRepIncompleteReason.toApp(): RepIncompleteReason = when (this) {
    KmpRepIncompleteReason.NO_TARGET_DEPTH -> RepIncompleteReason.NO_TARGET_DEPTH
    KmpRepIncompleteReason.NO_FULL_RETURN -> RepIncompleteReason.NO_FULL_RETURN
    KmpRepIncompleteReason.TOO_FAST -> RepIncompleteReason.TOO_FAST
    KmpRepIncompleteReason.TOO_SLOW -> RepIncompleteReason.TOO_SLOW
}

internal fun JointStateInfo.toKmpScoringInfo(): KmpJointStateInfo = KmpJointStateInfo(
    jointCode = jointCode,
    state = state.toKmp(),
    isPrimary = isPrimary,
    currentAngle = currentAngle,
    currentZone = currentZone.toKmp(),
)

internal fun com.trainingvalidator.poc.training.models.JointError.toKmp(): KmpJointError = KmpJointError(
    jointCode = jointCode,
    errorType = when (errorType) {
        ErrorType.TOO_HIGH -> KmpErrorType.TOO_HIGH
        ErrorType.TOO_LOW -> KmpErrorType.TOO_LOW
    },
    actualAngle = actualAngle,
    expectedMin = expectedMin,
    expectedMax = expectedMax,
    state = state.toKmp(),
    isPrimary = isPrimary,
)

internal fun KmpJointError.toApp(): com.trainingvalidator.poc.training.models.JointError =
    com.trainingvalidator.poc.training.models.JointError(
        jointCode = jointCode,
        errorType = when (errorType) {
            KmpErrorType.TOO_HIGH -> ErrorType.TOO_HIGH
            KmpErrorType.TOO_LOW -> ErrorType.TOO_LOW
        },
        actualAngle = actualAngle,
        expectedMin = expectedMin,
        expectedMax = expectedMax,
        message = com.trainingvalidator.poc.training.models.LocalizedText("", ""),
        state = state.toApp(),
        isPrimary = isPrimary,
    )

internal fun KmpRepScoreResult.toApp(): RepScoreResult = RepScoreResult(
    score = score,
    worstState = worstState.toApp(),
    isCounted = isCounted,
    isInvalidated = isInvalidated,
    dangerJoints = dangerJoints,
    breakdown = breakdown.mapValues { (_, value) -> value.toApp() },
)

internal fun KmpJointScoreContribution.toApp(): JointScoreContribution = JointScoreContribution(
    state = state.toApp(),
    rate = rate,
    weight = weight,
    contribution = contribution,
    isPrimary = isPrimary,
)

internal fun KmpHoldScoreResult.toApp(): HoldScoreResult = HoldScoreResult(
    score = score,
    isInvalidated = isInvalidated,
    timeInPerfect = timeInPerfect,
    timeInNormal = timeInNormal,
    timeInWarning = timeInWarning,
    timeInDanger = timeInDanger,
    totalTime = totalTime,
)

internal fun KmpRepResult.toApp(positionErrors: List<PositionError>): com.trainingvalidator.poc.training.models.RepResult =
    com.trainingvalidator.poc.training.models.RepResult(
        repNumber = repNumber,
        score = score,
        worstState = worstState.toApp(),
        isCounted = isCounted,
        isInvalidated = isInvalidated,
        errors = errors.map { it.toApp() },
        positionErrors = positionErrors,
        positionWarningCount = positionWarningCount,
        positionTipCount = positionTipCount,
        phaseTimings = phaseTimings,
        timestamp = timestamp,
    )

internal class TrackedJointPhaseAdapter(
    private val joint: TrackedJoint,
) : PhaseJointConfig {
    override val jointCode: String = joint.joint

    override fun hasUpDownRanges(): Boolean = joint.hasStateUpDownRanges()

    override fun hasHoldRange(): Boolean = joint.hasStateHoldRange()

    override fun upRangeEffectiveMin(): Double = joint.getStateUpRange().effectiveMin

    override fun upRangeOutermostMax(): Double = joint.getStateUpRange().outermostMax

    override fun downRangeOutermostMin(): Double = joint.getStateDownRange().outermostMin

    override fun downRangeEffectiveMax(): Double = joint.getStateDownRange().effectiveMax

    override fun holdRangeOutermostMin(): Double = joint.getStateHoldRange().outermostMin

    override fun holdRangeEffectiveMax(): Double = joint.getStateHoldRange().effectiveMax
}

internal class JointEvalAdapter(
    private val eval: JointEval,
) : JointEvalInput {
    override val code: String get() = eval.code
    override val state: KmpJointState get() = eval.state.toKmp()
    override val zoneType: KmpZoneType get() = eval.zoneType.toKmp()
    override val isPrimary: Boolean get() = eval.isPrimary
    override val isScorableForRepQuality: Boolean get() = eval.isScorableForRepQuality
    override val smoothedAngle: Double get() = eval.smoothedAngle
}

internal fun buildPhaseTimingConfig(
    repCountingConfig: com.trainingvalidator.poc.training.models.RepCountingConfig?,
    numberOfPhases: Int,
    timingPolicy: com.trainingvalidator.poc.training.engine.policy.TimingPolicy,
): PhaseTimingConfig = PhaseTimingConfig(
    minRepIntervalMs = timingPolicy.minRepIntervalFor(repCountingConfig),
    maxRepIntervalMs = timingPolicy.maxRepIntervalFor(repCountingConfig),
    minPhaseDurationMs = timingPolicy.minPhaseDurationFor(repCountingConfig, numberOfPhases),
)
