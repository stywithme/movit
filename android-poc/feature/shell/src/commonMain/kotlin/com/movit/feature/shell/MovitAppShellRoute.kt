package com.movit.feature.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movit.core.data.MovitData
import com.movit.designsystem.MovitTheme
import com.movit.designsystem.MovitThemeMode
import com.movit.resources.LocalMovitLanguage
import com.movit.resources.MovitLocaleProvider
import com.movit.resources.localizedString
import com.movit.feature.account.MovitProfileViewModel
import com.movit.feature.explore.MovitExploreViewModel
import com.movit.feature.home.MovitHomeViewModel
import com.movit.feature.reports.MovitReportsViewModel
import com.movit.feature.train.MovitTrainEffect
import com.movit.feature.train.MovitTrainViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MovitAppShellRoute(
    modifier: Modifier = Modifier,
    shellViewModel: MovitAppShellViewModel = viewModel(),
    homeViewModel: MovitHomeViewModel = viewModel { MovitHomeViewModel() },
    trainViewModel: MovitTrainViewModel = viewModel { MovitTrainViewModel() },
    exploreViewModel: MovitExploreViewModel = viewModel { MovitExploreViewModel() },
    reportsViewModel: MovitReportsViewModel = viewModel { MovitReportsViewModel() },
    profileViewModel: MovitProfileViewModel = viewModel { MovitProfileViewModel() },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onTrainEffect: (MovitTrainEffect) -> Boolean = { false },
    onLaunchLegacyTraining: (MovitAppShellEffect.LaunchLegacyCameraTraining) -> Boolean = { false },
    onLaunchLegacySubscription: () -> Boolean = { false },
) {
    val shellState by shellViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (MovitData.isInstalled) {
            MovitData.plan.refreshActiveUserProgramId()
        }
    }

    val language = remember(shellState.localeRevision) {
        if (MovitData.isInstalled) {
            MovitData.requirePlatform().preferredLanguage()
        } else {
            "en"
        }
    }

    MovitLocaleProvider(languageCode = language) {
        MovitTheme(themeMode = MovitThemeMode.fromStorageKey(shellState.themeMode)) {
            MovitAppShellRouteContent(
                shellViewModel = shellViewModel,
                homeViewModel = homeViewModel,
                trainViewModel = trainViewModel,
                exploreViewModel = exploreViewModel,
                reportsViewModel = reportsViewModel,
                profileViewModel = profileViewModel,
                modifier = modifier,
                snackbarHostState = snackbarHostState,
                onTrainEffect = onTrainEffect,
                onLaunchLegacyTraining = onLaunchLegacyTraining,
                onLaunchLegacySubscription = onLaunchLegacySubscription,
            )
        }
    }
}

@Composable
private fun MovitAppShellRouteContent(
    shellViewModel: MovitAppShellViewModel,
    homeViewModel: MovitHomeViewModel,
    trainViewModel: MovitTrainViewModel,
    exploreViewModel: MovitExploreViewModel,
    reportsViewModel: MovitReportsViewModel,
    profileViewModel: MovitProfileViewModel,
    modifier: Modifier,
    snackbarHostState: SnackbarHostState,
    onTrainEffect: (MovitTrainEffect) -> Boolean,
    onLaunchLegacyTraining: (MovitAppShellEffect.LaunchLegacyCameraTraining) -> Boolean,
    onLaunchLegacySubscription: () -> Boolean,
) {
    val state by shellViewModel.state.collectAsStateWithLifecycle()
    val language = LocalMovitLanguage.current

    LaunchedEffect(shellViewModel, snackbarHostState, language) {
        shellViewModel.effects.collectLatest { effect ->
            when (effect) {
                is MovitAppShellEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is MovitAppShellEffect.ShowLocalizedMessage -> {
                    snackbarHostState.showSnackbar(localizedString(language, effect.key))
                }
                is MovitAppShellEffect.LaunchLegacyCameraTraining -> {
                    if (!onLaunchLegacyTraining(effect)) {
                        snackbarHostState.showSnackbar(
                            localizedString(language, "prepare_training_bridge_unavailable"),
                        )
                    }
                }
                MovitAppShellEffect.LaunchLegacySubscription -> {
                    if (!onLaunchLegacySubscription()) {
                        snackbarHostState.showSnackbar(
                            localizedString(language, "profile_subscription_ios_unavailable"),
                        )
                    }
                }
            }
        }
    }

    MovitAppShell(
        state = state,
        onEvent = shellViewModel::onEvent,
        homeViewModel = homeViewModel,
        trainViewModel = trainViewModel,
        exploreViewModel = exploreViewModel,
        reportsViewModel = reportsViewModel,
        profileViewModel = profileViewModel,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onTrainEffect = onTrainEffect,
        onShellEffect = shellViewModel::emitShellEffect,
    )
}
