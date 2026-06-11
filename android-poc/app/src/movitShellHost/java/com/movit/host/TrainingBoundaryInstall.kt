package com.movit.host

import com.movit.feature.account.assessment.KmpAssessmentSessionBridge
import com.movit.feature.library.training.KmpTrainingSessionBridge
import com.movit.legacy.LegacyKmpAssessmentSessionFactory
import com.movit.legacy.LegacyKmpTrainingSessionFactory

/**
 * Registers legacy CameraX + MediaPipe behind KMP live-training and assessment bridges.
 */
object TrainingBoundaryInstall {
    fun install() {
        KmpAssessmentSessionBridge.factory = LegacyKmpAssessmentSessionFactory
        KmpTrainingSessionBridge.factory = LegacyKmpTrainingSessionFactory
    }
}
