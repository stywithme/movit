package com.movit.feature.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun MovitProfileRoute(
    modifier: Modifier = Modifier,
    viewModel: MovitProfileViewModel = viewModel { MovitProfileViewModel() },
    onEffect: (MovitProfileEffect) -> Unit = {},
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

    if (state.showSubscription) {
        MovitSubscriptionScreen(
            isPro = state.profile?.isPro == true,
            onBack = { viewModel.onEvent(MovitProfileEvent.CloseSubscriptionClicked) },
            onPrimaryAction = { viewModel.onEvent(MovitProfileEvent.SubscribeNowClicked) },
            onRestorePurchases = { viewModel.onEvent(MovitProfileEvent.RestorePurchasesClicked) },
            modifier = modifier,
        )
    } else {
        MovitProfileScreen(
            state = state,
            onEvent = { event ->
                when (event) {
                    MovitProfileEvent.RetryClicked -> scope.launch { viewModel.load() }
                    else -> viewModel.onEvent(event)
                }
            },
            modifier = modifier,
        )
    }
}
