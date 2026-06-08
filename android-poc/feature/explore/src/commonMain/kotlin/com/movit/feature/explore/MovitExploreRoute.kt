package com.movit.feature.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun MovitExploreRoute(
    modifier: Modifier = Modifier,
    viewModel: MovitExploreViewModel = viewModel { MovitExploreViewModel() },
    onNavigateToExercise: (String) -> Unit = {},
    useRtlPreview: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.loadInitial()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MovitExploreEffect.NavigateToExercise -> onNavigateToExercise(effect.id)
                is MovitExploreEffect.ShowMessage -> Unit
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
        useRtlPreview = useRtlPreview,
    )
}
