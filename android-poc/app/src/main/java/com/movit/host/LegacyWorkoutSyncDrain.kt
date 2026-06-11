package com.movit.host

import android.content.Context
import android.util.Log
import com.movit.core.data.MovitData
import com.movit.core.training.journal.RepMetrics
import com.movit.core.training.journal.RepMetricsData
import com.movit.core.training.journal.WorkoutExecutionMetrics
import com.movit.core.training.journal.WorkoutUpload
import com.movit.shared.AppResult
import com.trainingvalidator.poc.storage.AnalyticsStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.trainingvalidator.poc.storage.WorkoutUpload as LegacyWorkoutUpload

/**
 * WS-10 — moves pending legacy [AnalyticsStorage] workout executions into the KMP Outbox
 * after legacy OkHttp sync was removed (WS-10).
 */
object LegacyWorkoutSyncDrain {
    private const val TAG = "LegacyWorkoutSyncDrain"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun drainPendingExecutions(context: Context) {
        if (!MovitData.isInstalled) return
        scope.launch {
            val storage = AnalyticsStorage(context.applicationContext)
            val pending = storage.getAllPending()
            if (pending.isEmpty()) return@launch
            Log.i(TAG, "Draining ${pending.size} legacy workout execution(s) into Outbox")
            val coordinator = MovitData.trainingWrites
            for (legacy in pending) {
                val upload = legacy.toKmpUpload()
                when (val result = coordinator.uploadWorkoutExecution(upload)) {
                    is AppResult.Success -> storage.deletePending(legacy.id)
                    is AppResult.Failure -> Log.w(TAG, "Outbox enqueue failed for ${legacy.id}: ${result.message}")
                }
            }
        }
    }

    private fun LegacyWorkoutUpload.toKmpUpload(): WorkoutUpload = WorkoutUpload(
        id = id,
        exerciseId = exerciseId,
        timestamp = timestamp,
        durationMs = durationMs,
        totalReps = totalReps,
        countedReps = countedReps,
        invalidReps = invalidReps,
        weightKg = weightKg,
        weightUnit = weightUnit,
        repMetrics = repMetrics.map { rep ->
            RepMetricsData(
                num = rep.num,
                durationMs = rep.durationMs,
                worstState = rep.worstState,
                score = rep.score,
                weightKg = rep.weightKg,
                side = rep.side,
                metrics = RepMetrics(
                    rom = rep.metrics.rom,
                    symmetry = rep.metrics.symmetry,
                    stability = rep.metrics.stability,
                    tempo = rep.metrics.tempo.toList(),
                    velocity = rep.metrics.velocity,
                    formScore = rep.metrics.formScore,
                    alignmentAccuracy = rep.metrics.alignmentAccuracy,
                    velocityLoss = rep.metrics.velocityLoss,
                ),
            )
        },
        executionMetrics = WorkoutExecutionMetrics(
            avgRom = executionMetrics.avgRom,
            avgSymmetry = executionMetrics.avgSymmetry,
            avgStability = executionMetrics.avgStability,
            avgTempo = executionMetrics.avgTempo.toList(),
            avgVelocity = executionMetrics.avgVelocity,
            avgFormScore = executionMetrics.avgFormScore,
            avgAlignmentAccuracy = executionMetrics.avgAlignmentAccuracy,
            totalTUT = executionMetrics.totalTUT,
            totalVolume = executionMetrics.totalVolume,
            maxWeight = executionMetrics.maxWeight,
            est1RM = executionMetrics.est1RM,
            formConsistency = executionMetrics.formConsistency,
            fatigueIndex = executionMetrics.fatigueIndex,
            velocityLoss = executionMetrics.velocityLoss,
            tempoConsistency = executionMetrics.tempoConsistency,
        ),
    )
}
