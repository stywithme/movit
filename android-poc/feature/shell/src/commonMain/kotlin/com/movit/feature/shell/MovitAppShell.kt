package com.movit.feature.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.movit.feature.explore.MovitExploreRoute
import com.movit.feature.explore.MovitExploreViewModel
import com.movit.feature.home.MovitHomeRoute
import com.movit.feature.home.MovitHomeViewModel
import com.movit.feature.train.MovitTrainRoute
import com.movit.feature.train.MovitTrainViewModel

@Composable
fun MovitAppShell(
    state: MovitAppShellState,
    onEvent: (MovitAppShellEvent) -> Unit,
    homeViewModel: MovitHomeViewModel,
    trainViewModel: MovitTrainViewModel,
    exploreViewModel: MovitExploreViewModel,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            MovitNavigationBar(
                destinations = MovitAppDestination.entries,
                selectedDestination = state.selectedDestination,
                onDestinationSelected = { onEvent(MovitAppShellEvent.DestinationSelected(it)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (state.selectedDestination) {
                MovitAppDestination.Home -> {
                    MovitHomeRoute(
                        viewModel = homeViewModel,
                        modifier = Modifier.fillMaxSize(),
                        onEffect = { onEvent(MovitAppShellEvent.HomeEffectReceived(it)) },
                    )
                }
                MovitAppDestination.Train -> {
                    MovitTrainRoute(
                        viewModel = trainViewModel,
                        modifier = Modifier.fillMaxSize(),
                        onEffect = { onEvent(MovitAppShellEvent.TrainEffectReceived(it)) },
                    )
                }
                MovitAppDestination.Explore -> {
                    MovitExploreRoute(
                        viewModel = exploreViewModel,
                        modifier = Modifier.fillMaxSize(),
                        onNavigateToExercise = { id ->
                            onEvent(MovitAppShellEvent.ExploreItemSelected(id))
                        },
                    )
                }
                else -> {
                    MovitPlaceholderScreen(
                        destination = state.selectedDestination,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
