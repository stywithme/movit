package com.movit.core.training.boundary

data class CameraSourceConfiguration(
    val useFrontCamera: Boolean = false,
    val targetFps: Int = TrainingThroughputProfiles.HIGH.targetFps,
    val analysisWidth: Int = TrainingThroughputProfiles.HIGH.analysisWidth,
    val analysisHeight: Int = TrainingThroughputProfiles.HIGH.analysisHeight,
    val throughputProfileId: String = TrainingThroughputProfiles.HIGH.id,
    /** WP-18: skip ElbowAngleEstimator when no elbow is tracked. */
    val applyElbowCorrection: Boolean = true,
    /** WP-18: allocate elbow diagnostics only for debug consumers. */
    val collectElbowDiagnostics: Boolean = false,
    /**
     * WP-25: bone-length depth reconstruction as a third candidate. Default **off** — it stays
     * gated until the M-E recordings show it beats the arbiter on cross-camera consistency.
     */
    val elbowDepthReconstruction: Boolean = false,
)
