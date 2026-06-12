package com.movit.core.data.platform

import com.movit.core.data.outbox.replayPendingOutboxIfInstalled
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
@OptIn(ExperimentalForeignApi::class)
internal object IosNetworkMonitor {
    private val monitor = nw_path_monitor_create()

    var isOnline: Boolean = true
        private set

    private var started = false
    private var wasOffline = false

    fun ensureStarted() {
        if (started) return
        started = true
        nw_path_monitor_set_update_handler(monitor) { path ->
            val online = nw_path_get_status(path) == nw_path_status_satisfied
            if (online && wasOffline) {
                replayPendingOutboxIfInstalled()
                MovitConnectivitySignals.notifyConnectivityRestored()
            }
            wasOffline = !online
            isOnline = online
        }
        nw_path_monitor_start(monitor)
    }
}
