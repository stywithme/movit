package com.movit.core.data.outbox

import com.movit.core.data.MovitData
import com.movit.core.data.platform.IosNetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val replayScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

fun registerOutboxConnectivityReplay() {
    IosNetworkMonitor.ensureStarted()
}

internal fun replayPendingOutboxIfInstalled() {
    if (!MovitData.isInstalled) return
    replayScope.launch {
        runCatching {
            MovitData.offlineWrites.replayPending(OutboxReplayAcquisition.TrySkipIfBusy)
        }
    }
}
