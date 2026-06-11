package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.outbox.DayCustomizationCacheDto
import com.movit.core.network.MovitClock
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.EffectivePlanApiResponse
import com.movit.core.network.dto.EffectivePlanPayloadDto
import com.movit.core.network.dto.EffectivePlannedWorkoutDto
import com.movit.core.network.dto.ProgramCustomizationKeys
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Offline-first day customization overrides on [MovitLocalStore].
 * Ported from legacy [com.trainingvalidator.poc.storage.DayCustomizationStore].
 */
class DayCustomizationLocalStore(
    private val localStore: MovitLocalStore,
) {
    fun get(
        userProgramId: String,
        weekNumber: Int,
        dayNumber: Int,
    ): DayCustomizationCacheDto? =
        MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.DAY_CUSTOMIZATION_STORE,
            MovitCacheKeys.dayCustomizationKey(userProgramId, weekNumber, dayNumber),
            DayCustomizationCacheDto.serializer(),
        )

    fun hasCustomization(userProgramId: String, weekNumber: Int, dayNumber: Int): Boolean =
        get(userProgramId, weekNumber, dayNumber) != null

    fun getEffectivePlannedWorkouts(
        userProgramId: String,
        weekNumber: Int,
        dayNumber: Int,
        originalWorkouts: List<EffectivePlannedWorkoutDto>,
    ): List<EffectivePlannedWorkoutDto> {
        val customization = get(userProgramId, weekNumber, dayNumber)
        if (customization != null) {
            return customization.plannedWorkouts
                .mapIndexed { wIdx, workout ->
                    workout.copy(
                        sortOrder = wIdx,
                        items = workout.items.mapIndexed { iIdx, item -> item.copy(sortOrder = iIdx) },
                    )
                }
        }
        return originalWorkouts
            .map { workout ->
                workout.copy(items = workout.items.sortedBy { it.sortOrder })
            }
            .sortedBy { it.sortOrder }
    }

    fun saveUserCustomizations(
        userProgramId: String,
        weekNumber: Int,
        dayNumber: Int,
        plannedWorkouts: List<EffectivePlannedWorkoutDto>,
    ) {
        save(
            DayCustomizationCacheDto(
                userProgramId = userProgramId,
                weekNumber = weekNumber,
                dayNumber = dayNumber,
                plannedWorkouts = plannedWorkouts,
                lastModifiedAt = MovitClock.nowEpochMs(),
                isUserModified = true,
            ),
        )
    }

    fun hydrateFromBackend(
        userProgramId: String,
        customizations: JsonElement?,
        serverCustomizationsUpdatedAt: String? = null,
    ) {
        val dayMap = parseBackendCustomizations(customizations)
        if (dayMap.isEmpty()) return

        val serverMs = serverCustomizationsUpdatedAt?.let(::parseIsoToEpochMs)

        for ((dayKey, plannedWorkouts) in dayMap) {
            val parts = dayKey.removePrefix("day_").split("_")
            if (parts.size != 2) continue
            val weekNumber = parts[0].toIntOrNull() ?: continue
            val dayNumber = parts[1].toIntOrNull() ?: continue

            if (hasCustomization(userProgramId, weekNumber, dayNumber)) {
                val existing = get(userProgramId, weekNumber, dayNumber) ?: continue
                if (existing.isUserModified) {
                    continue
                }
                if (serverMs != null && serverMs <= existing.lastModifiedAt) {
                    continue
                }
            }

            save(
                DayCustomizationCacheDto(
                    userProgramId = userProgramId,
                    weekNumber = weekNumber,
                    dayNumber = dayNumber,
                    plannedWorkouts = plannedWorkouts,
                    lastModifiedAt = serverMs ?: MovitClock.nowEpochMs(),
                    isUserModified = false,
                ),
            )
        }
    }

    private fun save(customization: DayCustomizationCacheDto) {
        MovitCachePolicy.writeJson(
            localStore,
            MovitCacheKeys.DAY_CUSTOMIZATION_STORE,
            MovitCacheKeys.dayCustomizationKey(
                customization.userProgramId,
                customization.weekNumber,
                customization.dayNumber,
            ),
            customization,
            DayCustomizationCacheDto.serializer(),
        )
    }

    companion object {
        fun parseBackendCustomizations(
            customizations: JsonElement?,
        ): Map<String, List<EffectivePlannedWorkoutDto>> {
            if (customizations == null) return emptyMap()
            return runCatching {
                when (customizations) {
                    is JsonObject -> customizations.jsonObject.mapValues { (_, value) ->
                        MovitJson.decodeFromJsonElement(
                            kotlinx.serialization.builtins.ListSerializer(EffectivePlannedWorkoutDto.serializer()),
                            value,
                        )
                    }
                    else -> MovitJson.decodeFromJsonElement(
                        kotlinx.serialization.serializer<Map<String, List<EffectivePlannedWorkoutDto>>>(),
                        customizations,
                    )
                }
            }.getOrElse { emptyMap() }
        }

        fun applyToEffectivePlanCache(
            localStore: MovitLocalStore,
            userProgramId: String,
            weekNumber: Int,
            dayNumber: Int,
            plannedWorkouts: List<EffectivePlannedWorkoutDto>,
        ) {
            val cacheKey = MovitCacheKeys.effectivePlanKey(userProgramId, weekNumber, dayNumber)
            val basePayload = MovitCachePolicy.readJson(
                localStore,
                MovitCacheKeys.SESSION_STORE,
                cacheKey,
                EffectivePlanApiResponse.serializer(),
            )?.data ?: EffectivePlanPayloadDto(
                userProgramId = userProgramId,
                weekNumber = weekNumber,
                dayNumber = dayNumber,
            )

            MovitCachePolicy.writeJson(
                localStore,
                MovitCacheKeys.SESSION_STORE,
                cacheKey,
                EffectivePlanApiResponse(
                    success = true,
                    data = basePayload.copy(plannedWorkouts = plannedWorkouts),
                ),
                EffectivePlanApiResponse.serializer(),
            )
        }

        fun parseIsoToEpochMs(iso: String): Long? {
            val trimmed = iso.trim()
            if (trimmed.length >= 10 && trimmed[4] == '-' && trimmed[7] == '-') {
                val year = trimmed.substring(0, 4).toIntOrNull() ?: return null
                val month = trimmed.substring(5, 7).toIntOrNull() ?: return null
                val day = trimmed.substring(8, 10).toIntOrNull() ?: return null
                var hour = 0
                var minute = 0
                var second = 0
                var millis = 0
                if (trimmed.length >= 19 && trimmed[10] == 'T') {
                    hour = trimmed.substring(11, 13).toIntOrNull() ?: 0
                    minute = trimmed.substring(14, 16).toIntOrNull() ?: 0
                    second = trimmed.substring(17, 19).toIntOrNull() ?: 0
                    if (trimmed.length >= 23 && trimmed[19] == '.') {
                        millis = trimmed.substring(20, 23).toIntOrNull() ?: 0
                    }
                }
                return utcEpochMs(year, month, day, hour, minute, second, millis)
            }
            return null
        }

        private fun utcEpochMs(
            year: Int,
            month: Int,
            day: Int,
            hour: Int,
            minute: Int,
            second: Int,
            millis: Int,
        ): Long {
            var y = year.toLong()
            var m = month.toLong()
            val d = day.toLong()
            if (m <= 2) {
                y -= 1
                m += 12
            }
            val era = if (y >= 0) 1L else 0L
            val yAdj = y + (era * 4800L) - 1L
            val dayNumber = (1461L * yAdj) / 4L +
                (367L * m) / 12L -
                (3L * ((yAdj + 100L) / 100L)) / 4L +
                d - 32075L
            val dayMs = dayNumber * 86_400_000L
            val timeMs = hour * 3_600_000L + minute * 60_000L + second * 1_000L + millis
            return (dayMs + timeMs) - era * 86_400_000L
        }
    }
}

internal fun mergeEffectivePlanWithDayOverrides(
    localStore: MovitLocalStore,
    payload: EffectivePlanPayloadDto,
): EffectivePlanPayloadDto {
    val overrides = DayCustomizationLocalStore(localStore)
    val merged = overrides.getEffectivePlannedWorkouts(
        userProgramId = payload.userProgramId,
        weekNumber = payload.weekNumber,
        dayNumber = payload.dayNumber,
        originalWorkouts = payload.plannedWorkouts,
    )
    return payload.copy(plannedWorkouts = merged)
}

internal fun dayCustomizationKeyFromUpdate(
    weekNumber: Int,
    dayNumber: Int,
    customizations: Map<String, List<EffectivePlannedWorkoutDto>>,
): List<EffectivePlannedWorkoutDto>? =
    customizations[ProgramCustomizationKeys.dayKey(weekNumber, dayNumber)]
