package com.movit.feature.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.movit.core.data.MovitData
import com.movit.core.data.billing.IosStoreKitBridgeRegistry
import com.movit.core.data.outbox.registerOutboxConnectivityReplay
import com.movit.core.data.platform.IosMovitPlatform
import com.movit.designsystem.platform.installMovitCoilImageLoader
import com.movit.feature.account.MovitProfileViewModel
import com.movit.feature.explore.MovitExploreViewModel
import com.movit.feature.home.MovitHomeViewModel
import com.movit.feature.reports.MovitReportsViewModel
import com.movit.feature.train.MovitTrainViewModel
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS entry point, consumed by iosApp/ (Swift) through the MovitApp framework.
 *
 * ViewModels are created with [remember] (not [androidx.lifecycle.viewmodel.compose.viewModel])
 * because iOS does not provide an Android-style ViewModelStoreOwner during first composition.
 * Shared routes use [androidx.compose.runtime.collectAsState] instead of
 * [androidx.lifecycle.compose.collectAsStateWithLifecycle] for the same reason.
 *
 * [MovitData] installs after the Compose runtime is ready so Koin/SQLDelight do not run before
 * the UI host is alive (avoids native startup crashes on iOS).
 */
fun MainViewController(): UIViewController =
    ComposeUIViewController(
        configure = { enforceStrictPlistSanityCheck = false },
    ) {
        var runtimeReady by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            installIosShellRuntime()
            runtimeReady = true
        }

        if (runtimeReady) {
            MovitAppShellHost()
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }

@Composable
private fun MovitAppShellHost() {
    val scope = rememberCoroutineScope()

    val shellViewModel = remember { MovitAppShellViewModel() }
    val homeViewModel = remember { MovitHomeViewModel() }
    val trainViewModel = remember { MovitTrainViewModel() }
    val exploreViewModel = remember { MovitExploreViewModel() }
    val reportsViewModel = remember { MovitReportsViewModel() }
    val profileViewModel = remember { MovitProfileViewModel() }

    MovitAppShellRoute(
        shellViewModel = shellViewModel,
        homeViewModel = homeViewModel,
        trainViewModel = trainViewModel,
        exploreViewModel = exploreViewModel,
        reportsViewModel = reportsViewModel,
        profileViewModel = profileViewModel,
        onShareText = { _, text -> shareTextOnIos(subject = "", text = text) },
        onLaunchPlatformSubscription = { restorePurchases ->
            if (IosStoreKitBridgeRegistry.current()?.isAvailable != true) {
                false
            } else {
                scope.launch {
                    IosSubscriptionCoordinator.launchSubscriptionFlow(
                        restorePurchases = restorePurchases,
                    )
                }
                true
            }
        },
    )
}

private var iosShellRuntimeInstalled = false

private fun installIosShellRuntime() {
    if (iosShellRuntimeInstalled) return
    iosShellRuntimeInstalled = true

    val platform = IosMovitPlatform()
    installMovitCoilImageLoader()
    IosSubscriptionCoordinator.ensureRenewalHandlerInstalled()
    MovitData.install(platform)
    registerOutboxConnectivityReplay()
    IosLegacyWorkoutSyncDrain.drainPendingExecutions()
}
