package com.movit.feature.shell

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import com.movit.core.data.MovitData
import com.movit.core.data.billing.IosStoreKitBridgeRegistry
import com.movit.core.data.outbox.registerOutboxConnectivityReplay
import com.movit.core.data.platform.IosMovitPlatform
import com.movit.designsystem.platform.installMovitCoilImageLoader
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS entry point, consumed by iosApp/ (Swift) through the MovitApp framework.
 */
fun MainViewController(): UIViewController = ComposeUIViewController(
    configure = { enforceStrictPlistSanityCheck = false },
) {
    val platform = remember { IosMovitPlatform() }
    val scope = rememberCoroutineScope()

    remember {
        installMovitCoilImageLoader()
        IosSubscriptionCoordinator.ensureRenewalHandlerInstalled()
        MovitData.install(platform)
        registerOutboxConnectivityReplay()
        IosLegacyWorkoutSyncDrain.drainPendingExecutions()
    }

    MovitAppShellRoute(
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
