package com.movit.feature.training

import com.movit.core.data.repository.TrainingSessionWriteCoordinator
import com.movit.core.training.journal.WorkoutUpload
import com.movit.shared.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * Explore / multi-exercise workout uploads share one workoutGroupId (legacy explore batch).
 */
class WorkoutExecutionBatchCoordinator(
    private val writes: TrainingSessionWriteCoordinator,
    private val context: String = EXPLORE_WORKOUT_CONTEXT,
    private val onWriteOutcome: (AppResult<String>, TrainingSessionWriteDiagnostics.WriteKind) -> Unit = { _, _ -> },
) {
    private data class BatchEntry(
        val upload: WorkoutUpload,
        val legacyReport: JsonElement? = null,
    )

    private val pending = mutableListOf<BatchEntry>()

    fun record(upload: WorkoutUpload, legacyReport: JsonElement? = null) {
        pending += BatchEntry(upload, legacyReport)
    }

    fun pendingCount(): Int = pending.size

    fun flush(
        scope: CoroutineScope,
        workoutGroupId: String,
        workoutTemplateId: String? = null,
        onEachEnqueued: (uploadId: String, reportId: String) -> Unit = { _, _ -> },
        onComplete: (Int, Int) -> Unit = { _, _ -> },
    ) {
        scope.launch {
            flushAwait(
                workoutGroupId = workoutGroupId,
                workoutTemplateId = workoutTemplateId,
                onEachEnqueued = onEachEnqueued,
                onComplete = onComplete,
            )
        }
    }

    suspend fun flushAwait(
        workoutGroupId: String,
        workoutTemplateId: String? = null,
        onEachEnqueued: (uploadId: String, reportId: String) -> Unit = { _, _ -> },
        onComplete: (Int, Int) -> Unit = { _, _ -> },
    ): Pair<Int, Int> {
        val batch = pending.toList()
        pending.clear()
        var enqueued = 0
        var failed = 0
        for (entry in batch) {
            when (
                val result = writes.uploadWorkoutExecution(
                    upload = entry.upload,
                    context = context,
                    workoutGroupId = workoutGroupId,
                    workoutTemplateId = workoutTemplateId,
                    legacyReport = entry.legacyReport,
                )
            ) {
                is AppResult.Success -> {
                    enqueued++
                    onWriteOutcome(result, TrainingSessionWriteDiagnostics.WriteKind.EXECUTION_UPLOAD)
                    onEachEnqueued(entry.upload.id, result.value)
                }
                is AppResult.Failure -> {
                    failed++
                    onWriteOutcome(result, TrainingSessionWriteDiagnostics.WriteKind.EXECUTION_UPLOAD)
                }
            }
        }
        onComplete(enqueued, failed)
        return enqueued to failed
    }

    companion object {
        const val EXPLORE_WORKOUT_CONTEXT = "explore_workout"
    }
}
