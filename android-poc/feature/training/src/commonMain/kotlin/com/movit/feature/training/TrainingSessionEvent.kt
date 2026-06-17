package com.movit.feature.training

import com.movit.core.training.model.PoseFrame

sealed interface TrainingSessionEvent {
    data object StartWorkoutExercise : TrainingSessionEvent
    data object SkipRest : TrainingSessionEvent
    data object StopSession : TrainingSessionEvent
    data class PoseFrameReceived(val frame: PoseFrame?) : TrainingSessionEvent
    data object CameraReady : TrainingSessionEvent
    data object CameraSwitchStarted : TrainingSessionEvent
    data class CameraError(val message: String) : TrainingSessionEvent
    data object Pause : TrainingSessionEvent
    data object Resume : TrainingSessionEvent
    data object Stop : TrainingSessionEvent
    data class HostBackgrounded(val nowMs: Long? = null) : TrainingSessionEvent
    data class HostForegrounded(val nowMs: Long? = null) : TrainingSessionEvent
    data object BackPressed : TrainingSessionEvent
    data object FinishClicked : TrainingSessionEvent
    data object ViewReportClicked : TrainingSessionEvent
}
