package com.movit.feature.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movit.feature.explore.MovitExploreViewModel
import com.movit.feature.home.MovitHomeViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MovitAppShellRoute(
    modifier: Modifier = Modifier,
    shellViewModel: MovitAppShellViewModel = viewModel(),
    homeViewModel: MovitHomeViewModel = viewModel { MovitHomeViewModel() },
    exploreViewModel: MovitExploreViewModel = viewModel { MovitExploreViewModel() },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val state by shellViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(shellViewModel, snackbarHostState) {
        shellViewModel.effects.collectLatest { effect ->
            when (effect) {
                is MovitAppShellEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    MovitAppShell(
        state = state,
        onEvent = shellViewModel::onEvent,
        homeViewModel = homeViewModel,
        exploreViewModel = exploreViewModel,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}
