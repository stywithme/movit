package com.movit.feature.shell

import com.movit.feature.account.MovitAssessmentEffect
import com.movit.feature.account.MovitAuthEffect
import com.movit.feature.account.MovitLevelEffect
import com.movit.feature.account.MovitOnboardingEffect
import com.movit.feature.account.MovitProfileEffect
import com.movit.feature.explore.MovitExploreEffect
import com.movit.feature.home.MovitHomeEffect
import com.movit.feature.reports.MovitReportsEffect
import com.movit.feature.train.MovitTrainEffect

sealed interface MovitAppShellEvent {
    /** System back (Android hardware/gesture). Handled by [MovitAppShellViewModel.handleSystemBack]. */
    data object BackPressed : MovitAppShellEvent
    data class DestinationSelected(val destination: MovitAppDestination) : MovitAppShellEvent
    data class InnerRoutePushed(val route: MovitInnerRoute) : MovitAppShellEvent
    data object InnerRoutePopped : MovitAppShellEvent
    data class ExploreEffectReceived(val effect: MovitExploreEffect) : MovitAppShellEvent
    data class ExploreItemSelected(val itemId: String) : MovitAppShellEvent
    data class HomeEffectReceived(val effect: MovitHomeEffect) : MovitAppShellEvent
    data class TrainEffectReceived(val effect: MovitTrainEffect) : MovitAppShellEvent
    data class ReportsEffectReceived(val effect: MovitReportsEffect) : MovitAppShellEvent
    data class HeaderUserNameUpdated(val userName: String) : MovitAppShellEvent
    data class ProfileEffectReceived(val effect: MovitProfileEffect) : MovitAppShellEvent
    data class AuthEffectReceived(val effect: MovitAuthEffect) : MovitAppShellEvent
    data class OnboardingEffectReceived(val effect: MovitOnboardingEffect) : MovitAppShellEvent
    data class AssessmentEffectReceived(val effect: MovitAssessmentEffect) : MovitAppShellEvent
    data class LevelEffectReceived(val effect: MovitLevelEffect) : MovitAppShellEvent
}
