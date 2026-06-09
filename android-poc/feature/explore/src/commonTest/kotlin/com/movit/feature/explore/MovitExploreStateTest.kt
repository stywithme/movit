package com.movit.feature.explore

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovitExploreStateTest {

    private val items = MovitExplorePreviewData.exercises

    @Test
    fun filterSelection_limitsWorkouts() {
        val filtered = ExploreContentFilter.filterItems(
            items = items,
            query = "",
            filter = ExploreFilter.Workouts,
        )
        assertTrue(filtered.all { it.type == ExploreItemType.Workout })
        assertEquals(2, filtered.size)
    }

    @Test
    fun query_filtersByTitle() {
        val filtered = ExploreContentFilter.filterItems(
            items = items,
            query = "squat",
            filter = ExploreFilter.All,
        )
        assertEquals(1, filtered.size)
        assertEquals("ex-squat", filtered.first().id)
    }

    @Test
    fun emptyState_whenNoMatches() {
        val filtered = ExploreContentFilter.filterItems(
            items = items,
            query = "zzzz-not-found",
            filter = ExploreFilter.All,
        )
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun viewModel_queryChanged_updatesFilteredExercises() {
        runBlocking {
            val viewModel = MovitExploreViewModel(FakeExploreRepository())
            viewModel.load(isRefresh = false)
            viewModel.onEvent(MovitExploreEvent.QueryChanged("core"))
            val state = viewModel.state.value
            assertFalse(state.isEmpty)
            assertTrue(
                state.workouts.any { it.title.contains("Core", ignoreCase = true) } ||
                    state.exercises.any { it.title.contains("Core", ignoreCase = true) },
            )
        }
    }

    @Test
    fun viewModel_filterSelected_updatesState() {
        runBlocking {
            val viewModel = MovitExploreViewModel(FakeExploreRepository())
            viewModel.load(isRefresh = false)
            viewModel.onEvent(MovitExploreEvent.FilterSelected(ExploreFilter.Programs))
            assertTrue(viewModel.state.value.programs.all { it.type == ExploreItemType.Program })
        }
    }

    @Test
    fun errorState_preservedFromRepository() {
        runBlocking {
            val viewModel = MovitExploreViewModel(FakeExploreRepository(shouldFail = true))
            viewModel.load(isRefresh = false)
            assertEquals("Unable to load explore content.", viewModel.state.value.errorMessage)
        }
    }
}
