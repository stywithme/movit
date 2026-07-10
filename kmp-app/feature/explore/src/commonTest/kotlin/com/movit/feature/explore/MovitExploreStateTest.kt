package com.movit.feature.explore

import com.movit.core.model.ExploreItemType
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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
    fun workoutFilter_beginner_matchesLowLevelWorkouts() {
        val filtered = ExploreContentFilter.filterItems(
            items = MovitExplorePreviewData.workouts,
            query = "",
            filter = ExploreFilter.All,
            workoutFilter = ExploreWorkoutFilter.Beginner,
        )
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { (it.levelNumber ?: 0) in 1..2 })
    }

    @Test
    fun workoutFilter_short_matchesUnderTwentyMinutes() {
        val filtered = ExploreContentFilter.filterItems(
            items = MovitExplorePreviewData.workouts,
            query = "",
            filter = ExploreFilter.All,
            workoutFilter = ExploreWorkoutFilter.Short,
        )
        assertTrue(filtered.all { (it.durationMinutes ?: 0) <= 20 })
    }

    @Test
    fun exerciseCategory_filtersByCategoryCode() {
        val filtered = ExploreContentFilter.filterItems(
            items = MovitExplorePreviewData.exerciseOnly,
            query = "",
            filter = ExploreFilter.Exercises,
            exerciseCategoryCode = "core",
        )
        assertEquals(1, filtered.size)
        assertEquals("ex-plank", filtered.first().id)
    }

    @Test
    fun viewModel_queryChanged_updatesFilteredExercises() {
        runBlocking {
            val viewModel = MovitExploreViewModel(FakeExploreRepository().asContentSource())
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
            val viewModel = MovitExploreViewModel(FakeExploreRepository().asContentSource())
            viewModel.load(isRefresh = false)
            viewModel.onEvent(MovitExploreEvent.FilterSelected(ExploreFilter.Programs))
            assertTrue(viewModel.state.value.programs.all { it.type == ExploreItemType.Program })
        }
    }

    @Test
    fun viewModel_seeAllWorkouts_emitsWorkoutsLibrary() {
        runBlocking {
            val viewModel = MovitExploreViewModel(FakeExploreRepository().asContentSource())
            val effectDeferred = async {
                withTimeout(5_000) { viewModel.effects.first() }
            }
            yield()
            viewModel.onEvent(MovitExploreEvent.SeeAllWorkoutsClicked)
            assertEquals(MovitExploreEffect.OpenWorkoutsLibrary, effectDeferred.await())
        }
    }

    @Test
    fun viewModel_seeAllExercises_emitsExercisesLibrary() {
        runBlocking {
            val viewModel = MovitExploreViewModel(FakeExploreRepository().asContentSource())
            val effectDeferred = async {
                withTimeout(5_000) { viewModel.effects.first() }
            }
            yield()
            viewModel.onEvent(MovitExploreEvent.SeeAllExercisesClicked)
            assertEquals(MovitExploreEffect.OpenExercisesLibrary, effectDeferred.await())
        }
    }

    @Test
    fun viewModel_itemClicked_emitsWorkoutSessionEffect() {
        runBlocking {
            val viewModel = MovitExploreViewModel(FakeExploreRepository().asContentSource())
            val effectDeferred = async {
                withTimeout(5_000) { viewModel.effects.first() }
            }
            yield()
            viewModel.onEvent(MovitExploreEvent.ItemClicked("workout-lower-body", ExploreItemType.Workout))
            assertEquals(MovitExploreEffect.OpenWorkoutSession("workout-lower-body"), effectDeferred.await())
        }
    }

    @Test
    fun viewModel_itemClicked_emitsOpenProgramDetailForProgram() {
        runBlocking {
            val viewModel = MovitExploreViewModel(FakeExploreRepository().asContentSource())
            val effectDeferred = async {
                withTimeout(5_000) { viewModel.effects.first() }
            }
            yield()
            viewModel.onEvent(MovitExploreEvent.ItemClicked("prog-strength", ExploreItemType.Program))
            assertEquals(MovitExploreEffect.OpenProgramDetail("prog-strength"), effectDeferred.await())
        }
    }

    @Test
    fun viewModel_itemClicked_emitsExercisePrepareEffect() {
        runBlocking {
            val viewModel = MovitExploreViewModel(FakeExploreRepository().asContentSource())
            val effectDeferred = async {
                withTimeout(5_000) { viewModel.effects.first() }
            }
            yield()
            viewModel.onEvent(MovitExploreEvent.ItemClicked("ex-squat", ExploreItemType.Exercise))
            assertEquals(MovitExploreEffect.OpenExercisePrepare("ex-squat"), effectDeferred.await())
        }
    }

    @Test
    fun viewModel_filterButton_togglesSecondaryFilters() {
        runBlocking {
            val viewModel = MovitExploreViewModel(FakeExploreRepository().asContentSource())
            viewModel.load(isRefresh = false)
            assertTrue(viewModel.state.value.secondaryFiltersVisible)
            viewModel.onEvent(MovitExploreEvent.FilterButtonClicked)
            assertFalse(viewModel.state.value.secondaryFiltersVisible)
        }
    }

    @Test
    fun viewModel_refresh_loadsWithoutBlockingInitialSpinner() {
        runBlocking {
            val viewModel = MovitExploreViewModel(FakeExploreRepository().asContentSource())
            viewModel.load(isRefresh = false)
            assertFalse(viewModel.state.value.isRefreshing)
            viewModel.load(isRefresh = true)
            assertFalse(viewModel.state.value.isRefreshing)
            assertFalse(viewModel.state.value.isLoading)
            assertTrue(viewModel.state.value.workouts.isNotEmpty())
        }
    }

    @Test
    fun errorState_preservedFromRepository() {
        runBlocking {
            val viewModel = MovitExploreViewModel(FakeExploreRepository(shouldFail = true).asContentSource())
            viewModel.load(isRefresh = false)
            assertEquals("Unable to load explore content.", viewModel.state.value.errorMessage)
        }
    }

    @Test
    fun freshUpdate_preservesQueryAndFilter() {
        runBlocking {
            val fresh = MovitExplorePreviewData.content.copy(
                workouts = MovitExplorePreviewData.workouts + MovitExplorePreviewData.workouts.first().copy(
                    id = "workout-extra",
                    title = "Extra Core Blast",
                ),
            )
            val viewModel = MovitExploreViewModel(
                FakeExploreRepository(freshContent = fresh).asContentSource(),
            )
            viewModel.load(isRefresh = false)
            viewModel.onEvent(MovitExploreEvent.QueryChanged("core"))
            viewModel.onEvent(MovitExploreEvent.FilterSelected(ExploreFilter.Workouts))

            val state = viewModel.state.value
            assertEquals("core", state.query)
            assertEquals(ExploreFilter.Workouts, state.selectedFilter)
            assertTrue(state.workouts.all { it.title.contains("core", ignoreCase = true) })
        }
    }
}
