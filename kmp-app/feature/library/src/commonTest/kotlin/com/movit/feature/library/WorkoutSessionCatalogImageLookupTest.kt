package com.movit.feature.library

import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreExerciseDto
import com.movit.core.network.dto.LocalizedNameDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkoutSessionCatalogImageLookupTest {

    @Test
    fun buildExerciseCatalog_usesExploreImageUrl_withoutCallingResolver() {
        var resolverCalls = 0
        val explore = ExploreDataDto(
            exercises = listOf(
                ExploreExerciseDto(
                    id = "ex-1",
                    slug = "squat",
                    name = LocalizedNameDto(en = "Squat"),
                    imageUrl = "https://cdn.example/squat.png",
                ),
            ),
        )
        val (bySlug, _) = WorkoutSessionApiMapper.buildExerciseCatalog(
            explore = explore,
            language = "en",
            imageUrlForSlug = {
                resolverCalls++
                "fallback"
            },
        )
        assertEquals("https://cdn.example/squat.png", bySlug["squat"]?.imageUrl)
        assertEquals(0, resolverCalls)
    }

    @Test
    fun buildExerciseCatalog_fallsBackToResolverWhenImageMissing() {
        var resolverCalls = 0
        val explore = ExploreDataDto(
            exercises = listOf(
                ExploreExerciseDto(
                    id = "ex-2",
                    slug = "lunge",
                    name = LocalizedNameDto(en = "Lunge"),
                    imageUrl = null,
                ),
            ),
        )
        val (bySlug, _) = WorkoutSessionApiMapper.buildExerciseCatalog(
            explore = explore,
            language = "en",
            imageUrlForSlug = {
                resolverCalls++
                "https://cdn.example/lunge.png"
            },
        )
        assertEquals("https://cdn.example/lunge.png", bySlug["lunge"]?.imageUrl)
        assertTrue(resolverCalls >= 1)
    }
}
