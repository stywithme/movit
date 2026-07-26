package com.movit.core.data.repository

import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.ExerciseConfigRecord
import com.movit.core.training.config.LocalizedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfflineSubstitutionResolverTest {

    @Test
    fun familyMatchesWinAndKeepFamilyOrder() {
        val result = OfflineSubstitutionResolver.resolve(
            replacing = record("bodyweight-squat", familyKey = "squat", familyOrder = 1),
            catalog = listOf(
                record("bodyweight-squat", familyKey = "squat", familyOrder = 1),
                record("goblet-squat", familyKey = "squat", familyOrder = 3),
                record("box-squat", familyKey = "squat", familyOrder = 2),
                record("push-up", familyKey = "push"),
            ),
        )

        assertEquals(listOf("box-squat", "goblet-squat"), result.map { it.slug })
    }

    @Test
    fun fallsBackToMovementPatternAndArchetypeWhenFamilyHasNoSiblings() {
        val result = OfflineSubstitutionResolver.resolve(
            replacing = record(
                "bodyweight-squat",
                familyKey = "squat",
                movementPattern = "knee_dominant",
                archetype = "squat",
            ),
            catalog = listOf(
                record("lunge", familyKey = "lunge", movementPattern = "knee_dominant", archetype = "squat"),
                record("row", familyKey = "row", movementPattern = "horizontal_pull", archetype = "pull"),
            ),
        )

        assertEquals(listOf("lunge"), result.map { it.slug })
    }

    @Test
    fun fallbackExcludesTheOriginalFamilyAndIsCapped() {
        val catalog = (1..10).map { index ->
            record("alt-$index", familyKey = "other-$index", movementPattern = "hinge", archetype = "hinge")
        } + record("same-family", familyKey = "deadlift", movementPattern = "hinge", archetype = "hinge")

        val result = OfflineSubstitutionResolver.resolve(
            replacing = record(
                "deadlift",
                familyKey = "deadlift",
                movementPattern = "hinge",
                archetype = "hinge",
            ),
            catalog = catalog,
        )

        assertEquals(OfflineSubstitutionResolver.FALLBACK_LIMIT, result.size)
        assertTrue(result.none { it.slug == "same-family" })
    }

    @Test
    fun returnsEmptyWhenGroupingFieldsAreMissing() {
        val result = OfflineSubstitutionResolver.resolve(
            replacing = record("mystery"),
            catalog = listOf(record("other")),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun mapsBothLocalesAndArchetypeIntoTheDto() {
        val result = OfflineSubstitutionResolver.resolve(
            replacing = record("bodyweight-squat", familyKey = "squat"),
            catalog = listOf(
                record(
                    "goblet-squat",
                    familyKey = "squat",
                    archetype = "squat",
                    nameEn = "Goblet Squat",
                    nameAr = "سكوات جوبلت",
                ),
            ),
        )

        val dto = result.single()
        assertEquals("Goblet Squat", dto.name?.get("en"))
        assertEquals("سكوات جوبلت", dto.name?.get("ar"))
        assertEquals("squat", dto.archetype)
    }

    private fun record(
        slug: String,
        familyKey: String? = null,
        familyOrder: Int? = null,
        movementPattern: String? = null,
        archetype: String? = null,
        nameEn: String = slug,
        nameAr: String = slug,
    ) = ExerciseConfigRecord(
        id = "id-$slug",
        slug = slug,
        config = ExerciseConfig(
            name = LocalizedText(ar = nameAr, en = nameEn),
            familyKey = familyKey,
            familyOrder = familyOrder,
            movementPattern = movementPattern,
            archetype = archetype,
        ),
    )
}
