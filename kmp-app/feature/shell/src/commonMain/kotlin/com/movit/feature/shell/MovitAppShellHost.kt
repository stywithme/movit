package com.movit.feature.shell

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * Android / shared activity root for the Movit shell — wires [MovitAppShellViewModel] and system back.
 */
@Composable
fun MovitAppShellHost(
    onHostBackPressed: () -> Unit = {},
    onLaunchPlatformSubscription: (restorePurchases: Boolean) -> Boolean = { false },
    onShareText: (subject: String, text: String) -> Boolean = { _, _ -> false },
) {
    val shellViewModel = viewModel { MovitAppShellViewModel() }
    val navigationEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navigationEventState,
        onBackCompleted = {
            if (!shellViewModel.handleSystemBack()) {
                onHostBackPressed()
            }
        },
    )

    MovitAppShellRoute(
        shellViewModel = shellViewModel,
        onLaunchPlatformSubscription = onLaunchPlatformSubscription,
        onShareText = onShareText,
    )
}
