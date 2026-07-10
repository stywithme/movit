package com.movit.feature.training

import com.movit.core.data.repository.TrainingSessionWriteCoordinator
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.journal.SessionJournalSnapshot
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

/**
 * WS-8 hooks: motion journal checkpoint + offline-safe mobileWrites from the session VM.
 *
 * [readJournal] / [checkpointJournal] default to [writes]; tests may inject fakes without a coordinator.
 */
class TrainingSessionWriteHooks(
    private val sessionId: String,
    private val exerciseSlug: String,
    private val writes: TrainingSessionWriteCoordinator? = null,
    private val poseVariantIndex: Int = 0,
    private val isAssessmentMode: Boolean = false,
    private val defaultWeightKg: Float? = null,
    private val weightUnit: String = "kg",
    private val timeProvider: () -> Long = { 0L },
    private val readJournal: (String) -> SessionJournalSnapshot? = { id -> writes?.readJournal(id) },
    private val checkpointJournal: (SessionJournalSnapshot) -> Unit = { snap -> writes?.checkpointJournal(snap) },
) {
    private var motionSession: TrainingMotionSession? = null
    var peakFrameCaptures: List<MovitPeakFrameCapture> = emptyList()
    var repReplayClips: List<MovitRepReplayClip> = emptyList()

    /**
     * Attach motion journal. If a checkpoint exists → [TrainingMotionSession.restore] **without**
     * [TrainingMotionSession.start] (P1.5). Returns restored completed-rep count (0 when fresh).
     */
    fun attach(engine: MovitTrainingEngine, exerciseConfig: ExerciseConfig): Int {
        motionSession?.let { return it.completedRepCount() }
        val session = TrainingMotionSession(
            exerciseConfig = exerciseConfig,
            exerciseSlug = exerciseSlug,
            poseVariantIndex = poseVariantIndex,
            defaultWeightKg = defaultWeightKg,
            weightUnit = weightUnit,
            sessionId = sessionId,
            isAssessmentMode = isAssessmentMode,
            timeProvider = timeProvider,
            onCheckpoint = checkpointJournal,
        )
        session.attach(engine)
        val journal = readJournal(sessionId)
        val restored = if (journal != null) {
            session.restore(journal)
            session.checkpoint()
            journal.completedRepMetrics.size
        } else {
            session.start(timeProvider())
            0
        }
        motionSession = session
        return restored
    }

    fun detach() {
        motionSession?.detach()
        motionSession = null
    }

    fun discardCurrentRepAttempt() {
        motionSession?.discardCurrentRepAttempt()
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
        setNumber: Int = 1,
        repsTarget: Int? = null,
    ): MovitPostTrainingReport = MovitPostTrainingReportBuilder.build(
        upload = upload,
        summary = resolveSummaryForReport(summary, upload),
        exerciseConfig = exerciseConfig,
        exerciseSlug = exerciseSlug,
        sessionQuality = sessionQuality ?: motionSession?.sessionQualityMeta(),
        peakFrameCaptures = peakFrameCaptures,
        repReplayClips = repReplayClips,
        holdData = holdData,
        setNumber = setNumber,
        repsTarget = repsTarget ?: summary.totalReps,
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
        context: String? = null,
        workoutGroupId: String? = null,
        workoutTemplateId: String? = null,
        legacyReport: MovitPostTrainingReport? = null,
    ): AppResult<String> {
        val coordinator = requireWrites()
        return coordinator.uploadWorkoutExecution(
            upload = upload,
            context = context,
            workoutGroupId = workoutGroupId,
            workoutTemplateId = workoutTemplateId,
            legacyReport = legacyReport?.let(coordinator::encodePostTrainingReport),
            operationId = upload.id,
        )
    }

    fun finalizeExercise(upload: WorkoutUpload) {
        requireWrites().finalizeJournal(sessionId)
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
    ): AppResult<String> = requireWrites().startPlannedWorkout(
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
        workoutGroupId: String? = null,
        onOutcome: (AppResult<String>) -> Unit = {},
    ): AppResult<String> {
        val coordinator = requireWrites()
        val request = coordinator.buildPlannedCompleteRequest(
            report = report,
            completedAt = timeProvider(),
            rpe = rpe,
        )
        val result = coordinator.completePlannedWorkout(
            workoutId = workoutId,
            request = request,
            programId = programId,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            workoutGroupId = workoutGroupId,
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
        workoutGroupId: String? = null,
    ): AppResult<String> {
        val coordinator = requireWrites()
        val request = coordinator.buildPlannedCompleteRequest(
            report = report,
            completedAt = timeProvider(),
            rpe = rpe,
        )
        return coordinator.reportPlannedWorkout(
            workoutId = workoutId,
            request = request,
            programId = programId,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            workoutGroupId = workoutGroupId,
        )
    }

    private fun requireWrites(): TrainingSessionWriteCoordinator =
        requireNotNull(writes) { "TrainingSessionWriteCoordinator required for write paths" }
}
