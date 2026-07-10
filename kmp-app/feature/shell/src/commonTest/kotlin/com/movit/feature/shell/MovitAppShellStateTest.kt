package com.movit.feature.shell

import com.movit.feature.account.AuthBootstrapContext
import com.movit.feature.account.MovitAuthEffect
import com.movit.feature.account.MovitProfileEffect
import com.movit.feature.explore.MovitExploreEffect
import com.movit.feature.home.MovitHomeEffect
import com.movit.feature.library.WorkoutSessionKeys
import com.movit.feature.reports.MovitReportsEffect
import com.movit.feature.train.MovitTrainEffect
import com.movit.feature.train.TrainReportTargetUi
import com.movit.feature.train.TrainWorkoutLaunchUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MovitAppShellStateTest {

    @Test
    fun initialDestination_isHome() {
        val viewModel = MovitAppShellViewModel()
        assertEquals(MovitAppDestination.Home, viewModel.state.value.selectedDestination)
    }

    @Test
    fun resolveStartupInnerStack_unsignedUser_opensAuth() {
        val stack = MovitAppShellViewModel.resolveStartupInnerStack(
            bootstrap = AuthBootstrapContext(
                movitDataInstalled = true,
                hasActiveSession = false,
                introSeen = true,
            ),
        )
        assertEquals(listOf(MovitInnerRoute.Auth), stack)
    }

    @Test
    fun resolveStartupInnerStack_firstLaunch_opensAuthForSplashFlow() {
        val stack = MovitAppShellViewModel.resolveStartupInnerStack(
            bootstrap = AuthBootstrapContext(
                movitDataInstalled = true,
                hasActiveSession = false,
                introSeen = false,
            ),
        )
        assertEquals(listOf(MovitInnerRoute.Auth), stack)
    }

    @Test
    fun resolveStartupInnerStack_signedInUser_staysOnShell() {
        val stack = MovitAppShellViewModel.resolveStartupInnerStack(
            bootstrap = AuthBootstrapContext(
                movitDataInstalled = true,
                hasActiveSession = true,
                introSeen = false,
            ),
        )
        assertEquals(emptyList<MovitInnerRoute>(), stack)
    }

    @Test
    fun resolveStartupInnerStack_signedInWithoutLocalFlag_defersOnboardingToAsyncGate() {
        val stack = MovitAppShellViewModel.resolveStartupInnerStack(
            bootstrap = AuthBootstrapContext(
                movitDataInstalled = true,
                hasActiveSession = true,
                introSeen = false,
            ),
        )
        assertEquals(emptyList<MovitInnerRoute>(), stack)
    }

    @Test
    fun selectingExplore_updatesDestination() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.DestinationSelected(MovitAppDestination.Explore))
        assertEquals(MovitAppDestination.Explore, viewModel.state.value.selectedDestination)
    }

    @Test
    fun selectingTrain_updatesDestination() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.DestinationSelected(MovitAppDestination.Train))
        assertEquals(MovitAppDestination.Train, viewModel.state.value.selectedDestination)
    }

    @Test
    fun selectingSameDestination_isIdempotent() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.DestinationSelected(MovitAppDestination.Home))
        viewModel.onEvent(MovitAppShellEvent.DestinationSelected(MovitAppDestination.Home))
        assertEquals(MovitAppDestination.Home, viewModel.state.value.selectedDestination)
    }

    @Test
    fun exploreItemSelected_pushesExercisePrepareInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.ExploreItemSelected("ex-squat"))
        assertEquals(
            MovitInnerRoute.ExercisePrepare("ex-squat"),
            viewModel.state.value.currentInnerRoute,
        )
    }

    @Test
    fun homeOpenProfile_pushesProfileInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.HomeEffectReceived(MovitHomeEffect.OpenProfile))
        assertEquals(MovitInnerRoute.Profile, viewModel.state.value.currentInnerRoute)
        assertEquals(MovitAppDestination.Home, viewModel.state.value.selectedDestination)
    }

    @Test
    fun floatingDestinations_hasFourTabsWithoutProfile() {
        assertEquals(4, MovitShellFloatingDestinations.size)
        assertTrue(MovitShellFloatingDestinations.none { it.name == "Profile" })
    }

    @Test
    fun destinationSelectedProfile_pushesProfileInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.DestinationSelected(MovitAppDestination.Profile))
        assertEquals(MovitInnerRoute.Profile, viewModel.state.value.currentInnerRoute)
    }

    @Test
    fun tabProfileClicked_pushesProfileInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.DestinationSelected(MovitAppDestination.Train))
        viewModel.onEvent(MovitAppShellEvent.TabProfileClicked)
        assertEquals(MovitInnerRoute.Profile, viewModel.state.value.currentInnerRoute)
        assertEquals(MovitAppDestination.Train, viewModel.state.value.selectedDestination)
    }

    @Test
    fun homeOpenExplore_changesDestination() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.HomeEffectReceived(MovitHomeEffect.OpenExplore))
        assertEquals(MovitAppDestination.Explore, viewModel.state.value.selectedDestination)
    }

    @Test
    fun trainOpenProgramList_pushesInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.TrainEffectReceived(MovitTrainEffect.OpenProgramList))
        assertEquals(MovitInnerRoute.ProgramList, viewModel.state.value.currentInnerRoute)
    }

    @Test
    fun trainOpenReport_programDayWithoutReportId_pushesWeeklyReport() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.TrainEffectReceived(
                MovitTrainEffect.OpenReport(
                    TrainReportTargetUi.ProgramDay(
                        programId = "prog-full-body",
                        weekNumber = 2,
                        dayNumber = 3,
                        plannedWorkoutId = "pw-lower",
                    ),
                ),
            ),
        )
        assertEquals(
            MovitInnerRoute.WeeklyReport("prog-full-body", weekNumber = 2),
            viewModel.state.value.currentInnerRoute,
        )
    }

    @Test
    fun trainOpenReport_programDayWithReportId_pushesReportDetail() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.TrainEffectReceived(
                MovitTrainEffect.OpenReport(
                    TrainReportTargetUi.ProgramDay(
                        programId = "prog-full-body",
                        weekNumber = 2,
                        dayNumber = 3,
                        plannedWorkoutId = "pw-lower",
                        reportId = "pwr-day-42",
                    ),
                ),
            ),
        )
        assertEquals(
            MovitInnerRoute.ReportDetail("pwr-day-42"),
            viewModel.state.value.currentInnerRoute,
        )
    }

    @Test
    fun exploreAndTrain_openWorkout_viaLaunchRequest_sameWorkoutSessionRoute() {
        val exploreVm = MovitAppShellViewModel()
        exploreVm.onEvent(
            MovitAppShellEvent.ExploreEffectReceived(
                MovitExploreEffect.OpenWorkoutSession("workout-lower-body"),
            ),
        )
        assertEquals(
            MovitInnerRoute.WorkoutSession("workout-lower-body"),
            exploreVm.state.value.currentInnerRoute,
        )

        val trainVm = MovitAppShellViewModel()
        trainVm.onEvent(
            MovitAppShellEvent.TrainEffectReceived(
                MovitTrainEffect.OpenProgramWorkout(
                    TrainWorkoutLaunchUi(
                        programSlug = "prog-1",
                        programId = "prog-1",
                        weekNumber = 2,
                        dayNumber = 3,
                        plannedWorkoutId = "pw-1",
                    ),
                ),
            ),
        )
        assertEquals(
            MovitInnerRoute.WorkoutSession(
                WorkoutSessionKeys.encode("prog-1", 2, 3, "pw-1"),
            ),
            trainVm.state.value.currentInnerRoute,
        )
    }

    @Test
    fun trainOpenWeeklyReport_pushesInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.TrainEffectReceived(
                MovitTrainEffect.OpenWeeklyReport("prog-full-body", weekNumber = 2),
            ),
        )
        assertEquals(
            MovitInnerRoute.WeeklyReport("prog-full-body", weekNumber = 2),
            viewModel.state.value.currentInnerRoute,
        )
    }

    @Test
    fun exploreOpenProgramList_pushesInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.ExploreEffectReceived(MovitExploreEffect.OpenProgramList),
        )
        assertEquals(MovitInnerRoute.ProgramList, viewModel.state.value.currentInnerRoute)
    }

    @Test
    fun trainOpenExplore_changesDestination() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.TrainEffectReceived(MovitTrainEffect.OpenExplore))
        assertEquals(MovitAppDestination.Explore, viewModel.state.value.selectedDestination)
    }

    @Test
    fun trainOpenReports_changesDestination() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.TrainEffectReceived(MovitTrainEffect.OpenReports))
        assertEquals(MovitAppDestination.Reports, viewModel.state.value.selectedDestination)
    }

    @Test
    fun exploreOpenExercisesLibrary_pushesInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.ExploreEffectReceived(MovitExploreEffect.OpenExercisesLibrary),
        )
        assertEquals(MovitInnerRoute.ExercisesLibrary, viewModel.state.value.currentInnerRoute)
    }

    @Test
    fun exploreOpenWorkoutSession_pushesWorkoutSessionInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.ExploreEffectReceived(
                MovitExploreEffect.OpenWorkoutSession("workout-lower-body"),
            ),
        )
        assertEquals(
            MovitInnerRoute.WorkoutSession("workout-lower-body"),
            viewModel.state.value.currentInnerRoute,
        )
    }

    @Test
    fun exploreOpenExercisePrepare_pushesExercisePrepareInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.ExploreEffectReceived(
                MovitExploreEffect.OpenExercisePrepare("ex-squat"),
            ),
        )
        assertEquals(
            MovitInnerRoute.ExercisePrepare("ex-squat"),
            viewModel.state.value.currentInnerRoute,
        )
    }

    @Test
    fun reportsOpenUpgrade_launchesSubscriptionWithoutProfileTabSwitch() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.ReportsEffectReceived(MovitReportsEffect.OpenUpgrade),
        )
        assertEquals(MovitAppDestination.Home, viewModel.state.value.selectedDestination)
    }

    @Test
    fun reportsOpenReportDetail_pushesInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.ReportsEffectReceived(
                MovitReportsEffect.OpenReportDetail("barbell-squat"),
            ),
        )
        assertEquals(
            MovitInnerRoute.ReportDetail("barbell-squat"),
            viewModel.state.value.currentInnerRoute,
        )
    }

    @Test
    fun homeOpenReportDetail_pushesInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.HomeEffectReceived(
                MovitHomeEffect.OpenReportDetail("barbell-squat"),
            ),
        )
        assertEquals(
            MovitInnerRoute.ReportDetail("barbell-squat"),
            viewModel.state.value.currentInnerRoute,
        )
    }

    @Test
    fun homeOpenAssessment_pushesAssessmentInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.HomeEffectReceived(MovitHomeEffect.OpenAssessment))
        assertEquals(MovitInnerRoute.Assessment(), viewModel.state.value.currentInnerRoute)
    }

    @Test
    fun homeOpenCatchUpDay_pushesWorkoutSessionWithAutoPlannedWorkout() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.HomeEffectReceived(
                MovitHomeEffect.OpenCatchUpDay(
                    programId = "prog-1",
                    weekNumber = 2,
                    dayNumber = 2,
                ),
            ),
        )
        assertEquals(
            MovitInnerRoute.WorkoutSession("session:prog-1:2:2:_auto"),
            viewModel.state.value.currentInnerRoute,
        )
    }

    @Test
    fun homeOpenProgramDetail_pushesProgramDetailInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.HomeEffectReceived(
                MovitHomeEffect.OpenProgramDetail("prog-full-body"),
            ),
        )
        assertEquals(
            MovitInnerRoute.ProgramDetail("prog-full-body"),
            viewModel.state.value.currentInnerRoute,
        )
    }

    @Test
    fun exploreOpenProgramDetail_pushesProgramDetailInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.ExploreEffectReceived(
                MovitExploreEffect.OpenProgramDetail("prog-strength"),
            ),
        )
        assertEquals(
            MovitInnerRoute.ProgramDetail("prog-strength"),
            viewModel.state.value.currentInnerRoute,
        )
    }

    @Test
    fun trainOpenProgramDetail_pushesProgramDetailInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.TrainEffectReceived(
                MovitTrainEffect.OpenProgramDetail("prog-strength"),
            ),
        )
        assertEquals(
            MovitInnerRoute.ProgramDetail("prog-strength"),
            viewModel.state.value.currentInnerRoute,
        )
    }

    @Test
    fun programListClick_pushesProgramDetailInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.ProgramList))
        viewModel.onEvent(
            MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.ProgramDetail("prog-strength")),
        )
        assertEquals(
            MovitInnerRoute.ProgramDetail("prog-strength"),
            viewModel.state.value.currentInnerRoute,
        )
    }

    @Test
    fun homeOpenLevel_pushesLevelInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.HomeEffectReceived(MovitHomeEffect.OpenLevel))
        assertEquals(MovitInnerRoute.LevelProfile, viewModel.state.value.currentInnerRoute)
    }

    @Test
    fun profileOpenAuth_pushesAuthInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.ProfileEffectReceived(MovitProfileEffect.OpenAuth))
        assertEquals(MovitInnerRoute.Auth, viewModel.state.value.currentInnerRoute)
    }

    @Test
    fun profileOpenOnboarding_pushesOnboardingInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.ProfileEffectReceived(MovitProfileEffect.OpenOnboarding))
        assertEquals(MovitInnerRoute.ProfileOnboarding, viewModel.state.value.currentInnerRoute)
    }

    @Test
    fun authOpenOnboarding_pushesOnboardingInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.AuthEffectReceived(MovitAuthEffect.OpenOnboarding))
        assertEquals(MovitInnerRoute.ProfileOnboarding, viewModel.state.value.currentInnerRoute)
    }

    @Test
    fun profileLanguageChanged_incrementsLocaleRevision() {
        val viewModel = MovitAppShellViewModel()
        val before = viewModel.state.value.localeRevision
        viewModel.onEvent(
            MovitAppShellEvent.ProfileEffectReceived(MovitProfileEffect.LanguageChanged("ar")),
        )
        assertEquals(before + 1, viewModel.state.value.localeRevision)
    }

    @Test
    fun profileThemeModeChanged_updatesShellTheme() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.ProfileEffectReceived(MovitProfileEffect.ThemeModeChanged("dark")),
        )
        assertEquals("dark", viewModel.state.value.themeMode)
    }

    @Test
    fun backPressed_withInnerRoute_popsStack() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.HomeEffectReceived(MovitHomeEffect.OpenAssessment))
        assertEquals(MovitInnerRoute.Assessment(), viewModel.state.value.currentInnerRoute)
        assertEquals(true, viewModel.handleSystemBack())
        assertEquals(null, viewModel.state.value.currentInnerRoute)
    }

    @Test
    fun backPressed_onTrainTab_navigatesToHome() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.DestinationSelected(MovitAppDestination.Train))
        assertEquals(true, viewModel.handleSystemBack())
        assertEquals(MovitAppDestination.Home, viewModel.state.value.selectedDestination)
        assertEquals(null, viewModel.state.value.currentInnerRoute)
    }

    @Test
    fun backPressed_onHomeWithEmptyStack_isNotConsumed() {
        val viewModel = MovitAppShellViewModel()
        assertEquals(false, viewModel.handleSystemBack())
        assertEquals(MovitAppDestination.Home, viewModel.state.value.selectedDestination)
    }

    @Test
    fun tabSwitch_preservesInnerStackWhenPresent() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.HomeEffectReceived(MovitHomeEffect.OpenLevel))
        viewModel.onEvent(MovitAppShellEvent.DestinationSelected(MovitAppDestination.Train))
        assertEquals(MovitInnerRoute.LevelProfile, viewModel.state.value.currentInnerRoute)
        assertEquals(MovitAppDestination.Train, viewModel.state.value.selectedDestination)
    }

    @Test
    fun workoutSessionExerciseCard_pushesStandaloneExercisePrepare() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.WorkoutSession("preview")))
        viewModel.onEvent(
            MovitAppShellEvent.InnerRoutePushed(
                MovitInnerRoute.ExercisePrepare("bodyweight-squat"),
            ),
        )
        assertEquals(
            MovitInnerRoute.ExercisePrepare("bodyweight-squat"),
            viewModel.state.value.currentInnerRoute,
        )
    }

    @Test
    fun exercisePrepareWithWorkoutId_start_pushesTrainingSessionRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.InnerRoutePushed(
                MovitInnerRoute.ExercisePrepare("bodyweight-squat", workoutId = "preview"),
            ),
        )
        viewModel.onEvent(
            MovitAppShellEvent.InnerRoutePushed(
                MovitInnerRoute.TrainingSession(
                    exerciseSlug = "bodyweight-squat",
                    exerciseName = "Squat",
                    targetReps = 12,
                    workoutId = "preview",
                ),
            ),
        )
        val route = viewModel.state.value.currentInnerRoute
        assertTrue(route is MovitInnerRoute.TrainingSession)
        assertEquals("preview", (route as MovitInnerRoute.TrainingSession).workoutId)
    }

    @Test
    fun innerRoutePopped_removesTopRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.ExercisesLibrary))
        viewModel.onEvent(MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.WorkoutsLibrary))
        viewModel.onEvent(MovitAppShellEvent.InnerRoutePopped)
        assertEquals(MovitInnerRoute.ExercisesLibrary, viewModel.state.value.currentInnerRoute)
    }

    @Test
    fun workoutSessionStart_pushesExercisePrepareRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.WorkoutSession("preview")))
        viewModel.onEvent(
            MovitAppShellEvent.InnerRoutePushed(
                MovitInnerRoute.ExercisePrepare("bodyweight-squat", workoutId = "preview"),
            ),
        )
        assertEquals(
            MovitInnerRoute.ExercisePrepare("bodyweight-squat", workoutId = "preview"),
            viewModel.state.value.currentInnerRoute,
        )
    }
}
