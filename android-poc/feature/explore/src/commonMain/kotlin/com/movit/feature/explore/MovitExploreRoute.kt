package com.movit.feature.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun MovitExploreRoute(
    modifier: Modifier = Modifier,
    viewModel: MovitExploreViewModel = viewModel { MovitExploreViewModel() },
    onEffect: (MovitExploreEffect) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.loadInitial()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MovitExploreEffect.NavigateToExercise -> {
                    onEffect(MovitExploreEffect.NavigateToItem(effect.id, ExploreItemType.Exercise))
                }
                else -> onEffect(effect)
            }
        }
    }

    MovitExploreScreen(
        state = state,
        onEvent = { event ->
            when (event) {
                MovitExploreEvent.RetryClicked -> scope.launch { viewModel.load(isRefresh = false) }
                MovitExploreEvent.RefreshRequested -> scope.launch { viewModel.load(isRefresh = true) }
                else -> viewModel.onEvent(event)
            }
        },
        modifier = modifier,
    )
}
