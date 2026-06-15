package com.movit.feature.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitFloatPill
import com.movit.designsystem.components.MovitFloatPillVariant
import com.movit.resources.movitText
import kotlinx.coroutines.launch

@Composable
fun MovitProfileRoute(
    modifier: Modifier = Modifier,
    viewModel: MovitProfileViewModel = viewModel { MovitProfileViewModel() },
    onBack: (() -> Unit)? = null,
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

    val contentModifier = if (onBack != null) {
        Modifier.fillMaxSize()
    } else {
        modifier
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (state.showSubscription) {
            MovitSubscriptionScreen(
                isPro = state.profile?.isPro == true,
                onBack = { viewModel.onEvent(MovitProfileEvent.CloseSubscriptionClicked) },
                onPrimaryAction = { viewModel.onEvent(MovitProfileEvent.SubscribeNowClicked) },
                onRestorePurchases = { viewModel.onEvent(MovitProfileEvent.RestorePurchasesClicked) },
                modifier = contentModifier,
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
                modifier = contentModifier,
            )
        }

        if (onBack != null) {
            MovitFloatPill(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                variant = MovitFloatPillVariant.Outline,
                contentDescription = movitText("profile_back"),
                modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.md),
            )
        }
    }
}
