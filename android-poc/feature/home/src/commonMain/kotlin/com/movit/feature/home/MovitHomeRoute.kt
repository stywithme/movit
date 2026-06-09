package com.movit.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun MovitHomeRoute(
    modifier: Modifier = Modifier,
    viewModel: MovitHomeViewModel = viewModel { MovitHomeViewModel() },
    onEffect: (MovitHomeEffect) -> Unit = {},
    onUserNameUpdated: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.loadInitial()
    }

    LaunchedEffect(state.userName) {
        if (state.userName.isNotBlank()) {
            onUserNameUpdated(state.userName)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            onEffect(effect)
        }
    }

    MovitHomeScreen(
        state = state,
        onEvent = { event ->
            when (event) {
                MovitHomeEvent.RetryClicked -> scope.launch { viewModel.load() }
                else -> viewModel.onEvent(event)
            }
        },
        modifier = modifier,
    )
}
