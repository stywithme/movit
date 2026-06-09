package com.movit.feature.reports

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovitReportsStateTest {

    @Test
    fun initialState_isLoading() {
        val viewModel = MovitReportsViewModel()
        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun successfulLoad_populatesDashboard() {
        runBlocking {
            val viewModel = MovitReportsViewModel()
            viewModel.load(isRefresh = false)
            val state = viewModel.state.value
            assertEquals(false, state.isLoading)
            assertNull(state.errorMessage)
            assertEquals(ReportsHubState.Success, state.dashboard?.hubState)
            assertTrue(state.dashboard?.kpis?.isNotEmpty() == true)
        }
    }

    @Test
    fun repositoryFailure_setsErrorMessage() {
        runBlocking {
            val viewModel = MovitReportsViewModel(
                repository = FakeReportsRepository(shouldFail = true),
            )
            viewModel.load(isRefresh = false)
            assertEquals("Unable to load reports.", viewModel.state.value.errorMessage)
        }
    }

    @Test
    fun exerciseReportClicked_emitsOpenReportDetail() = runBlocking {
        val viewModel = MovitReportsViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitReportsEvent.ExerciseReportClicked("barbell-squat"))
        assertEquals(
            MovitReportsEffect.OpenReportDetail("barbell-squat"),
            effectDeferred.await(),
        )
    }

    @Test
    fun refreshLoad_setsRefreshingThenClears() = runBlocking {
        val viewModel = MovitReportsViewModel()
        viewModel.load(isRefresh = false)
        viewModel.load(isRefresh = true)
        assertEquals(false, viewModel.state.value.isRefreshing)
        assertEquals(ReportsHubState.Success, viewModel.state.value.dashboard?.hubState)
    }

    @Test
    fun tabSelected_updatesSelectedTab() {
        val viewModel = MovitReportsViewModel()
        viewModel.onEvent(MovitReportsEvent.TabSelected(ReportsTab.Trends))
        assertEquals(ReportsTab.Trends, viewModel.state.value.selectedTab)
    }

    @Test
    fun startTrainingClicked_emitsOpenTrain() = runBlocking {
        val viewModel = MovitReportsViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitReportsEvent.StartTrainingClicked)
        assertEquals(MovitReportsEffect.OpenTrain, effectDeferred.await())
    }
}
