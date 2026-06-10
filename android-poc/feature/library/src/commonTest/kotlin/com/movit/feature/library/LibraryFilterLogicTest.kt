package com.movit.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryFilterLogicTest {

    private val exercises = LibraryTestFixtures.exercises
    private val workouts = LibraryTestFixtures.workouts

    @Test
    fun coreFilter_matchesCoreWorkout() {
        val filtered = LibraryFilterLogic.filterItems(
            items = workouts,
            kind = LibraryListKind.Workouts,
            chip = LibraryFilterChip.Core,
            query = "",
        )
        assertEquals(1, filtered.size)
        assertEquals("workout-core", filtered.first().id)
    }

    @Test
    fun query_filtersByTitle() {
        val filtered = LibraryFilterLogic.filterItems(
            items = exercises,
            kind = LibraryListKind.Exercises,
            chip = LibraryFilterChip.All,
            query = "squat",
        )
        assertEquals(1, filtered.size)
        assertEquals("ex-squat", filtered.first().id)
    }

    @Test
    fun under20Min_usesDurationMinutes() {
        val filtered = LibraryFilterLogic.filterItems(
            items = workouts,
            kind = LibraryListKind.Workouts,
            chip = LibraryFilterChip.Under20Min,
            query = "",
        )
        assertTrue(filtered.any { it.id == "workout-core" })
        assertFalse(filtered.any { it.id == "workout-full-body" })
    }

    @Test
    fun empty_whenNoMatches() {
        val filtered = LibraryFilterLogic.filterItems(
            items = exercises,
            kind = LibraryListKind.Exercises,
            chip = LibraryFilterChip.All,
            query = "zzzz-not-found",
        )
        assertTrue(filtered.isEmpty())
    }
}
