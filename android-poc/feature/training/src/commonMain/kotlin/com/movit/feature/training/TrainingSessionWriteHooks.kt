package com.movit.feature.training

import com.movit.core.data.repository.TrainingSessionWriteCoordinator
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.journal.TrainingMotionSession
import com.movit.core.training.journal.WorkoutUpload
import com.movit.core.training.report.AssessmentTrainingResult
import com.movit.core.training.report.MovitHoldReportData
import com.movit.core.training.report.MovitPeakFrameCapture
import com.movit.core.training.report.MovitRepReplayClip
import com.movit.core.training.report.MovitPostTrainingReport
import com.movit.core.training.report.MovitPostTrainingReportBuilder
import com.movit.core.training.report.MovitSessionReport
import com.movit.core.training.report.MovitSessionReportBuilder
import com.movit.core.training.report.SessionQualityMeta
import com.movit.core.training.session.ExerciseWorkoutSummary
import com.movit.core.training.session.MovitTrainingEngine
import com.movit.shared.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * WS-8 hooks: motion journal checkpoint + offline-safe mobileWrites from the session VM.
 */
class TrainingSessionWriteHooks(
    private val sessionId: String,
    private val exerciseSlug: String,
    private val writes: TrainingSessionWriteCoordinator,
    private val poseVariantIndex: Int = 0,
    private val isAssessmentMode: Boolean = false,
    private val defaultWeightKg: Float? = null,
    private val weightUnit: String = "kg",
    private val timeProvider: () -> Long = { 0L },
) {
    private var motionSession: TrainingMotionSession? = null
    var peakFrameCaptures: List<MovitPeakFrameCapture> = emptyList()
    var repReplayClips: List<MovitRepReplayClip> = emptyList()

    fun attach(engine: MovitTrainingEngine, exerciseConfig: ExerciseConfig) {
        if (motionSession != null) return
        val session = TrainingMotionSession(
            exerciseConfig = exerciseConfig,
            exerciseSlug = exerciseSlug,
            poseVariantIndex = poseVariantIndex,
            defaultWeightKg = defaultWeightKg,
            weightUnit = weightUnit,
            sessionId = sessionId,
            isAssessmentMode = isAssessmentMode,
            timeProvider = timeProvider,
            onCheckpoint = writes::checkpointJournal,
        )
        session.attach(engine)
        writes.readJournal(sessionId)?.let(session::restore)
        session.start(timeProvider())
        motionSession = session
    }

    fun detach() {
        motionSession?.detach()
        motionSession = null
    }

    fun resolveSessionQualityMeta(
        visibilityPauseCount: Int = 0,
        cameraWarningCount: Int = 0,
        ingressFramesDropped: Int = 0,
    ): SessionQualityMeta? {
        val base = motionSession?.sessionQualityMeta(
            visibilityPauseCount = visibilityPauseCount,
            cameraWarningCount = cameraWarningCount,
        ) ?: return null
        if (ingressFramesDropped <= 0) return base
        return SessionQualityMeta.fromFrameStats(
            framesOffered = base.framesOffered + ingressFramesDropped,
            framesRecorded = base.framesRecorded,
            framesDropped = base.framesDropped + ingressFramesDropped,
            jointCoverageRatio = base.jointCoverageRatio,
            visibilityPauseCount = visibilityPauseCount,
            cameraWarningCount = cameraWarningCount,
        )
    }

    fun buildPostTrainingReport(
        upload: WorkoutUpload,
        summary: ExerciseWorkoutSummary,
        exerciseConfig: ExerciseConfig,
        sessionQuality: SessionQualityMeta? = null,
        holdData: MovitHoldReportData? = null,
    ): MovitPostTrainingReport = MovitPostTrainingReportBuilder.build(
        upload = upload,
        summary = resolveSummaryForReport(summary, upload),
        exerciseConfig = exerciseConfig,
        exerciseSlug = exerciseSlug,
        sessionQuality = sessionQuality ?: motionSession?.sessionQualityMeta(),
        peakFrameCaptures = peakFrameCaptures,
        repReplayClips = repReplayClips,
        holdData = holdData,
    )

    private fun resolveSummaryForReport(
        summary: ExerciseWorkoutSummary,
        upload: WorkoutUpload,
    ): ExerciseWorkoutSummary {
        val weight = summary.weightKg ?: upload.weightKg ?: defaultWeightKg
        if (weight == null || weight <= 0f) return summary
        return summary.copy(
            weightKg = weight,
            weightUnit = when {
                summary.weightKg != null -> summary.weightUnit
                upload.weightKg != null -> upload.weightUnit
                else -> weightUnit
            },
        )
    }

    suspend fun enqueueExecutionUpload(
        upload: WorkoutUpload,
        scope: CoroutineScope,
        context: String? = null,
        workoutGroupId: String? = null,
        workoutTemplateId: String? = null,
        legacyReport: MovitPostTrainingReport? = null,
        onEnqueued: (String) -> Unit = {},
        onOutcome: (AppResult<String>) -> Unit = {},
    ) {
        scope.launch {
            val result = writes.uploadWorkoutExecution(
                upload = upload,
                context = context,
                workoutGroupId = workoutGroupId,
                workoutTemplateId = workoutTemplateId,
                legacyReport = legacyReport?.let(writes::encodePostTrainingReport),
            )
            onOutcome(result)
            when (result) {
                is AppResult.Success -> onEnqueued(result.value)
                is AppResult.Failure -> Unit
            }
        }
    }

    fun finalizeExercise(upload: WorkoutUpload) {
        writes.finalizeJournal(sessionId)
    }

    fun finalizeUpload(endTimestampMs: Long = timeProvider()): WorkoutUpload? =
        motionSession?.finalize(endTimestampMs = endTimestampMs)

    fun buildSessionReport(
        upload: WorkoutUpload,
        summary: ExerciseWorkoutSummary,
        exerciseConfig: ExerciseConfig,
    ): MovitSessionReport = MovitSessionReportBuilder.fromExerciseExecution(
        upload = upload,
        summary = summary,
        exerciseSlug = exerciseSlug,
        exerciseName = exerciseConfig.name,
    )

    fun buildAssessmentResult(
        summary: ExerciseWorkoutSummary,
        upload: WorkoutUpload?,
    ): AssessmentTrainingResult = MovitSessionReportBuilder.toAssessmentResult(
        sessionId = sessionId,
        exerciseSlug = exerciseSlug,
        summary = summary,
        upload = upload,
    )

    suspend fun startPlannedWorkout(
        workoutId: String,
        programId: String?,
        weekNumber: Int,
        dayNumber: Int,
    ): AppResult<String> = writes.startPlannedWorkout(
        workoutId = workoutId,
        programId = programId,
        weekNumber = weekNumber,
        dayNumber = dayNumber,
        startedAt = timeProvider(),
    )

    suspend fun completePlannedDay(
        workoutId: String,
        report: MovitSessionReport,
        programId: String?,
        weekNumber: Int,
        dayNumber: Int,
        rpe: Int? = null,
        onOutcome: (AppResult<String>) -> Unit = {},
    ): AppResult<String> {
        val request = writes.buildPlannedCompleteRequest(
            report = report,
            completedAt = timeProvider(),
            rpe = rpe,
        )
        val result = writes.completePlannedWorkout(
            workoutId = workoutId,
            request = request,
            programId = programId,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
        )
        onOutcome(result)
        return result
    }

    /**
     * Legacy `/report` partial update — do not pair with [completePlannedDay] on the same session.
     * See [TrainingSessionPlannedWritePolicy].
     */
    suspend fun reportPlannedDay(
        workoutId: String,
        report: MovitSessionReport,
        programId: String?,
        weekNumber: Int,
        dayNumber: Int,
        rpe: Int? = null,
    ): AppResult<String> {
        val request = writes.buildPlannedCompleteRequest(
            report = report,
            completedAt = timeProvider(),
            rpe = rpe,
        )
        return writes.reportPlannedWorkout(
            workoutId = workoutId,
            request = request,
            programId = programId,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
        )
    }
}
