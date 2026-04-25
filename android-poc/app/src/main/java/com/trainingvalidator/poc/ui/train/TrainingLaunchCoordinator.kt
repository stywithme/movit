package com.trainingvalidator.poc.ui.train

import android.util.Log
import android.widget.Toast
import androidx.core.content.IntentCompat
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
 *
 * [TrainingActivity] remains the lifecycle owner; this class only groups launch logic.
 */
class TrainingLaunchCoordinator(
    private val host: TrainingActivity
) {
    /**
     * Reads video/camera mode, indicator, and flags; updates [host] and overlay.
     * @return `false` if the activity is finishing and must not continue.
     */
    fun applyEarlyIntentOrFinish(): Boolean {
        val trainingMode = host.intent.getStringExtra(TrainingActivity.EXTRA_TRAINING_MODE)
            ?: TrainingActivity.MODE_CAMERA
        host.isVideoMode = trainingMode == TrainingActivity.MODE_VIDEO
        host.videoUri = IntentCompat.getParcelableExtra(
            host.intent,
            TrainingActivity.EXTRA_VIDEO_URI,
            android.net.Uri::class.java
        )
        host.viewModel.supervisor.isVideoMode = host.isVideoMode

        if (host.isVideoMode && host.videoUri == null) {
            Toast.makeText(host, host.getString(com.trainingvalidator.poc.R.string.no_video_selected), Toast.LENGTH_LONG)
                .show()
            host.finish()
            return false
        }

        val indicatorType = host.intent.getStringExtra(TrainingActivity.EXTRA_INDICATOR_TYPE)
            ?: SettingsManager.getIndicatorType()
        host.currentIndicatorType = indicatorType
        SettingsManager.setIndicatorType(indicatorType)
        host.binding.skeletonOverlay.setIndicatorType(indicatorType)

        host.isAssessmentMode = host.intent.getBooleanExtra(TrainingActivity.EXTRA_ASSESSMENT_MODE, false)
        host.isSessionMode = host.intent.getBooleanExtra(TrainingActivity.EXTRA_IS_SESSION_MODE, false)
        return true
    }

    /**
     * Runs [ExerciseRepository.initialize] off the main thread, then on the main thread
     * calls [afterReady] (typically [TrainingActivity.parseIntentExtrasAfterRepositoryReady]).
     */
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
