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
    fun activePlan_loadsContent() = runBlocking {
        val viewModel = MovitTrainViewModel()
        viewModel.load()
        val state = viewModel.state.value
        assertEquals(false, state.isLoading)
        assertEquals(TrainDashboardStatus.ActivePlan, state.dashboard?.status)
        assertNotNull(state.dashboard?.today)
        Unit
    }

    @Test
    fun noPlanState_mapsCorrectly() = runBlocking {
        val viewModel = MovitTrainViewModel(
            repository = FakeTrainRepository(MovitTrainPreviewData.noPlan),
        )
        viewModel.load()
        val dashboard = viewModel.state.value.dashboard
        assertEquals(TrainDashboardStatus.NoPlan, dashboard?.status)
        assertEquals(null, dashboard?.program)
    }

    @Test
    fun restDayState_mapsCorrectly() = runBlocking {
        val viewModel = MovitTrainViewModel(
            repository = FakeTrainRepository(MovitTrainPreviewData.restDay),
        )
        viewModel.load()
        assertEquals(TrainDashboardStatus.RestDay, viewModel.state.value.dashboard?.status)
    }

    @Test
    fun completedState_mapsCorrectly() = runBlocking {
        val viewModel = MovitTrainViewModel(
            repository = FakeTrainRepository(MovitTrainPreviewData.completedToday),
        )
        viewModel.load()
        assertEquals(TrainDashboardStatus.CompletedToday, viewModel.state.value.dashboard?.status)
    }

    @Test
    fun repositoryFailure_setsError() = runBlocking {
        val viewModel = MovitTrainViewModel(
            repository = FakeTrainRepository(shouldFail = true),
        )
        viewModel.load()
        assertEquals("Unable to load training dashboard.", viewModel.state.value.errorMessage)
        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test
    fun startWorkout_emitsOpenSessionPreview() = runBlocking {
        val viewModel = MovitTrainViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitTrainEvent.StartWorkoutClicked)
        assertEquals(MovitTrainEffect.OpenSessionPreview, effectDeferred.await())
    }

    @Test
    fun explorePrograms_emitsOpenExplore() = runBlocking {
        val viewModel = MovitTrainViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitTrainEvent.ExploreProgramsClicked)
        assertEquals(MovitTrainEffect.OpenExplore, effectDeferred.await())
    }

    @Test
    fun viewReport_emitsOpenReports() = runBlocking {
        val viewModel = MovitTrainViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitTrainEvent.ViewReportClicked)
        assertEquals(MovitTrainEffect.OpenReports, effectDeferred.await())
    }
}
