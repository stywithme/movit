package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.outbox.DayCustomizationCacheDto
import com.movit.core.data.outbox.DayCustomizationDayKey
import com.movit.core.data.outbox.OutboxPendingScan
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
 * Ported from legacy [com.movit.storage.DayCustomizationStore].
 */
open class DayCustomizationLocalStore(
    private val localStore: MovitLocalStore,
    private val keyResolver: DayCustomizationKeyResolver = DayCustomizationKeyResolver(
        UserProgramEnrollmentLocalStore(localStore),
    ),
) {
    fun get(
        userProgramId: String,
        weekNumber: Int,
        dayNumber: Int,
    ): DayCustomizationCacheDto? {
        val canonicalId = keyResolver.resolveCanonicalUserProgramId(userProgramId)
        readCustomization(canonicalId, weekNumber, dayNumber)?.let { return it }

        val programId = keyResolver.programIdForUserProgram(canonicalId)
            ?.takeIf { it != canonicalId }
        if (programId != null) {
            readCustomization(programId, weekNumber, dayNumber)?.let { return it }
        }
        return if (canonicalId != userProgramId) {
            readCustomization(userProgramId, weekNumber, dayNumber)
        } else {
            null
        }
    }

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
        val canonicalId = keyResolver.resolveCanonicalUserProgramId(userProgramId)
        save(
            DayCustomizationCacheDto(
                userProgramId = canonicalId,
                weekNumber = weekNumber,
                dayNumber = dayNumber,
                plannedWorkouts = plannedWorkouts,
                lastModifiedAt = MovitClock.nowEpochMs(),
                isUserModified = true,
            ),
        )
    }

    fun markServerAcknowledged(
        userProgramId: String,
        weekNumber: Int,
        dayNumber: Int,
    ) {
        val existing = get(userProgramId, weekNumber, dayNumber) ?: return
        save(existing.copy(isUserModified = false))
    }

    open suspend fun hydrateFromBackend(
        userProgramId: String,
        customizations: JsonElement?,
        serverCustomizationsUpdatedAt: String? = null,
        pendingDayKeys: Set<DayCustomizationDayKey>? = null,
    ) {
        val dayMap = parseBackendCustomizations(customizations)
        if (dayMap.isEmpty()) return

        val serverMs = serverCustomizationsUpdatedAt?.let(::parseIsoToEpochMs)
        val pending = pendingDayKeys ?: pendingDayKeysFromOutbox(localStore)

        for ((dayKey, plannedWorkouts) in dayMap) {
            val parts = dayKey.removePrefix("day_").split("_")
            if (parts.size != 2) continue
            val weekNumber = parts[0].toIntOrNull() ?: continue
            val dayNumber = parts[1].toIntOrNull() ?: continue

            val dayKeyTriple = DayCustomizationDayKey(userProgramId, weekNumber, dayNumber)
            if (dayKeyTriple in pending) continue

            val existing = get(userProgramId, weekNumber, dayNumber)
            if (existing != null && serverMs != null && serverMs < existing.lastModifiedAt) {
                continue
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

    private fun readCustomization(
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

    companion object {
        suspend fun pendingDayKeysFromOutbox(localStore: MovitLocalStore): Set<DayCustomizationDayKey> =
            OutboxPendingScan.pendingDayCustomizationKeys(OutboxPendingScan.awaitingDispatch(localStore))

        fun formatEpochMsToIsoUtc(epochMs: Long): String {
            var rem = epochMs
            val millis = (rem % 1_000).toInt().also { rem /= 1_000 }
            val second = (rem % 60).toInt().also { rem /= 60 }
            val minute = (rem % 60).toInt().also { rem /= 60 }
            val hour = (rem % 24).toInt().also { rem /= 24 }
            val (year, month, day) = epochDayToYmd(rem)
            return buildString {
                append(year.toString().padStart(4, '0'))
                append('-')
                append(month.toString().padStart(2, '0'))
                append('-')
                append(day.toString().padStart(2, '0'))
                append('T')
                append(hour.toString().padStart(2, '0'))
                append(':')
                append(minute.toString().padStart(2, '0'))
                append(':')
                append(second.toString().padStart(2, '0'))
                append('.')
                append(millis.toString().padStart(3, '0'))
                append('Z')
            }
        }

        private fun epochDayToYmd(epochDay: Long): Triple<Int, Int, Int> {
            var z = epochDay + 719_468L
            val era = if (z >= 0L) z / 146_097L else (z - 146_096L) / 146_097L
            val doe = z - era * 146_097L
            val yoe = (doe - doe / 1_460L + doe / 36_524L - doe / 146_096L) / 365L
            var y = (yoe + era * 400L).toInt()
            val doy = (doe - (365L * yoe + yoe / 4L - yoe / 100L)).toInt()
            val mp = (5 * doy + 2) / 153
            val d = doy - (153 * mp + 2) / 5 + 1
            val m = mp + if (mp < 10) 3 else -9
            if (mp < 10) y -= 1
            return Triple(y, m, d)
        }

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
            if (trimmed.length < 10 || trimmed[4] != '-' || trimmed[7] != '-') return null
            val year = trimmed.substring(0, 4).toIntOrNull() ?: return null
            val month = trimmed.substring(5, 7).toIntOrNull() ?: return null
            val day = trimmed.substring(8, 10).toIntOrNull() ?: return null
            var hour = 0
            var minute = 0
            var second = 0
            var millis = 0
            var offsetMs = 0L
            if (trimmed.length >= 19 && trimmed[10] == 'T') {
                hour = trimmed.substring(11, 13).toIntOrNull() ?: 0
                minute = trimmed.substring(14, 16).toIntOrNull() ?: 0
                second = trimmed.substring(17, 19).toIntOrNull() ?: 0
                var cursor = 19
                if (trimmed.length > cursor && trimmed[cursor] == '.') {
                    val fracStart = cursor + 1
                    var fracEnd = fracStart
                    while (fracEnd < trimmed.length && trimmed[fracEnd].isDigit()) fracEnd++
                    val frac = trimmed.substring(fracStart, fracEnd)
                    millis = frac.take(3).padEnd(3, '0').toIntOrNull() ?: 0
                    cursor = fracEnd
                }
                when {
                    cursor >= trimmed.length || trimmed[cursor] == 'Z' -> offsetMs = 0L
                    trimmed[cursor] == '+' || trimmed[cursor] == '-' -> {
                        val sign = if (trimmed[cursor] == '-') -1 else 1
                        val tz = trimmed.substring(cursor + 1)
                        val tzHour: Int
                        val tzMinute: Int
                        when {
                            tz.length >= 5 && tz[2] == ':' -> {
                                tzHour = tz.substring(0, 2).toIntOrNull() ?: return null
                                tzMinute = tz.substring(3, 5).toIntOrNull() ?: return null
                            }
                            tz.length >= 4 && tz[2] != ':' -> {
                                tzHour = tz.substring(0, 2).toIntOrNull() ?: return null
                                tzMinute = tz.substring(2, 4).toIntOrNull() ?: return null
                            }
                            tz.length >= 2 -> {
                                tzHour = tz.substring(0, 2).toIntOrNull() ?: return null
                                tzMinute = 0
                            }
                            else -> return null
                        }
                        offsetMs = sign * (tzHour * 3_600_000L + tzMinute * 60_000L)
                    }
                }
            }
            // Local wall time minus offset → UTC epoch (P3.2).
            return utcEpochMs(year, month, day, hour, minute, second, millis) - offsetMs
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
