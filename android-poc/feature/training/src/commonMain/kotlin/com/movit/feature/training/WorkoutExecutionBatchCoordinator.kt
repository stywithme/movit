package com.movit.feature.training

import com.movit.core.data.repository.TrainingSessionWriteCoordinator
import com.movit.core.training.journal.WorkoutUpload
import com.movit.shared.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Explore / multi-exercise workout uploads share one workoutGroupId (legacy explore batch).
 */
class WorkoutExecutionBatchCoordinator(
    private val writes: TrainingSessionWriteCoordinator,
    private val context: String = EXPLORE_WORKOUT_CONTEXT,
) {
    private val pending = mutableListOf<WorkoutUpload>()

    fun record(upload: WorkoutUpload) {
        pending += upload
    }

    fun pendingCount(): Int = pending.size

    fun flush(
        scope: CoroutineScope,
        workoutGroupId: String,
        workoutTemplateId: String? = null,
        onEachEnqueued: (String) -> Unit = {},
        onComplete: (Int) -> Unit = {},
    ) {
        val batch = pending.toList()
        pending.clear()
        scope.launch {
            var enqueued = 0
            for (upload in batch) {
                when (
                    val result = writes.uploadWorkoutExecution(
                        upload = upload,
                        context = context,
                        workoutGroupId = workoutGroupId,
                        workoutTemplateId = workoutTemplateId,
                    )
                ) {
                    is AppResult.Success -> {
                        enqueued++
                        onEachEnqueued(result.value)
                    }
                    is AppResult.Failure -> Unit
                }
            }
            onComplete(enqueued)
        }
    }

    companion object {
        const val EXPLORE_WORKOUT_CONTEXT = "explore_workout"
    }
}
