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
            val viewModel = MovitHomeViewModel(repository = FakeHomeRepository())
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
    fun repositoryFailure_onColdStart_showsHonestNoProgramEmpty() {
        runBlocking {
            val viewModel = MovitHomeViewModel(
                repository = FakeHomeRepository(shouldFail = true),
            )
            viewModel.load()
            assertNull(viewModel.state.value.errorMessage)
            assertEquals(false, viewModel.state.value.isLoading)
            assertTrue(viewModel.state.value.showNoProgramEmpty)
            assertTrue(viewModel.state.value.metricTiles.isEmpty())
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

    @Test
    fun notificationClicked_emitsOpenNotifications() = runBlocking {
        val viewModel = MovitHomeViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitHomeEvent.NotificationClicked)
        assertEquals(MovitHomeEffect.OpenNotifications, effectDeferred.await())
    }

    @Test
    fun levelCardClicked_emitsOpenLevel() = runBlocking {
        val viewModel = MovitHomeViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitHomeEvent.LevelCardClicked)
        assertEquals(MovitHomeEffect.OpenLevel, effectDeferred.await())
    }

    @Test
    fun viewProgramClicked_emitsOpenProgramDetail() = runBlocking {
        val viewModel = MovitHomeViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitHomeEvent.ViewProgramClicked("prog-full-body"))
        assertEquals(
            MovitHomeEffect.OpenProgramDetail("prog-full-body"),
            effectDeferred.await(),
        )
    }

    @Test
    fun viewPlanClicked_emitsOpenLevel() = runBlocking {
        val viewModel = MovitHomeViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitHomeEvent.ViewPlanClicked)
        assertEquals(MovitHomeEffect.OpenLevel, effectDeferred.await())
    }

    @Test
    fun alertProgressionClicked_emitsOpenTrain() = runBlocking {
        val viewModel = MovitHomeViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitHomeEvent.AlertClicked("progression_applied"))
        assertEquals(MovitHomeEffect.OpenTrain, effectDeferred.await())
    }

    @Test
    fun recentActivityClicked_emitsOpenReportDetail() = runBlocking {
        val viewModel = MovitHomeViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitHomeEvent.RecentActivityClicked("barbell-squat"))
        assertEquals(MovitHomeEffect.OpenReportDetail("barbell-squat"), effectDeferred.await())
    }

    @Test
    fun recentActivityClicked_blankId_fallsBackToReports() = runBlocking {
        val viewModel = MovitHomeViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitHomeEvent.RecentActivityClicked(""))
        assertEquals(MovitHomeEffect.OpenReports, effectDeferred.await())
    }

    @Test
    fun successfulLoad_heroProgressReflectsApi() {
        runBlocking {
            val viewModel = MovitHomeViewModel(repository = FakeHomeRepository())
            viewModel.load()
            val state = viewModel.state.value
            assertEquals(71, state.progress?.weeklyCompletionPercent)
        }
    }

    @Test
    fun refreshLoad_updatesDashboardAndClearsRefreshing() {
        runBlocking {
            val viewModel = MovitHomeViewModel(repository = FakeHomeRepository())
            viewModel.load(isRefresh = true)
            val state = viewModel.state.value
            assertEquals(false, state.isRefreshing)
            assertNotNull(state.todayPlan)
        }
    }

    @Test
    fun catchUpOpenClicked_emitsOpenCatchUpDay() {
        runBlocking {
            val catchUp = HomeCatchUpUi(
                message = "You missed yesterday",
                programId = "prog-1",
                weekNumber = 2,
                dayNumber = 2,
            )
            val viewModel = MovitHomeViewModel(
                repository = FakeHomeRepository(
                    dashboard = MovitHomePreviewData.dashboardWithPlan.copy(catchUp = catchUp),
                ),
            )
            viewModel.load(isRefresh = true)
            val effectDeferred = async {
                withTimeout(5_000) {
                    viewModel.effects.first()
                }
            }
            yield()
            viewModel.onEvent(MovitHomeEvent.CatchUpOpenClicked)
            assertEquals(
                MovitHomeEffect.OpenCatchUpDay("prog-1", 2, 2),
                effectDeferred.await(),
            )
        }
    }
}
