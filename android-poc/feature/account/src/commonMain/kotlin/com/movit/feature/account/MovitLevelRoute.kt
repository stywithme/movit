package com.movit.feature.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun MovitLevelRoute(
    modifier: Modifier = Modifier,
    viewModel: MovitLevelViewModel = viewModel { MovitLevelViewModel() },
    onEffect: (MovitLevelEffect) -> Unit = {},
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

    MovitLevelScreen(
        state = state,
        onEvent = { event ->
            when (event) {
                MovitLevelEvent.RetryClicked -> scope.launch { viewModel.load() }
                else -> viewModel.onEvent(event)
            }
        },
        modifier = modifier,
    )
}
