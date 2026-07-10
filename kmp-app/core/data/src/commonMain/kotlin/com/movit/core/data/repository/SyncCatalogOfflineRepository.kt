package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.sync.SyncCatalogGraphReport
import com.movit.core.data.sync.SyncCatalogGraphValidator
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.MobileSyncDataDto
import com.movit.core.network.dto.ProgramExportDto
import com.movit.core.network.dto.WorkoutExportDto
import com.movit.core.network.dto.WorkoutTemplateTrainingConfigDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

/**
 * Persists full program and workout-template exports from `/api/mobile/sync`
 * for offline program detail and standalone workout sessions.
 */
class SyncCatalogOfflineRepository(
    private val localStore: MovitLocalStore,
    private val trainingConfig: TrainingConfigRepository,
) {
    fun readProgram(programId: String): ProgramExportDto? {
        val key = resolveProgramKey(programId) ?: return null
        return MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.PROGRAM_STORE,
            key,
            ProgramExportDto.serializer(),
        )
    }

    fun readWorkoutExport(templateId: String): WorkoutExportDto? {
        val key = resolveWorkoutKey(templateId) ?: return null
        return MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.WORKOUT_TEMPLATE_STORE,
            key,
            WorkoutExportDto.serializer(),
        )
    }

    fun readWorkoutTrainingConfig(templateId: String): WorkoutTemplateTrainingConfigDto? {
        readWorkoutExport(templateId)?.let { return WorkoutExportMapper.toTrainingConfig(it) }
        // Lazy cleanup of legacy derived keys written before P2.12.
        val legacyKey = MovitCacheKeys.workoutTemplateTrainingConfigKey(templateId)
        val legacy = MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.SESSION_STORE,
            legacyKey,
            WorkoutTemplateTrainingConfigDto.serializer(),
        )
        if (legacy != null) {
            localStore.removeJsonCache(MovitCacheKeys.SESSION_STORE, legacyKey)
        }
        return legacy
    }

    fun allProgramIds(): List<String> = readProgramIndex()

    fun allWorkoutTemplateIds(): List<String> = readWorkoutIndex()

    fun applyFromSync(
        payload: MobileSyncDataDto,
        isFullSync: Boolean,
    ): SyncCatalogGraphReport {
        if (isFullSync) {
            clearProgramStore()
            clearWorkoutStore()
        }

        payload.deletedProgramIds.forEach { removeProgram(it) }
        payload.deletedWorkoutTemplateIds.forEach { removeWorkoutTemplate(it) }

        val programs = parsePrograms(payload.programs)
        val workouts = parseWorkouts(payload.workoutTemplates)

        if (programs.isNotEmpty() || isFullSync) {
            val index = if (isFullSync) mutableSetOf() else readProgramIndex().toMutableSet()
            programs.forEach { program ->
                if (program.id.isBlank()) return@forEach
                MovitCachePolicy.writeJson(
                    localStore,
                    MovitCacheKeys.PROGRAM_STORE,
                    MovitCacheKeys.programKey(program.id),
                    program,
                    ProgramExportDto.serializer(),
                )
                index += program.id
            }
            writeProgramIndex(index.toList())
        }

        if (workouts.isNotEmpty() || isFullSync) {
            val index = if (isFullSync) mutableSetOf() else readWorkoutIndex().toMutableSet()
            workouts.forEach { workout ->
                if (workout.id.isBlank()) return@forEach
                MovitCachePolicy.writeJson(
                    localStore,
                    MovitCacheKeys.WORKOUT_TEMPLATE_STORE,
                    MovitCacheKeys.workoutTemplateExportKey(workout.id),
                    workout,
                    WorkoutExportDto.serializer(),
                )
                // P2.12: do not persist derived training-config — derive on read via readWorkoutTrainingConfig.
                index += workout.id
            }
            writeWorkoutIndex(index.toList())
        }

        return SyncCatalogGraphValidator.validate(
            programs = readAllPrograms(),
            workoutTemplates = readAllWorkouts(),
            trainingConfig = trainingConfig,
        )
    }

    private fun readAllPrograms(): List<ProgramExportDto> =
        readProgramIndex().mapNotNull { readProgram(it) }

    private fun readAllWorkouts(): List<WorkoutExportDto> =
        readWorkoutIndex().mapNotNull { readWorkoutExport(it) }

    private fun parsePrograms(elements: List<JsonElement>): List<ProgramExportDto> =
        elements.mapNotNull { element ->
            runCatching {
                MovitJson.decodeFromJsonElement(ProgramExportDto.serializer(), element)
            }.getOrNull()
        }

    private fun parseWorkouts(elements: List<JsonElement>): List<WorkoutExportDto> =
        elements.mapNotNull { element ->
            runCatching {
                MovitJson.decodeFromJsonElement(WorkoutExportDto.serializer(), element)
            }.getOrNull()
        }

    private fun resolveProgramKey(programId: String): String? {
        if (programId.isBlank()) return null
        if (localStore.readJsonCache(
                MovitCacheKeys.PROGRAM_STORE,
                MovitCacheKeys.programKey(programId),
            ) != null
        ) {
            return MovitCacheKeys.programKey(programId)
        }
        return readProgramIndex()
            .firstOrNull { id ->
                readProgram(id)?.slug == programId
            }
            ?.let(MovitCacheKeys::programKey)
    }

    private fun resolveWorkoutKey(templateId: String): String? {
        if (templateId.isBlank()) return null
        if (localStore.readJsonCache(
                MovitCacheKeys.WORKOUT_TEMPLATE_STORE,
                MovitCacheKeys.workoutTemplateExportKey(templateId),
            ) != null
        ) {
            return MovitCacheKeys.workoutTemplateExportKey(templateId)
        }
        return readWorkoutIndex()
            .firstOrNull { id ->
                readWorkoutExport(id)?.slug == templateId
            }
            ?.let(MovitCacheKeys::workoutTemplateExportKey)
    }

    private fun removeProgram(programId: String) {
        localStore.removeJsonCache(MovitCacheKeys.PROGRAM_STORE, MovitCacheKeys.programKey(programId))
        writeProgramIndex(readProgramIndex().filterNot { it == programId })
    }

    private fun removeWorkoutTemplate(templateId: String) {
        localStore.removeJsonCache(
            MovitCacheKeys.WORKOUT_TEMPLATE_STORE,
            MovitCacheKeys.workoutTemplateExportKey(templateId),
        )
        localStore.removeJsonCache(
            MovitCacheKeys.SESSION_STORE,
            MovitCacheKeys.workoutTemplateTrainingConfigKey(templateId),
        )
        writeWorkoutIndex(readWorkoutIndex().filterNot { it == templateId })
    }

    private fun clearProgramStore() {
        readProgramIndex().forEach { id ->
            localStore.removeJsonCache(MovitCacheKeys.PROGRAM_STORE, MovitCacheKeys.programKey(id))
        }
        writeProgramIndex(emptyList())
    }

    private fun clearWorkoutStore() {
        readWorkoutIndex().forEach { id ->
            localStore.removeJsonCache(
                MovitCacheKeys.WORKOUT_TEMPLATE_STORE,
                MovitCacheKeys.workoutTemplateExportKey(id),
            )
            localStore.removeJsonCache(
                MovitCacheKeys.SESSION_STORE,
                MovitCacheKeys.workoutTemplateTrainingConfigKey(id),
            )
        }
        writeWorkoutIndex(emptyList())
    }

    private fun readProgramIndex(): List<String> = readIdIndex(MovitCacheKeys.PROGRAM_ID_INDEX)

    private fun writeProgramIndex(ids: List<String>) = writeIdIndex(MovitCacheKeys.PROGRAM_ID_INDEX, ids)

    private fun readWorkoutIndex(): List<String> = readIdIndex(MovitCacheKeys.WORKOUT_TEMPLATE_ID_INDEX)

    private fun writeWorkoutIndex(ids: List<String>) = writeIdIndex(MovitCacheKeys.WORKOUT_TEMPLATE_ID_INDEX, ids)

    private fun readIdIndex(key: String): List<String> {
        val raw = localStore.readJsonCache(MovitCacheKeys.CATALOG_INDEX_STORE, key) ?: return emptyList()
        return runCatching {
            MovitJson.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun writeIdIndex(key: String, ids: List<String>) {
        localStore.writeJsonCache(
            MovitCacheKeys.CATALOG_INDEX_STORE,
            key,
            MovitJson.encodeToString(ListSerializer(String.serializer()), ids.distinct()),
        )
    }
}
