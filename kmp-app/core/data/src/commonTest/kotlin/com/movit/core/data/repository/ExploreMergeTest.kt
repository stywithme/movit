package com.movit.core.data.repository

import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreExerciseDto
import com.movit.core.network.dto.LocalizedNameDto
import kotlin.test.Test
import kotlin.test.assertEquals

class ExploreMergeTest {

    @Test
    fun incrementalMerge_replacesUpdatedExercise() {
        val old = ExploreDataDto(
            exercises = listOf(
                ExploreExerciseDto(
                    id = "1",
                    slug = "squat",
                    name = LocalizedNameDto(en = "Squat"),
                    updatedAt = "2026-01-01",
                ),
            ),
        )
        val incoming = ExploreDataDto(
            exercises = listOf(
                ExploreExerciseDto(
                    id = "1",
                    slug = "squat",
                    name = LocalizedNameDto(en = "Barbell Squat"),
                    updatedAt = "2026-06-01",
                ),
            ),
        )
        val merged = mergeExploreData(old, incoming, isFullSync = false)
        assertEquals("Barbell Squat", merged.exercises.single().name.en)
    }
}
