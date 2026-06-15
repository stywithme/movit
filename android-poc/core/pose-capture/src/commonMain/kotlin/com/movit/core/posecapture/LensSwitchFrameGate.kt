package com.movit.core.posecapture

/**
 * Suppresses pose frames from the previous lens until the first frame matching [beginAwaitingFrames].
 * Used with [CameraStartGate.Action.SwitchFacing] so setup/countdown/training do not ingest stale facing.
 */
internal class LensSwitchFrameGate {
    enum class FrameDecision {
        /** Frame is from the previous lens — discard. */
        Suppress,

        /** Normal delivery (no lens switch pending). */
        Deliver,

        /** First frame from the new lens — deliver and signal switch complete. */
        DeliverCompleteSwitch,
    }

    private var awaitingFacing: Boolean? = null

    fun beginAwaitingFrames(useFrontCamera: Boolean) {
        awaitingFacing = useFrontCamera
    }

    fun isAwaitingFrames(): Boolean = awaitingFacing != null

    fun clear() {
        awaitingFacing = null
    }

    fun acceptFrame(isFrontCamera: Boolean): FrameDecision {
        val expected = awaitingFacing ?: return FrameDecision.Deliver
        if (isFrontCamera != expected) return FrameDecision.Suppress
        awaitingFacing = null
        return FrameDecision.DeliverCompleteSwitch
    }
}

/** Engine and debug consumers must share the same gate delivery policy. */
internal fun LensSwitchFrameGate.FrameDecision.deliversToConsumers(): Boolean = when (this) {
    LensSwitchFrameGate.FrameDecision.Deliver,
    LensSwitchFrameGate.FrameDecision.DeliverCompleteSwitch,
    -> true
    LensSwitchFrameGate.FrameDecision.Suppress -> false
}
