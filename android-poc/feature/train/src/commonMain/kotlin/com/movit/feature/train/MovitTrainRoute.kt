package com.movit.feature.train

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun MovitTrainRoute(
    modifier: Modifier = Modifier,
    viewModel: MovitTrainViewModel = viewModel { MovitTrainViewModel() },
    onEffect: (MovitTrainEffect) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.loadInitial()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            onEffect(effect)
        }
    }

    MovitTrainScreen(
        state = state,
        onEvent = { event ->
            when (event) {
                MovitTrainEvent.RetryClicked -> scope.launch { viewModel.load(isRefresh = false) }
                MovitTrainEvent.RefreshRequested -> scope.launch { viewModel.load(isRefresh = true) }
                else -> viewModel.onEvent(event)
            }
        },
        modifier = modifier,
    )
}
