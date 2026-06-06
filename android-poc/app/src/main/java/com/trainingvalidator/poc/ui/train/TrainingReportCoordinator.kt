package com.trainingvalidator.poc.ui.train

import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.network.ApiConfig
import com.trainingvalidator.poc.network.WorkoutSyncService
import com.trainingvalidator.poc.storage.AnalyticsStorage
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.ui.components.GlassmorphicMessageView
import com.trainingvalidator.poc.ui.report.WorkoutReportActivity
import com.trainingvalidator.poc.training.analytics.WorkoutUpload
import com.trainingvalidator.poc.training.report.ReportGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.trainingvalidator.poc.training.models.ExerciseWorkoutSummary

/**
 * Report generation, navigation to [WorkoutReportActivity], offline save, and background session sync
 * (token refresh, [WorkoutSyncService]).
 */
class TrainingReportCoordinator(
    private val host: TrainingActivity
) {
    private data class RefreshResponse(
        val tokens: TokenData? = null
    )
    private data class TokenData(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long
    )

    private val tag: String
        get() = TrainingActivity.TAG

    private fun showFallbackSummary() {
        val summary: ExerciseWorkoutSummary? = host.viewModel.trainingEngine?.stop()

        host.binding.skeletonOverlay.setTrainingMode(false)

        if (host.viewModel.isHoldExercise()) {
            val holdElapsed = host.viewModel.holdStatus.value?.elapsedMs ?: 0L
            host.binding.tvSummaryReps.text = host.formatTimeMs(holdElapsed)
            host.binding.tvSummaryCorrect.text = host.getString(
                R.string.training_target_format,
                host.formatTimeMs(host.viewModel.getTargetDurationMs())
            )
            host.binding.tvSummaryAccuracy.text = host.getString(
                R.string.training_grace_periods_format,
                host.viewModel.trainingEngine?.getGracePeriodCount() ?: 0
            )
        } else {
            host.binding.tvSummaryReps.text = "${summary?.totalReps ?: 0}"
            host.binding.tvSummaryCorrect.text = "${summary?.correctReps ?: 0} correct"
            host.binding.tvSummaryAccuracy.text = "${String.format("%.0f", summary?.accuracy ?: 0f)}%"
        }
        host.binding.tvSummaryDuration.text = summary?.getFormattedDuration() ?: "00:00"
        host.binding.completedPanel.visibility = View.VISIBLE
        host.binding.btnFinish.setOnClickListener { host.finishWithResult() }
    }

    /**
     * Used from workout-run mode [TrainingActivity.onExerciseCompleted] (and internally after single-exercise report).
     */
    suspend fun syncWorkoutExecutionToBackend(
        workoutId: String,
        workoutUpload: WorkoutUpload?
    ) {
        val appContext = host.applicationContext
        try {
            if (workoutUpload == null) {
                Log.w(tag, "No workout execution data to sync (MotionRecorder not active)")
                return
            }
            Log.d(
                tag, "Syncing workout execution to backend: ${workoutUpload.id}, " +
                    "${workoutUpload.totalReps} reps, " +
                    "avgScore=${workoutUpload.executionMetrics.avgFormScore / 10f}%"
            )
            if (AuthManager.shouldRefreshToken(appContext)) {
                Log.d(tag, "Token needs refresh, attempting...")
                refreshTokenStandalone(appContext)
            }
            val syncService = WorkoutSyncService.getInstance(
                appContext,
                ApiConfig.getEffectiveBaseUrl()
            )
            val token = AuthManager.getAccessToken(appContext)
            if (token != null) {
                syncService.setAuthToken(token)
                val result = syncService.uploadWorkout(workoutUpload)
                if (result.success) {
                    Log.d(tag, "Workout execution synced successfully!")
                } else {
                    Log.w(tag, "Workout execution sync failed (saved for later): ${result.error}")
                }
            } else {
                Log.w(tag, "No auth token, saving workout execution for later sync")
                val storage = AnalyticsStorage(appContext)
                storage.savePending(workoutUpload)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error syncing workout execution: ${e.message}", e)
        }
    }

    private suspend fun refreshTokenStandalone(context: android.content.Context) {
        try {
            val refreshTokenValue = AuthManager.getRefreshToken(context) ?: return
            val client = okhttp3.OkHttpClient()
            val json = """{"refreshToken": "$refreshTokenValue"}"""
            val body = json.toRequestBody("application/json".toMediaType())
            val request = okhttp3.Request.Builder()
                .url("${ApiConfig.getEffectiveBaseUrl()}api/mobile/auth/refresh")
                .post(body)
                .build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string()
                        bodyStr?.let { s ->
                            val result = Gson().fromJson(s, RefreshResponse::class.java)
                            if (result.tokens != null) {
                                AuthManager.saveNewTokens(
                                    context,
                                    result.tokens.accessToken,
                                    result.tokens.refreshToken,
                                    result.tokens.expiresIn
                                )
                                Log.d(tag, "Token refreshed successfully (standalone)")
                            }
                        }
                    } else {
                        Log.w(tag, "Token refresh failed: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Token refresh error: ${e.message}")
        }
    }

    /**
     * Async report generation, navigation, and best-effort sync (same as legacy [generateReportAndNavigate] on activity).
     */
    fun generateReportAndNavigate() {
        val engine = host.viewModel.trainingEngine ?: run {
            Log.e(tag, "TrainingEngine is null!")
            showFallbackSummary()
            return
        }
        val exerciseConfig = host.viewModel.exerciseConfig.value
        if (exerciseConfig == null) {
            Log.e(tag, "ExerciseConfig is null!")
            showFallbackSummary()
            return
        }
        val frameCaptures = host.frameCaptureManager?.getAllCaptures() ?: emptyList()
        val replayClips = host.frameCaptureManager?.getAllReplayClips() ?: emptyList()
        Log.d(tag, "Frame captures count: ${frameCaptures.size}, replay clips: ${replayClips.size}")

        host.binding.heroCounterContainer.visibility = View.GONE
        host.binding.progressContainer.visibility = View.GONE
        host.binding.completedPanel.visibility = View.GONE
        host.binding.glassmorphicMessage.showMessage(
            "📊 Generating report...",
            GlassmorphicMessageView.TYPE_INFO,
            durationMs = -1
        )
        host.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sessionUpload = host.viewModel.finalizeAndGetWorkoutUpload()
                val report = ReportGenerator.generateFromEngine(
                    engine = engine,
                    exerciseConfig = exerciseConfig,
                    executionDurationMs = host.viewModel.getWorkoutDurationMs(),
                    frameCaptures = frameCaptures,
                    replayClips = replayClips,
                    executionMetrics = sessionUpload?.executionMetrics,
                    weightKg = host.viewModel.getWeightKg(),
                    weightUnit = host.viewModel.getWeightUnit()
                )
                Log.d(tag, "Report generated: id=${report.id}, accuracy=${report.summary.accuracy}%")
                val saved = host.reportStorage?.save(report) ?: false
                launch(Dispatchers.Main) {
                    host.binding.glassmorphicMessage.hide()
                    if (host.isAssessmentMode) {
                        val resultIntent = android.content.Intent().apply {
                            putExtra(TrainingActivity.RESULT_REPORT_ID, report.id)
                            putExtra(TrainingActivity.RESULT_IS_COMPLETED, true)
                        }
                        host.setResult(android.app.Activity.RESULT_OK, resultIntent)
                        host.finish()
                        return@launch
                    }
                    if (saved) {
                        try {
                            val intent = WorkoutReportActivity.createIntent(host, report.id)
                            host.startActivity(intent)
                            host.finish()
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to start WorkoutReportActivity: ${e.message}", e)
                            showFallbackSummary()
                        }
                    } else {
                        showFallbackSummary()
                    }
                }
                ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        syncWorkoutExecutionToBackend(report.id, sessionUpload)
                    } catch (e: Exception) {
                        Log.w(tag, "Background sync failed, will retry later: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error generating report: ${e.message}", e)
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    host.binding.glassmorphicMessage.hide()
                    Toast.makeText(
                        host,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    showFallbackSummary()
                }
            }
        }
    }
}
