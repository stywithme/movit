package com.movit.core.posecapture.boundary.trainingdebug

import com.movit.core.training.model.Landmark

/**
 * Single debug analysis frame with raw + smoothed landmark variants for diagnostics.
 * Produced by [com.movit.core.posecapture.android.MediaPipeSyncPoseDetector] on Android.
 */
data class TrainingDebugPoseFrame(
    val rawNormalizedLandmarks: List<Landmark>,
    val smoothedNormalizedLandmarks: List<Landmark>,
    val rawWorldLandmarks: List<Landmark>?,
    val smoothedWorldLandmarks: List<Landmark>?,
    val timestampMs: Long,
    val inferenceTimeMs: Long,
    val analysisImageWidth: Int,
    val analysisImageHeight: Int,
    val modelDisplayLabel: String,
    val isFrontCamera: Boolean = false,
)
