package com.movit.core.training.config

import com.movit.core.training.engine.PhaseJointConfig

/** Adapts [TrackedJoint] state ranges to [PhaseStateMachine] phase bounds. */
class TrackedJointPhaseAdapter(
    private val joint: TrackedJoint,
) : PhaseJointConfig {
    override val jointCode: String = joint.joint

    override fun hasUpDownRanges(): Boolean = joint.hasStateUpDownRanges()

    override fun hasHoldRange(): Boolean = joint.hasStateHoldRange()

    override fun upRangeEffectiveMin(): Double =
        if (hasUpDownRanges()) joint.getStateUpRange().effectiveMin else joint.range!!.effectiveMin

    override fun upRangeOutermostMax(): Double =
        if (hasUpDownRanges()) joint.getStateUpRange().outermostMax else joint.range!!.outermostMax

    override fun downRangeOutermostMin(): Double =
        if (hasUpDownRanges()) joint.getStateDownRange().outermostMin else joint.range!!.outermostMin

    override fun downRangeEffectiveMax(): Double =
        if (hasUpDownRanges()) joint.getStateDownRange().effectiveMax else joint.range!!.effectiveMax

    override fun holdRangeOutermostMin(): Double = joint.range?.outermostMin ?: 0.0

    override fun holdRangeEffectiveMax(): Double = joint.range?.effectiveMax ?: 0.0
}
