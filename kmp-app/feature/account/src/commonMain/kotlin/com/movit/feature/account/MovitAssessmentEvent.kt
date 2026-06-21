package com.movit.feature.account

import com.movit.core.training.model.PoseFrame

sealed interface MovitAssessmentEvent {
    data class ParqAnswered(val questionIndex: Int, val yes: Boolean) : MovitAssessmentEvent
    data object ContinueToBodyScan : MovitAssessmentEvent
    data object BodyScanCameraReady : MovitAssessmentEvent
    data object BodyScanGuidedModeStarted : MovitAssessmentEvent
    data class BodyScanFrameReceived(val frame: PoseFrame?) : MovitAssessmentEvent
    data class BodyScanError(val message: String) : MovitAssessmentEvent
    data object CompleteBodyScan : MovitAssessmentEvent
    data object BrowseProgramsClicked : MovitAssessmentEvent
    data object GoHomeClicked : MovitAssessmentEvent
    data object BackClicked : MovitAssessmentEvent
}
