package com.movit.core.data.repository

import com.movit.core.network.dto.SubstitutionExerciseDto
import com.movit.core.training.config.ExerciseConfigRecord

/**
 * Local mirror of the backend `exerciseSubstitutionsService` ranking, so swapping an exercise
 * works with no network — the gym case, where the swap sheet used to render empty.
 *
 * Order matches the server:
 * 1. same `familyKey`, sorted by `familyOrder`
 * 2. otherwise same `movementPattern` + `archetype` (excluding the original family), capped at 6
 */
internal object OfflineSubstitutionResolver {
    const val FALLBACK_LIMIT = 6

    fun resolve(
        replacing: ExerciseConfigRecord,
        catalog: List<ExerciseConfigRecord>,
    ): List<SubstitutionExerciseDto> {
        val source = replacing.config
        val others = catalog.filter { it.slug != replacing.slug && it.slug.isNotBlank() }

        val familyKey = source.familyKey?.takeIf { it.isNotBlank() }
        if (familyKey != null) {
            val family = others
                .filter { it.config.familyKey == familyKey }
                .sortedWith(compareBy({ it.config.familyOrder ?: Int.MAX_VALUE }, { it.slug }))
            if (family.isNotEmpty()) return family.map(::toDto)
        }

        val movementPattern = source.movementPattern?.takeIf { it.isNotBlank() }
        val archetype = source.archetype?.takeIf { it.isNotBlank() }
        if (movementPattern == null || archetype == null) return emptyList()

        return others
            .filter { it.config.movementPattern == movementPattern && it.config.archetype == archetype }
            .filter { familyKey == null || it.config.familyKey != familyKey }
            .sortedWith(compareBy({ it.config.familyOrder ?: Int.MAX_VALUE }, { it.slug }))
            .take(FALLBACK_LIMIT)
            .map(::toDto)
    }

    private fun toDto(record: ExerciseConfigRecord): SubstitutionExerciseDto =
        SubstitutionExerciseDto(
            id = record.id.ifBlank { record.slug },
            slug = record.slug,
            name = mapOf(
                "en" to record.config.name.en,
                "ar" to record.config.name.ar,
            ),
            archetype = record.config.archetype,
        )
}
