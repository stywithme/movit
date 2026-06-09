package com.movit.feature.shell

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
    fun innerRoutePopped_removesTopRoute() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.ExercisesLibrary))
        viewModel.onEvent(MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.WorkoutsLibrary))
        viewModel.onEvent(MovitAppShellEvent.InnerRoutePopped)
        assertEquals(MovitInnerRoute.ExercisesLibrary, viewModel.state.value.currentInnerRoute)
    }
}
