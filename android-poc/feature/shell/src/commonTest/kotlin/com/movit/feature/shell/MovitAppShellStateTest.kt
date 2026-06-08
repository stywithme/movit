package com.movit.feature.shell

import com.movit.feature.home.MovitHomeEffect
import com.movit.feature.train.MovitTrainEffect
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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
    fun exploreItemSelected_emitsEffect() = runBlocking {
        val viewModel = MovitAppShellViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitAppShellEvent.ExploreItemSelected("ex-squat"))
        val effect = effectDeferred.await()
        assertTrue(effect is MovitAppShellEffect.ShowMessage)
        assertEquals("Opening ex-squat…", effect.message)
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
    fun trainOpenSessionPreview_emitsPlaceholderMessage() = runBlocking {
        val viewModel = MovitAppShellViewModel()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitAppShellEvent.TrainEffectReceived(MovitTrainEffect.OpenSessionPreview))
        val effect = effectDeferred.await()
        assertTrue(effect is MovitAppShellEffect.ShowMessage)
        assertEquals(
            "Session flow starts in a later phase.",
            effect.message,
        )
    }
}
