package com.movit.feature.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MovitAssessmentRoute(
    modifier: Modifier = Modifier,
    viewModel: MovitAssessmentViewModel = viewModel { MovitAssessmentViewModel() },
    onEffect: (MovitAssessmentEffect) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            onEffect(effect)
        }
    }

    MovitAssessmentScreen(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}
