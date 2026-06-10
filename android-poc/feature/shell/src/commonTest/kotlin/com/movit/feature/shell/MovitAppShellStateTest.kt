package com.movit.feature.shell

import com.movit.feature.account.AuthBootstrapContext
import com.movit.feature.account.MovitAuthEffect
import com.movit.feature.account.MovitProfileEffect
import com.movit.feature.explore.MovitExploreEffect
import com.movit.feature.home.MovitHomeEffect
import com.movit.feature.reports.MovitReportsEffect
import com.movit.feature.train.MovitTrainEffect
import kotlin.test.Test
import kotlin.test.assertEquals

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
            onboardingCompleted = true,
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
            onboardingCompleted = true,
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
            onboardingCompleted = true,
        )
        assertEquals(emptyList<MovitInnerRoute>(), stack)
    }

    @Test
    fun resolveStartupInnerStack_signedInWithoutOnboarding_opensOnboarding() {
        val stack = MovitAppShellViewModel.resolveStartupInnerStack(
            bootstrap = AuthBootstrapContext(
                movitDataInstalled = true,
                hasActiveSession = true,
                introSeen = false,
            ),
            onboardingCompleted = false,
        )
        assertEquals(listOf(MovitInnerRoute.ProfileOnboarding), stack)
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
    fun homeOpenProfile_changesDestination() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.HomeEffectReceived(MovitHomeEffect.OpenProfile))
        assertEquals(MovitAppDestination.Profile, viewModel.state.value.selectedDestination)
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
    fun trainOpenSessionPreview_pushesWorkoutSessionInnerRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.TrainEffectReceived(MovitTrainEffect.OpenSessionPreview))
        assertEquals(
            MovitInnerRoute.WorkoutSession("preview"),
            viewModel.state.value.currentInnerRoute,
        )
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
        assertEquals(MovitInnerRoute.Assessment, viewModel.state.value.currentInnerRoute)
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
        assertEquals(MovitInnerRoute.Assessment, viewModel.state.value.currentInnerRoute)
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
    fun workoutSessionExerciseCard_pushesExercisePrepareWithWorkoutId() {
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

    @Test
    fun exercisePrepareWithWorkoutId_start_pushesWorkoutRunRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.InnerRoutePushed(
                MovitInnerRoute.ExercisePrepare("bodyweight-squat", workoutId = "preview"),
            ),
        )
        viewModel.onEvent(MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.WorkoutRun("preview")))
        assertEquals(
            MovitInnerRoute.WorkoutRun("preview"),
            viewModel.state.value.currentInnerRoute,
        )
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
    fun workoutSessionStart_pushesWorkoutCustomizeRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.WorkoutSession("preview")))
        viewModel.onEvent(MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.WorkoutCustomize("preview")))
        assertEquals(
            MovitInnerRoute.WorkoutCustomize("preview"),
            viewModel.state.value.currentInnerRoute,
        )
    }

    @Test
    fun workoutCustomizeStart_pushesWorkoutRunRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.WorkoutCustomize("preview")))
        viewModel.onEvent(MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.WorkoutRun("preview")))
        assertEquals(
            MovitInnerRoute.WorkoutRun("preview"),
            viewModel.state.value.currentInnerRoute,
        )
    }
}
