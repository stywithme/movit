package com.movit.feature.shell

import com.movit.feature.home.MovitHomeEffect
import com.movit.feature.train.MovitTrainEffect

sealed interface MovitAppShellEvent {
    data class DestinationSelected(val destination: MovitAppDestination) : MovitAppShellEvent
    data class ExploreItemSelected(val itemId: String) : MovitAppShellEvent
    data class HomeEffectReceived(val effect: MovitHomeEffect) : MovitAppShellEvent
    data class TrainEffectReceived(val effect: MovitTrainEffect) : MovitAppShellEvent
}
