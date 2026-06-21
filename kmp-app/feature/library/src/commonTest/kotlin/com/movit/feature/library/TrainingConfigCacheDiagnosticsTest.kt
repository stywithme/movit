package com.movit.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals

class TrainingConfigCacheDiagnosticsTest {

    @Test
    fun trainingSlugCandidates_includesSlugAndExerciseId() {
        val candidates = trainingSlugCandidates(
            slug = "bicebs-mo8j7gg1",
            exerciseId = "planned-ex-1",
        )

        assertEquals(
            listOf("bicebs-mo8j7gg1", "planned-ex-1"),
            candidates,
        )
    }

    @Test
    fun trainingSlugCandidates_normalizesExploreIds() {
        val candidates = trainingSlugCandidates(
            slug = "ex-squat",
            exerciseId = "ex-squat",
        )

        assertEquals(
            listOf("ex-squat", "squat"),
            candidates,
        )
    }
}
