package com.movit.feature.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MovitAuthRoute(
    modifier: Modifier = Modifier,
    viewModel: MovitAuthViewModel = viewModel { MovitAuthViewModel() },
    onEffect: (MovitAuthEffect) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            onEffect(effect)
        }
    }

    MovitAuthScreen(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}
