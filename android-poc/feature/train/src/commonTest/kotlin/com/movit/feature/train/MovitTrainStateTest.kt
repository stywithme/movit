package com.movit.feature.train

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MovitTrainStateTest {

    @Test
    fun initialState_isLoading() {
        val viewModel = MovitTrainViewModel()
        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun activePlan_loadsContent() {
        runBlocking {
            val viewModel = MovitTrainViewModel(repository = FakeTrainRepository())
            viewModel.load()
            val state = viewModel.state.value
            assertEquals(false, state.isLoading)
            assertEquals(TrainDashboardStatus.ActivePlan, state.dashboard?.status)
            assertNotNull(state.dashboard?.today)
        }
    }

    @Test
    fun noPlanState_mapsCorrectly() {
        runBlocking {
            val viewModel = MovitTrainViewModel(
                repository = FakeTrainRepository(MovitTrainPreviewData.noPlan),
            )
            viewModel.load()
            val dashboard = viewModel.state.value.dashboard
            assertEquals(TrainDashboardStatus.NoPlan, dashboard?.status)
            assertEquals(null, dashboard?.program)
        }
    }

    @Test
    fun restDayState_mapsCorrectly() {
        runBlocking {
            val viewModel = MovitTrainViewModel(
                repository = FakeTrainRepository(MovitTrainPreviewData.restDay),
            )
            viewModel.load()
            assertEquals(TrainDashboardStatus.RestDay, viewModel.state.value.dashboard?.status)
        }
    }

    @Test
    fun completedState_mapsCorrectly() {
        runBlocking {
            val viewModel = MovitTrainViewModel(
                repository = FakeTrainRepository(MovitTrainPreviewData.completedToday),
            )
            viewModel.load()
            assertEquals(TrainDashboardStatus.CompletedToday, viewModel.state.value.dashboard?.status)
        }
    }

    @Test
    fun repositoryFailure_setsError() {
        runBlocking {
            val viewModel = MovitTrainViewModel(
                repository = FakeTrainRepository(shouldFail = true),
            )
            viewModel.load()
            assertEquals("Unable to load training dashboard.", viewModel.state.value.errorMessage)
            assertEquals(false, viewModel.state.value.isLoading)
        }
    }

    @Test
    fun startWorkoutWithoutLaunchTarget_emitsOpenProgramList() {
        runBlocking {
            val viewModel = MovitTrainViewModel()
            val effectDeferred = async {
                withTimeout(5_000) {
                    viewModel.effects.first()
                }
            }
            yield()
            viewModel.onEvent(MovitTrainEvent.StartWorkoutClicked)
            assertEquals(MovitTrainEffect.OpenProgramList, effectDeferred.await())
        }
    }

    @Test
    fun startProgramClicked_emitsOpenProgramDetail() {
        runBlocking {
            val viewModel = MovitTrainViewModel()
            val effectDeferred = async {
                withTimeout(5_000) {
                    viewModel.effects.first()
                }
            }
            yield()
            viewModel.onEvent(MovitTrainEvent.StartProgramClicked("prog-strength"))
            assertEquals(MovitTrainEffect.OpenProgramDetail("prog-strength"), effectDeferred.await())
        }
    }

    @Test
    fun explorePrograms_emitsOpenProgramList() {
        runBlocking {
            val viewModel = MovitTrainViewModel()
            val effectDeferred = async {
                withTimeout(5_000) {
                    viewModel.effects.first()
                }
            }
            yield()
            viewModel.onEvent(MovitTrainEvent.ExploreProgramsClicked)
            assertEquals(MovitTrainEffect.OpenProgramList, effectDeferred.await())
        }
    }

    @Test
    fun weekNavigation_updatesSelectedIndex() {
        runBlocking {
            val viewModel = MovitTrainViewModel(repository = FakeTrainRepository())
            viewModel.load()
            val initialIndex = viewModel.state.value.selectedWeekIndex
            viewModel.onEvent(MovitTrainEvent.NextWeekClicked)
            assertEquals(initialIndex + 1, viewModel.state.value.selectedWeekIndex)
            viewModel.onEvent(MovitTrainEvent.PreviousWeekClicked)
            assertEquals(initialIndex, viewModel.state.value.selectedWeekIndex)
        }
    }

    @Test
    fun weekNavigation_clampsAtBounds() {
        runBlocking {
            val viewModel = MovitTrainViewModel(repository = FakeTrainRepository())
            viewModel.load()
            repeat(10) { viewModel.onEvent(MovitTrainEvent.PreviousWeekClicked) }
            assertEquals(0, viewModel.state.value.selectedWeekIndex)
            val lastIndex = viewModel.state.value.dashboard
                ?.weekOptions
                ?.lastIndex
                ?: 0
            repeat(10) { viewModel.onEvent(MovitTrainEvent.NextWeekClicked) }
            assertEquals(lastIndex, viewModel.state.value.selectedWeekIndex)
        }
    }

    @Test
    fun noPlan_includesFeaturedPrograms() {
        runBlocking {
            val viewModel = MovitTrainViewModel(
                repository = FakeTrainRepository(MovitTrainPreviewData.noPlan),
            )
            viewModel.load()
            val programs = viewModel.state.value.dashboard?.featuredPrograms.orEmpty()
            assertTrue(programs.isNotEmpty())
            assertNotNull(programs.first().imageUrl)
        }
    }

    @Test
    fun viewReport_emitsOpenWeeklyReportWhenProgramPresent() {
        runBlocking {
            val viewModel = MovitTrainViewModel(repository = FakeTrainRepository())
            viewModel.load()
            val effectDeferred = async {
                withTimeout(5_000) {
                    viewModel.effects.first()
                }
            }
            yield()
            viewModel.onEvent(MovitTrainEvent.ViewReportClicked)
            assertEquals(
                MovitTrainEffect.OpenWeeklyReport(
                    programId = "prog-full-body",
                    weekNumber = 2,
                ),
                effectDeferred.await(),
            )
        }
    }

    @Test
    fun refreshLoad_updatesDashboardAndClearsRefreshing() = runBlocking {
        val viewModel = MovitTrainViewModel(repository = FakeTrainRepository())
        viewModel.load(isRefresh = true)
        val state = viewModel.state.value
        assertEquals(false, state.isRefreshing)
        assertEquals(TrainDashboardStatus.ActivePlan, state.dashboard?.status)
    }
}
