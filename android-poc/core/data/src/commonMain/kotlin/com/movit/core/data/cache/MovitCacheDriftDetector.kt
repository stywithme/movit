package com.movit.core.data.cache

import com.movit.core.network.dto.MessageLibraryStatsDto
import com.movit.core.network.dto.SyncMetaDto

/**
 * Port of legacy SyncManager drift detection for structured KMP caches.
 */
object MovitCacheDriftDetector {
    data class EntityCounts(
        val exercises: Int,
        val workouts: Int,
        val programs: Int,
    )

    data class MessageStatsSnapshot(
        val totalMessages: Int,
        val totalWithAudio: Int,
        val totalAssignments: Int,
        val fingerprint: String,
    ) {
        companion object {
            const val UNINITIALIZED = -1
        }
    }

    sealed class DriftVerdict {
        data object Ok : DriftVerdict()

        data object NeedsFullRefresh : DriftVerdict()

        data object MessageStatsMismatch : DriftVerdict()
    }

    fun detectEntityDrift(
        local: EntityCounts,
        meta: SyncMetaDto?,
        hasNoEntityDelta: Boolean,
    ): DriftVerdict {
        if (!hasNoEntityDelta || meta == null) return DriftVerdict.Ok

        val serverExercises = meta.totalExercises
        val serverWorkouts = meta.totalWorkoutTemplates
        val serverPrograms = meta.totalPrograms

        val exerciseOverflow = local.exercises > serverExercises
        val exerciseUnderflow = local.exercises < serverExercises
        val workoutOverflow = local.workouts > serverWorkouts
        val programOverflow = local.programs > serverPrograms
        val workoutUnderflow = local.workouts < serverWorkouts
        val programUnderflow = local.programs < serverPrograms

        return if (
            exerciseOverflow ||
            exerciseUnderflow ||
            workoutOverflow ||
            programOverflow ||
            workoutUnderflow ||
            programUnderflow
        ) {
            DriftVerdict.NeedsFullRefresh
        } else {
            DriftVerdict.Ok
        }
    }

    fun detectMessageStatsDrift(
        cached: MessageStatsSnapshot?,
        server: MessageLibraryStatsDto?,
    ): DriftVerdict {
        if (server == null) return DriftVerdict.Ok

        if (cached == null || cached.totalMessages == MessageStatsSnapshot.UNINITIALIZED) {
            val serverHasMessages = server.totalMessages > 0 || server.totalAssignments > 0
            return if (serverHasMessages) DriftVerdict.MessageStatsMismatch else DriftVerdict.Ok
        }

        val countMismatch =
            cached.totalMessages != server.totalMessages ||
                cached.totalWithAudio != server.totalWithAudio ||
                cached.totalAssignments != server.totalAssignments

        val fingerprintMismatch =
            server.fingerprint.isNotBlank() &&
                cached.fingerprint.isNotBlank() &&
                server.fingerprint != cached.fingerprint

        return if (countMismatch || fingerprintMismatch) {
            DriftVerdict.MessageStatsMismatch
        } else {
            DriftVerdict.Ok
        }
    }
}
