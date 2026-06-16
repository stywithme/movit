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
    onHostBackPressed: () -> Unit = {},
    onLaunchLegacySubscription: () -> Boolean = { false },
    onShareText: (subject: String, text: String) -> Boolean = { _, _ -> false },
) {
    val shellViewModel = viewModel { MovitAppShellViewModel() }

    BackHandler {
        if (!shellViewModel.handleSystemBack()) {
            onHostBackPressed()
        }
    }

    MovitAppShellRoute(
        shellViewModel = shellViewModel,
        onLaunchLegacySubscription = onLaunchLegacySubscription,
        onShareText = onShareText,
    )
}
