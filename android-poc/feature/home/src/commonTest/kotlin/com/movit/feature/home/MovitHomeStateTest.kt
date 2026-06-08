package com.movit.feature.home

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

class MovitHomeStateTest {

    @Test
    fun initialState_isLoading() {
        val viewModel = MovitHomeViewModel()
        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun successfulLoad_populatesDashboard() {
        runBlocking {
            val viewModel = MovitHomeViewModel()
            viewModel.load()
            val state = viewModel.state.value
            assertEquals(false, state.isLoading)
            assertNull(state.errorMessage)
            assertNotNull(state.todayPlan)
            assertNotNull(state.progress)
            assertEquals(71, state.progress.weeklyCompletionPercent)
            assertTrue(state.quickActions.isNotEmpty())
        }
    }

    @Test
    fun repositoryFailure_setsErrorMessage() {
        runBlocking {
            val viewModel = MovitHomeViewModel(
                repository = FakeHomeRepository(shouldFail = true),
            )
            viewModel.load()
            assertEquals("Unable to load home dashboard.", viewModel.state.value.errorMessage)
            assertEquals(false, viewModel.state.value.isLoading)
        }
    }

    @Test
    fun noPlanFixture_producesNullTodayPlan() {
        runBlocking {
            val viewModel = MovitHomeViewModel(
                repository = FakeHomeRepository(dashboard = MovitHomePreviewData.dashboardNoPlan),
            )
            viewModel.load()
            assertNull(viewModel.state.value.todayPlan)
            assertNotNull(viewModel.state.value.progress)
        }
    }

    @Test
    fun startTodayPlanClicked_emitsOpenTrain() = runBlocking {
        val viewModel = MovitHomeViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitHomeEvent.StartTodayPlanClicked)
        assertEquals(MovitHomeEffect.OpenTrain, effectDeferred.await())
    }

    @Test
    fun exploreClicked_emitsOpenExplore() = runBlocking {
        val viewModel = MovitHomeViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitHomeEvent.ExploreClicked)
        assertEquals(MovitHomeEffect.OpenExplore, effectDeferred.await())
    }

    @Test
    fun reportsClicked_emitsOpenReports() = runBlocking {
        val viewModel = MovitHomeViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitHomeEvent.ReportsClicked)
        assertEquals(MovitHomeEffect.OpenReports, effectDeferred.await())
    }

    @Test
    fun profileClicked_emitsOpenProfile() = runBlocking {
        val viewModel = MovitHomeViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitHomeEvent.ProfileClicked)
        assertEquals(MovitHomeEffect.OpenProfile, effectDeferred.await())
    }
}
