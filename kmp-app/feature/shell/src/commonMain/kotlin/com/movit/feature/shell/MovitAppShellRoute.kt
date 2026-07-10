package com.movit.feature.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onLaunchPlatformSubscription: (restorePurchases: Boolean) -> Boolean = { false },
    onShareText: (subject: String, text: String) -> Boolean = { _, _ -> false },
) {
    val shellState by shellViewModel.state.collectAsState()

    ShellSyncLifecycleEffects(shellViewModel)

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
                onLaunchPlatformSubscription = onLaunchPlatformSubscription,
                onShareText = onShareText,
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
    onLaunchPlatformSubscription: (restorePurchases: Boolean) -> Boolean,
    onShareText: (subject: String, text: String) -> Boolean,
) {
    val state by shellViewModel.state.collectAsState()
    val language = LocalMovitLanguage.current

    var deferredDataRefresh by remember { mutableStateOf(false) }

    LaunchedEffect(state.dataRevision) {
        if (state.dataRevision == 0) return@LaunchedEffect
        if (state.innerStack.isNotEmpty()) {
            deferredDataRefresh = true
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        refreshVisibleTab(
            destination = state.selectedDestination,
            homeViewModel = homeViewModel,
            trainViewModel = trainViewModel,
            exploreViewModel = exploreViewModel,
            reportsViewModel = reportsViewModel,
        )
        deferredDataRefresh = false
    }

    LaunchedEffect(state.innerStack.isEmpty(), deferredDataRefresh) {
        if (!state.innerStack.isEmpty() || !deferredDataRefresh || state.dataRevision == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(300)
        refreshVisibleTab(
            destination = state.selectedDestination,
            homeViewModel = homeViewModel,
            trainViewModel = trainViewModel,
            exploreViewModel = exploreViewModel,
            reportsViewModel = reportsViewModel,
        )
        deferredDataRefresh = false
    }

    LaunchedEffect(state.localeRevision, state.selectedDestination) {
        if (state.localeRevision == 0) return@LaunchedEffect
        when (state.selectedDestination) {
            MovitAppDestination.Home -> homeViewModel.load(isRefresh = false)
            MovitAppDestination.Train -> trainViewModel.load(isRefresh = false)
            MovitAppDestination.Explore -> exploreViewModel.load(isRefresh = false)
            MovitAppDestination.Reports -> reportsViewModel.load(isRefresh = false)
            MovitAppDestination.Profile -> Unit
        }
    }

    LaunchedEffect(shellViewModel, snackbarHostState, language) {
        shellViewModel.effects.collectLatest { effect ->
            when (effect) {
                is MovitAppShellEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is MovitAppShellEffect.ShowLocalizedMessage -> {
                    snackbarHostState.showSnackbar(localizedString(language, effect.key))
                }
                is MovitAppShellEffect.ShareText -> {
                    if (!onShareText(effect.subject, effect.text)) {
                        snackbarHostState.showSnackbar(
                            localizedString(language, "program_flow_share_coming_soon"),
                        )
                    }
                }
                is MovitAppShellEffect.LaunchPlatformSubscription -> {
                    if (!onLaunchPlatformSubscription(effect.restorePurchases)) {
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

private suspend fun refreshVisibleTab(
    destination: MovitAppDestination,
    homeViewModel: MovitHomeViewModel,
    trainViewModel: MovitTrainViewModel,
    exploreViewModel: MovitExploreViewModel,
    reportsViewModel: MovitReportsViewModel,
) {
    when (destination) {
        MovitAppDestination.Home -> homeViewModel.load(isRefresh = false)
        MovitAppDestination.Train -> trainViewModel.load(isRefresh = false)
        MovitAppDestination.Explore -> exploreViewModel.load(isRefresh = false)
        MovitAppDestination.Reports -> reportsViewModel.load(isRefresh = false)
        MovitAppDestination.Profile -> Unit
    }
}
