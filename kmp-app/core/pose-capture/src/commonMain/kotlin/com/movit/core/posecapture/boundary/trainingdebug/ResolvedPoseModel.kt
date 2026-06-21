package com.movit.core.posecapture.boundary.trainingdebug

data class ResolvedPoseModel(
    val requestedType: PoseModelType,
    /** Asset path or absolute file path passed to MediaPipe BaseOptions. */
    val resolvedAssetLabel: String,
    /** User-facing label shown in Debug settings / status band. */
    val displayLabel: String,
    val usesHeavyFallback: Boolean,
    val scheduleHeavyUpgrade: Boolean,
) {
    companion object {
        fun fullBundled(): ResolvedPoseModel = ResolvedPoseModel(
            requestedType = PoseModelType.FULL,
            resolvedAssetLabel = FULL_ASSET,
            displayLabel = "Full",
            usesHeavyFallback = false,
            scheduleHeavyUpgrade = false,
        )

        const val FULL_ASSET: String = "pose_landmarker_full.task"
        const val HEAVY_ASSET: String = "pose_landmarker_heavy.task"
    }
}
