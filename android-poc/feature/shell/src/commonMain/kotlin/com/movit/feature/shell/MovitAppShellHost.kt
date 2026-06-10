package com.movit.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Android / shared activity root for the Movit shell — wires [MovitAppShellViewModel] and system back.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MovitAppShellHost(
    legacyAuthExitEnabled: Boolean = false,
    onHostBackPressed: () -> Unit = {},
    onLaunchLegacyTraining: (MovitAppShellEffect.LaunchLegacyCameraTraining) -> Boolean = { false },
    onLaunchLegacySubscription: () -> Boolean = { false },
    onNavigateToLegacyAuth: () -> Boolean = { false },
) {
    val shellViewModel = viewModel { MovitAppShellViewModel(legacyAuthExitEnabled) }

    BackHandler {
        if (!shellViewModel.handleSystemBack()) {
            onHostBackPressed()
        }
    }

    MovitAppShellRoute(
        shellViewModel = shellViewModel,
        onLaunchLegacyTraining = onLaunchLegacyTraining,
        onLaunchLegacySubscription = onLaunchLegacySubscription,
        onNavigateToLegacyAuth = onNavigateToLegacyAuth,
    )
}
