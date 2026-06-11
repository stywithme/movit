package com.movit.core.data.repository

import com.movit.core.data.journal.SessionJournalStore
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.PlannedWorkoutCompleteRequestDto
import com.movit.core.network.dto.PlannedWorkoutStartRequestDto
import com.movit.core.training.journal.SessionJournalSnapshot
import com.movit.core.training.journal.WorkoutUpload
import com.movit.core.training.report.MovitPostTrainingReport
import com.movit.core.training.report.MovitSessionReport
import com.movit.core.training.report.PostTrainingReportLegacyJson
import com.movit.shared.AppResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Offline-safe training session writes (WS-8): journal checkpoint + mobileWrites outbox.
 */
class TrainingSessionWriteCoordinator(
    private val mobileWrites: MobileWriteSyncRepository,
    private val reportsSync: ReportsSyncRepository,
    private val journalStore: SessionJournalStore,
) {
    suspend fun startPlannedWorkout(
        workoutId: String,
        programId: String?,
        weekNumber: Int,
        dayNumber: Int,
        startedAt: Long? = null,
        operationId: String? = null,
    ): AppResult<String> = mobileWrites.startPlannedWorkout(
        workoutId = workoutId,
        request = PlannedWorkoutStartRequestDto(
            programId = programId,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            startedAt = startedAt,
        ),
        operationId = operationId,
    )

    suspend fun uploadWorkoutExecution(
        upload: WorkoutUpload,
        context: String? = null,
        workoutGroupId: String? = null,
        workoutTemplateId: String? = null,
        legacyReport: JsonElement? = null,
        operationId: String? = null,
    ): AppResult<String> {
        val request = WorkoutUploadMapper.toUploadRequest(
            upload = upload,
            context = context,
            workoutGroupId = workoutGroupId,
            workoutTemplateId = workoutTemplateId,
            legacyReport = legacyReport,
        )
        reportsSync.patchExerciseMetricsFromUpload(request)
        return mobileWrites.uploadWorkoutExecution(request, operationId = operationId)
    }

    suspend fun completePlannedWorkout(
        workoutId: String,
        request: PlannedWorkoutCompleteRequestDto,
        programId: String?,
        weekNumber: Int = 0,
        dayNumber: Int = 0,
        operationId: String? = null,
    ): AppResult<String> {
        reportsSync.recordPendingPlannedWorkoutCompletion(
            workoutId = workoutId,
            request = request,
            programId = programId,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
        )
        return mobileWrites.completePlannedWorkout(workoutId, request, operationId)
    }

    suspend fun reportPlannedWorkout(
        workoutId: String,
        request: PlannedWorkoutCompleteRequestDto,
        programId: String?,
        weekNumber: Int = 0,
        dayNumber: Int = 0,
        operationId: String? = null,
    ): AppResult<String> {
        reportsSync.recordPendingPlannedWorkoutCompletion(
            workoutId = workoutId,
            request = request,
            programId = programId,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
        )
        return mobileWrites.reportPlannedWorkout(workoutId, request, operationId)
    }

    fun checkpointJournal(snapshot: SessionJournalSnapshot) {
        journalStore.saveCheckpoint(snapshot)
    }

    fun readJournal(sessionId: String): SessionJournalSnapshot? = journalStore.readCheckpoint(sessionId)

    fun finalizeJournal(sessionId: String) {
        journalStore.markCompleted(sessionId)
    }

    fun encodeSessionReport(report: MovitSessionReport): JsonElement =
        MovitJson.encodeToJsonElement(MovitSessionReport.serializer(), report)

    fun encodePostTrainingReport(report: MovitPostTrainingReport): JsonElement =
        PostTrainingReportLegacyJson.encode(report)

    fun buildPlannedCompleteRequest(
        report: MovitSessionReport,
        completedAt: Long? = null,
        rpe: Int? = null,
    ): PlannedWorkoutCompleteRequestDto = PlannedWorkoutCompleteRequestDto(
        completedAt = completedAt,
        totalDurationMs = report.totalDurationMs.toInt(),
        totalExercises = report.totalExercises,
        totalSets = report.totalSetsPlanned,
        completedSets = report.totalSetsCompleted,
        totalReps = report.totalReps,
        avgAccuracy = report.averageAccuracy,
        avgFormScore = report.averageFormScore,
        rpe = rpe,
        report = encodeSessionReport(report),
    )
}
