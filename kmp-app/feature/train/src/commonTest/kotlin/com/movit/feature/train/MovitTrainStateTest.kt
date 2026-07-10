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
    fun startSessionWithoutLaunchTarget_emitsOpenProgramList() {
        runBlocking {
            val viewModel = MovitTrainViewModel()
            val effectDeferred = async {
                withTimeout(5_000) {
                    viewModel.effects.first()
                }
            }
            yield()
            viewModel.onEvent(
                MovitTrainEvent.StartSession(
                    TrainWorkoutLaunchUi(
                        programSlug = "missing",
                        programId = "",
                        weekNumber = 1,
                        dayNumber = 1,
                        plannedWorkoutId = "",
                    ),
                ),
            )
            assertEquals(
                MovitTrainEffect.OpenProgramWorkout(
                    TrainWorkoutLaunchUi(
                        programSlug = "missing",
                        programId = "",
                        weekNumber = 1,
                        dayNumber = 1,
                        plannedWorkoutId = "",
                    ),
                ),
                effectDeferred.await(),
            )
        }
    }

    @Test
    fun startSession_emitsOpenProgramWorkoutForTarget() {
        runBlocking {
            val target = TrainWorkoutLaunchUi(
                programSlug = "prog-full-body",
                programId = "prog-full-body",
                weekNumber = 2,
                dayNumber = 3,
                plannedWorkoutId = "pw-mobility",
            )
            val viewModel = MovitTrainViewModel(repository = FakeTrainRepository())
            val effectDeferred = async {
                withTimeout(5_000) {
                    viewModel.effects.first()
                }
            }
            yield()
            viewModel.onEvent(MovitTrainEvent.StartSession(target))
            assertEquals(MovitTrainEffect.OpenProgramWorkout(target), effectDeferred.await())
        }
    }

    @Test
    fun sessionBStart_emitsMobilityTarget() {
        runBlocking {
            val mobilityTarget = TrainWorkoutLaunchUi(
                programSlug = "full-body-4-week",
                programId = "prog-full-body",
                weekNumber = 2,
                dayNumber = 3,
                plannedWorkoutId = "pw-mobility",
            )
            val viewModel = MovitTrainViewModel(repository = FakeTrainRepository())
            viewModel.load(isRefresh = true)
            val effectDeferred = async {
                withTimeout(5_000) {
                    viewModel.effects.first()
                }
            }
            yield()
            viewModel.onEvent(MovitTrainEvent.StartSession(mobilityTarget))
            assertEquals(MovitTrainEffect.OpenProgramWorkout(mobilityTarget), effectDeferred.await())
        }
    }

    @Test
    fun cachedThenFresh_preservesWeekAndDaySelection() {
        runBlocking {
            val freshWeek = MovitTrainPreviewData.week.copy(
                title = "Week 2 refreshed",
                days = MovitTrainPreviewData.week.days.map { day ->
                    if (day.dayNumber == "4") {
                        day.copy(detail = day.detail?.copy(title = "Lower Body Strength (fresh)"))
                    } else {
                        day
                    }
                },
            )
            val fresh = MovitTrainPreviewData.activePlan.copy(
                week = freshWeek,
                weekOptions = listOf(
                    MovitTrainPreviewData.week1,
                    freshWeek,
                    MovitTrainPreviewData.week3,
                ),
            )
            val viewModel = MovitTrainViewModel(
                repository = FakeTrainRepository(
                    dashboard = MovitTrainPreviewData.activePlan,
                    freshDashboard = fresh,
                ),
            )
            viewModel.load(isRefresh = true)
            viewModel.onEvent(MovitTrainEvent.DayClicked(3))
            assertEquals(3, viewModel.state.value.selectedDayIndex)
            assertEquals(4, viewModel.state.value.selectedDayNumber)

            viewModel.load(isRefresh = true)

            assertEquals(1, viewModel.state.value.selectedWeekIndex)
            assertEquals(2, viewModel.state.value.selectedWeekNumber)
            assertEquals(3, viewModel.state.value.selectedDayIndex)
            assertEquals(4, viewModel.state.value.selectedDayNumber)
            assertEquals(
                "Lower Body Strength (fresh)",
                viewModel.state.value.dashboard?.weekOptions?.get(1)?.days?.get(3)?.detail?.title,
            )
        }
    }

    @Test
    fun missedDayAction_emitsCatchUpTarget() {
        runBlocking {
            val missedDetail = TrainWeekDayDetailUi(
                title = "Upper Body",
                infoLabel = "4 exercises",
                statusLabel = "Missed",
                isWorkout = true,
                actionLabel = "Start catch-up",
                launchTarget = TrainWorkoutLaunchUi(
                    programSlug = "prog-1",
                    programId = "prog-1",
                    weekNumber = 1,
                    dayNumber = 1,
                    plannedWorkoutId = "pw-missed",
                ),
            )
            val viewModel = MovitTrainViewModel()
            val effectDeferred = async {
                withTimeout(5_000) {
                    viewModel.effects.first()
                }
            }
            yield()
            viewModel.onEvent(MovitTrainEvent.DayActionClicked(missedDetail))
            assertEquals(
                MovitTrainEffect.OpenProgramWorkout(missedDetail.launchTarget!!),
                effectDeferred.await(),
            )
        }
    }

    @Test
    fun viewReport_emitsOpenReportForDayTarget() {
        runBlocking {
            val viewModel = MovitTrainViewModel(
                repository = FakeTrainRepository(MovitTrainPreviewData.completedToday),
            )
            viewModel.load()
            val effectDeferred = async {
                withTimeout(5_000) {
                    viewModel.effects.first()
                }
            }
            yield()
            viewModel.onEvent(
                MovitTrainEvent.ViewReport(
                    TrainReportTargetUi.ProgramDay(
                        programId = "prog-full-body",
                        weekNumber = 2,
                        dayNumber = 3,
                        plannedWorkoutId = "pw-lower",
                    ),
                ),
            )
            assertEquals(
                MovitTrainEffect.OpenReport(
                    TrainReportTargetUi.ProgramDay(
                        programId = "prog-full-body",
                        weekNumber = 2,
                        dayNumber = 3,
                        plannedWorkoutId = "pw-lower",
                    ),
                ),
                effectDeferred.await(),
            )
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
            viewModel.onEvent(
                MovitTrainEvent.ViewReport(
                    TrainReportTargetUi.ProgramWeek(
                        programId = "prog-full-body",
                        weekNumber = 2,
                    ),
                ),
            )
            assertEquals(
                MovitTrainEffect.OpenReport(
                    TrainReportTargetUi.ProgramWeek(
                        programId = "prog-full-body",
                        weekNumber = 2,
                    ),
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
