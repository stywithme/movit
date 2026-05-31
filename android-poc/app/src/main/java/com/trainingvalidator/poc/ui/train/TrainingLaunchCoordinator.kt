package com.trainingvalidator.poc.ui.train

import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.training.config.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LAUNCH_LOG_TAG = "TrainingLaunch"

/**
 * Owns the ordering of: early intent read → [ExerciseRepository.initialize] →
 * [TrainingActivity.parseIntentExtrasAfterRepositoryReady].
 */
class TrainingLaunchCoordinator(
    private val host: TrainingActivity
) {
    fun applyEarlyIntentOrFinish(): Boolean {
        val indicatorType = host.intent.getStringExtra(TrainingActivity.EXTRA_INDICATOR_TYPE)
            ?: SettingsManager.getIndicatorType()
        host.currentIndicatorType = indicatorType
        SettingsManager.setIndicatorType(indicatorType)
        host.binding.skeletonOverlay.setIndicatorType(indicatorType)

        host.isAssessmentMode = host.intent.getBooleanExtra(TrainingActivity.EXTRA_ASSESSMENT_MODE, false)
        host.isSessionMode = host.intent.getBooleanExtra(TrainingActivity.EXTRA_IS_SESSION_MODE, false)
        return true
    }

    fun runAfterRepositoryReady(afterReady: () -> Unit) {
        host.lifecycleScope.launch {
            try {
                val exerciseRepo = ExerciseRepository.getInstance(host)
                val exerciseSuccess = withContext(Dispatchers.IO) {
                    exerciseRepo.initialize(autoSync = true)
                }
                if (exerciseSuccess) {
                    Log.d(LAUNCH_LOG_TAG, "ExerciseRepository initialized successfully")
                } else {
                    Log.w(LAUNCH_LOG_TAG, "ExerciseRepository initialized but no exercises available")
                }
            } catch (e: Exception) {
                Log.e(LAUNCH_LOG_TAG, "Failed to initialize repositories", e)
            } finally {
                afterReady()
            }
        }
    }
}
