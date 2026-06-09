package com.movit.legacy

import android.content.Context
import android.content.Intent
import com.trainingvalidator.poc.ui.train.TrainingActivity

/**
 * Boundary from Movit KMP shell to legacy camera training (Pre-06 WS-C).
 *
 * Shell does not embed CameraX/MediaPipe in Pre-06; use this launcher until
 * Phase 07 platform adapters exist. Mirrors [PreWorkoutActivity] / [ExerciseDetailActivity].
 */
object LegacyTrainingLauncher {

    fun cameraExerciseIntent(
        context: Context,
        exerciseFileName: String,
        poseVariant: Int = 0,
        indicatorType: String? = null,
    ): Intent =
        Intent(context, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_EXERCISE_NAME, exerciseFileName)
            putExtra(TrainingActivity.EXTRA_POSE_VARIANT, poseVariant)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_CAMERA)
            indicatorType?.let { putExtra(TrainingActivity.EXTRA_INDICATOR_TYPE, it) }
        }

    fun startCameraExercise(
        context: Context,
        exerciseFileName: String,
        poseVariant: Int = 0,
        indicatorType: String? = null,
    ) {
        context.startActivity(
            cameraExerciseIntent(context, exerciseFileName, poseVariant, indicatorType),
        )
    }
}
