package com.movit.feature.library.training

import com.movit.core.training.model.PoseFrame
import com.movit.core.training.session.LiveExerciseRunner

/**
 * Android registers a factory from [com.movit.host.TrainingBoundaryInstall] that wraps
 * legacy CameraX + MediaPipe. iOS / unconfigured builds receive null and show a fallback UI.
 */
interface KmpTrainingSession : AutoCloseable {
    fun bindPreview(surface: Any)
    fun start()
    override fun close()
}

fun interface KmpTrainingSessionFactory {
    fun create(
        hostContext: Any,
        lifecycleOwner: Any,
        runner: LiveExerciseRunner,
        onPoseFrame: (PoseFrame?) -> Unit,
        onError: (String) -> Unit,
    ): KmpTrainingSession?
}

object KmpTrainingSessionBridge {
    var factory: KmpTrainingSessionFactory? = null
}
