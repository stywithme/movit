package com.movit.feature.library

import com.movit.core.model.ExploreContent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryListViewModelTest {

    @Test
    fun load_populatesExerciseItems() = runBlocking {
        val viewModel = LibraryListViewModel(
            kind = LibraryListKind.Exercises,
            repository = FakeLibraryRepository(sampleLibraryContent(LibraryListKind.Exercises)),
        )
        viewModel.load()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(4, state.totalCount)
        assertTrue(state.items.isNotEmpty())
    }

    @Test
    fun queryChange_filtersItems() = runBlocking {
        val viewModel = LibraryListViewModel(
            kind = LibraryListKind.Exercises,
            repository = FakeLibraryRepository(sampleLibraryContent(LibraryListKind.Exercises)),
        )
        viewModel.load()
        viewModel.onQueryChange("lunge")

        val state = viewModel.state.value
        assertEquals(1, state.visibleCount)
        assertEquals("ex-lunge", state.items.first().id)
    }

    @Test
    fun filterSelected_limitsWorkoutsByCore() = runBlocking {
        val viewModel = LibraryListViewModel(
            kind = LibraryListKind.Workouts,
            repository = FakeLibraryRepository(sampleLibraryContent(LibraryListKind.Workouts)),
        )
        viewModel.load()
        viewModel.onFilterSelected(LibraryFilterChip.Core)

        val state = viewModel.state.value
        assertEquals(1, state.visibleCount)
        assertEquals("workout-core", state.items.first().id)
    }

    @Test
    fun seeMore_expandsVisibleItems() = runBlocking {
        val base = sampleWorkoutItems().first()
        val manyWorkouts = (1..8).map { index ->
            base.copy(id = "workout-$index", title = "Workout $index")
        }
        val viewModel = LibraryListViewModel(
            kind = LibraryListKind.Workouts,
            repository = FakeLibraryRepository(
                ExploreContent(
                    featured = emptyList(),
                    workouts = manyWorkouts,
                    exercises = emptyList(),
                    programs = emptyList(),
                ),
            ),
        )
        viewModel.load()
        assertEquals(LibraryListViewModel.DEFAULT_VISIBLE, viewModel.state.value.items.size)
        viewModel.onSeeMore()
        assertEquals(8, viewModel.state.value.items.size)
        assertTrue(viewModel.state.value.showAll)
    }

    @Test
    fun emptySearch_showsFilteredEmptyState() = runBlocking {
        val viewModel = LibraryListViewModel(
            kind = LibraryListKind.Exercises,
            repository = FakeLibraryRepository(sampleLibraryContent(LibraryListKind.Exercises)),
        )
        viewModel.load()
        viewModel.onQueryChange("not-found-xyz")

        val state = viewModel.state.value
        assertTrue(state.isFilteredEmpty)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun clearFilters_resetsQueryAndChip() = runBlocking {
        val viewModel = LibraryListViewModel(
            kind = LibraryListKind.Exercises,
            repository = FakeLibraryRepository(sampleLibraryContent(LibraryListKind.Exercises)),
        )
        viewModel.load()
        viewModel.onQueryChange("zzzz")
        viewModel.onFilterSelected(LibraryFilterChip.Core)
        viewModel.onClearFilters()

        val state = viewModel.state.value
        assertEquals("", state.query)
        assertEquals(LibraryFilterChip.All, state.selectedFilter)
        assertFalse(state.isFilteredEmpty)
    }

    @Test
    fun errorState_fromRepository() = runBlocking {
        val viewModel = LibraryListViewModel(
            kind = LibraryListKind.Workouts,
            repository = FakeLibraryRepository(shouldFail = true),
        )
        viewModel.load()

        assertEquals("Unable to load library content.", viewModel.state.value.errorMessage)
    }
}
