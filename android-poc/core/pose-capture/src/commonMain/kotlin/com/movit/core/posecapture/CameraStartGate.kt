package com.movit.core.posecapture

/**
 * Ensures camera bind runs only after preview is bound (and vice versa).
 * Distinguishes first bind from lens-facing switch so flip does not re-run full startup.
 */
internal class CameraStartGate {
    private var previewReady = false
    private var startRequested = false
    private var boundFacingFront: Boolean? = null

    sealed class Action {
        data object Defer : Action()
        data object InitialBind : Action()
        data class SwitchFacing(val useFrontCamera: Boolean) : Action()
        data object NoOp : Action()
    }

    fun onStartRequested(useFrontCamera: Boolean): Action {
        startRequested = true
        return resolve(useFrontCamera)
    }

    fun onPreviewReady(useFrontCamera: Boolean): Action {
        previewReady = true
        return resolve(useFrontCamera)
    }

    fun isSessionBound(): Boolean = boundFacingFront != null

    fun boundFacing(): Boolean? = boundFacingFront

    fun markBound(useFrontCamera: Boolean) {
        boundFacingFront = useFrontCamera
    }

    fun shouldStartNow(): Boolean = previewReady && startRequested

    fun reset() {
        previewReady = false
        startRequested = false
        boundFacingFront = null
    }

    private fun resolve(useFrontCamera: Boolean): Action {
        if (!previewReady || !startRequested) return Action.Defer
        val bound = boundFacingFront
        return when {
            bound == null -> Action.InitialBind
            bound != useFrontCamera -> Action.SwitchFacing(useFrontCamera)
            else -> Action.NoOp
        }
    }
}
