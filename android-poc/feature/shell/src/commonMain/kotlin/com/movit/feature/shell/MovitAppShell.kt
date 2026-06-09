package com.movit.feature.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitFloatingNavBar
import com.movit.feature.explore.MovitExploreRoute
import com.movit.feature.explore.MovitExploreViewModel
import com.movit.feature.home.MovitHomeRoute
import com.movit.feature.home.MovitHomeViewModel
import com.movit.feature.reports.MovitReportsRoute
import com.movit.feature.reports.MovitReportsViewModel
import com.movit.feature.account.MovitProfileRoute
import com.movit.feature.account.MovitProfileViewModel
import com.movit.feature.train.MovitTrainEffect
import com.movit.feature.train.MovitTrainRoute
import com.movit.feature.train.MovitTrainViewModel

@Composable
fun MovitAppShell(
    state: MovitAppShellState,
    onEvent: (MovitAppShellEvent) -> Unit,
    homeViewModel: MovitHomeViewModel,
    trainViewModel: MovitTrainViewModel,
    exploreViewModel: MovitExploreViewModel,
    reportsViewModel: MovitReportsViewModel,
    profileViewModel: MovitProfileViewModel,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onTrainEffect: (MovitTrainEffect) -> Boolean = { false },
    onShellEffect: (MovitAppShellEffect) -> Unit = {},
) {
    val innerRoute = state.currentInnerRoute
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (innerRoute == null) {
                Box(
                    modifier = Modifier.padding(bottom = MovitSpacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    MovitFloatingNavBar(
                        selected = state.selectedDestination.toFloatingNav(),
                        destinations = MovitShellFloatingDestinations,
                        onDestinationSelected = { destination ->
                            onEvent(MovitAppShellEvent.DestinationSelected(destination.toAppDestination()))
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (innerRoute != null) {
                MovitInnerHost(
                    route = innerRoute,
                    onBack = { onEvent(MovitAppShellEvent.InnerRoutePopped) },
                    onNavigate = { onEvent(MovitAppShellEvent.InnerRoutePushed(it)) },
                    onShellEvent = onEvent,
                    onShellEffect = onShellEffect,
                    modifier = Modifier.fillMaxSize(),
                )
            } else when (state.selectedDestination) {
                MovitAppDestination.Home -> {
                    MovitHomeRoute(
                        viewModel = homeViewModel,
                        modifier = Modifier.fillMaxSize(),
                        onEffect = { onEvent(MovitAppShellEvent.HomeEffectReceived(it)) },
                        onUserNameUpdated = { onEvent(MovitAppShellEvent.HeaderUserNameUpdated(it)) },
                    )
                }
                MovitAppDestination.Train -> {
                    MovitTrainRoute(
                        viewModel = trainViewModel,
                        modifier = Modifier.fillMaxSize(),
                        onEffect = { effect ->
                            if (!onTrainEffect(effect)) {
                                onEvent(MovitAppShellEvent.TrainEffectReceived(effect))
                            }
                        },
                    )
                }
                MovitAppDestination.Explore -> {
                    MovitExploreRoute(
                        viewModel = exploreViewModel,
                        modifier = Modifier.fillMaxSize(),
                        onEffect = { onEvent(MovitAppShellEvent.ExploreEffectReceived(it)) },
                    )
                }
                MovitAppDestination.Profile -> {
                    MovitProfileRoute(
                        viewModel = profileViewModel,
                        modifier = Modifier.fillMaxSize(),
                        onEffect = { onEvent(MovitAppShellEvent.ProfileEffectReceived(it)) },
                    )
                }
                MovitAppDestination.Reports -> {
                    MovitReportsRoute(
                        viewModel = reportsViewModel,
                        userName = state.headerUserName,
                        modifier = Modifier.fillMaxSize(),
                        onEffect = { onEvent(MovitAppShellEvent.ReportsEffectReceived(it)) },
                    )
                }
            }
        }
    }
}
