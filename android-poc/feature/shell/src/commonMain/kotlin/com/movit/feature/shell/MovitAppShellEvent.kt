package com.movit.feature.shell

import com.movit.feature.explore.MovitExploreEffect
import com.movit.feature.home.MovitHomeEffect
import com.movit.feature.reports.MovitReportsEffect
import com.movit.feature.train.MovitTrainEffect

sealed interface MovitAppShellEvent {
    data class DestinationSelected(val destination: MovitAppDestination) : MovitAppShellEvent
    data class InnerRoutePushed(val route: MovitInnerRoute) : MovitAppShellEvent
    data object InnerRoutePopped : MovitAppShellEvent
    data class ExploreEffectReceived(val effect: MovitExploreEffect) : MovitAppShellEvent
    data class ExploreItemSelected(val itemId: String) : MovitAppShellEvent
    data class HomeEffectReceived(val effect: MovitHomeEffect) : MovitAppShellEvent
    data class TrainEffectReceived(val effect: MovitTrainEffect) : MovitAppShellEvent
    data class ReportsEffectReceived(val effect: MovitReportsEffect) : MovitAppShellEvent
    data class HeaderUserNameUpdated(val userName: String) : MovitAppShellEvent
}
