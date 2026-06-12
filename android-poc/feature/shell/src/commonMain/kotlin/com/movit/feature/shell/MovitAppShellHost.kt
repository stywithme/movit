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
    onLaunchLegacySubscription: () -> Boolean = { false },
    onNavigateToLegacyAuth: () -> Boolean = { false },
    onShareText: (subject: String, text: String) -> Boolean = { _, _ -> false },
) {
    val shellViewModel = viewModel { MovitAppShellViewModel(legacyAuthExitEnabled) }

    BackHandler {
        if (!shellViewModel.handleSystemBack()) {
            onHostBackPressed()
        }
    }

    MovitAppShellRoute(
        shellViewModel = shellViewModel,
        onLaunchLegacySubscription = onLaunchLegacySubscription,
        onNavigateToLegacyAuth = onNavigateToLegacyAuth,
        onShareText = onShareText,
    )
}
