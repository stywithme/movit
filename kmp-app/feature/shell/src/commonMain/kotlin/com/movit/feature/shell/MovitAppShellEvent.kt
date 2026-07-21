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
    /** Drop Prepare+Training for the completed run and place ReportDetail as the journey end. */
    data class ReplaceWorkoutJourneyWithReport(
        val reportId: String,
        val returnTarget: com.movit.feature.library.ReturnTarget? = null,
        val doneTarget: com.movit.feature.library.ReturnTarget? = null,
    ) : MovitAppShellEvent
    /** Save and exit / End workout — drop Prepare+Training, keep WorkoutSession (or tab). */
    data object ExitWorkoutJourney : MovitAppShellEvent
    data class NavigateReportReturn(
        val target: com.movit.feature.library.ReturnTarget,
        val clearInner: Boolean = true,
    ) : MovitAppShellEvent
    data class ExploreEffectReceived(val effect: MovitExploreEffect) : MovitAppShellEvent
    data class ExploreItemSelected(val itemId: String) : MovitAppShellEvent
    data class HomeEffectReceived(val effect: MovitHomeEffect) : MovitAppShellEvent
    data class TrainEffectReceived(val effect: MovitTrainEffect) : MovitAppShellEvent
    data class ReportsEffectReceived(val effect: MovitReportsEffect) : MovitAppShellEvent
    data class HeaderUserNameUpdated(val userName: String) : MovitAppShellEvent
    /** Open profile as inner route from Train / Explore / Reports headers. */
    data object TabProfileClicked : MovitAppShellEvent
    data class ProfileEffectReceived(val effect: MovitProfileEffect) : MovitAppShellEvent
    data class AuthEffectReceived(val effect: MovitAuthEffect) : MovitAppShellEvent
    data class OnboardingEffectReceived(val effect: MovitOnboardingEffect) : MovitAppShellEvent
    data class AssessmentEffectReceived(val effect: MovitAssessmentEffect) : MovitAppShellEvent
    data class LevelEffectReceived(val effect: MovitLevelEffect) : MovitAppShellEvent
    data object GuestOutboxAcceptClicked : MovitAppShellEvent
    data object GuestOutboxDiscardClicked : MovitAppShellEvent
    data object BootstrapRetryClicked : MovitAppShellEvent
    data object BootstrapContinuePartialClicked : MovitAppShellEvent
    data object SyncStatusSheetDismissed : MovitAppShellEvent
    data object SyncStatusSheetRequested : MovitAppShellEvent
    data object SyncNowClicked : MovitAppShellEvent
}
