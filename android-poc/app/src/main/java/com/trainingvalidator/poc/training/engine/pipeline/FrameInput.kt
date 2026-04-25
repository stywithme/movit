package com.trainingvalidator.poc.training.engine.pipeline

import android.os.SystemClock
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark

/**
 * One frame of pose inputs for [com.trainingvalidator.poc.training.TrainingEngine].
 * Thin DTO for pipeline entry (Phase 6.1); delegates to [com.trainingvalidator.poc.training.TrainingEngine.processFrame].
 */
data class FrameInput(
    val angles: JointAngles,
    val landmarks: List<SmoothedLandmark>? = null,
    val isFrontCamera: Boolean = false,
    val timestampMs: Long = SystemClock.uptimeMillis()
)
