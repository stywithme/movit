package com.movit.feature.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.LocalMovitFloatingNavContentInset
import com.movit.designsystem.components.MovitFloatingNavBar
import com.movit.designsystem.components.MovitFloatingNavContentInset
import com.movit.feature.explore.MovitExploreRoute
import com.movit.feature.explore.MovitExploreViewModel
import com.movit.feature.home.MovitHomeRoute
import com.movit.feature.home.MovitHomeViewModel
import com.movit.feature.reports.MovitReportsRoute
import com.movit.feature.reports.MovitReportsViewModel
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
    val interceptSystemBack = innerRoute != null || state.selectedDestination != MovitAppDestination.Home
    val navigationEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = interceptSystemBack,
        onBackCompleted = { onEvent(MovitAppShellEvent.BackPressed) },
    )
    val showFloatingNav = innerRoute == null
    val floatingNavInset = if (showFloatingNav) MovitFloatingNavContentInset else 0.dp

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        CompositionLocalProvider(LocalMovitFloatingNavContentInset provides floatingNavInset) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                if (innerRoute != null) {
                    MovitInnerHost(
                        route = innerRoute,
                        profileViewModel = profileViewModel,
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
                            userName = state.headerUserName,
                            onProfileClick = { onEvent(MovitAppShellEvent.TabProfileClicked) },
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
                            userName = state.headerUserName,
                            onProfileClick = { onEvent(MovitAppShellEvent.TabProfileClicked) },
                            onEffect = { onEvent(MovitAppShellEvent.ExploreEffectReceived(it)) },
                        )
                    }
                    MovitAppDestination.Reports -> {
                        MovitReportsRoute(
                            viewModel = reportsViewModel,
                            userName = state.headerUserName,
                            onProfileClick = { onEvent(MovitAppShellEvent.TabProfileClicked) },
                            modifier = Modifier.fillMaxSize(),
                            onEffect = { onEvent(MovitAppShellEvent.ReportsEffectReceived(it)) },
                        )
                    }
                    MovitAppDestination.Profile -> {
                        LaunchedEffect(Unit) {
                            onEvent(MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.Profile))
                        }
                    }
                }

                if (showFloatingNav) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = MovitSpacing.md),
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
            }
        }
    }
}
