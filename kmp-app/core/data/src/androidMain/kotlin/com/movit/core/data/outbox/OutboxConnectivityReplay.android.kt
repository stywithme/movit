package com.movit.core.data.outbox

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.movit.core.data.MovitData
import com.movit.core.data.platform.MovitConnectivitySignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val replayScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private var wasOffline = false

fun registerOutboxConnectivityReplay(context: Context) {
    val appContext = context.applicationContext
    val connectivityManager = appContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return

    if (outboxNetworkCallback != null) return

    wasOffline = !hasValidatedInternet(connectivityManager)

    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        .build()

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // P2.11: single path — replay outbox then shell sync via connectivity signal (no requestNow).
            replayPendingOutboxIfInstalled()
            if (wasOffline) {
                MovitConnectivitySignals.notifyConnectivityRestored()
            }
            wasOffline = false
        }

        override fun onLost(network: Network) {
            wasOffline = true
        }
    }

    outboxNetworkCallback = callback
    connectivityManager.registerNetworkCallback(request, callback)
}

private fun hasValidatedInternet(connectivityManager: ConnectivityManager): Boolean {
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private var outboxNetworkCallback: ConnectivityManager.NetworkCallback? = null

internal fun replayPendingOutboxIfInstalled() {
    if (!MovitData.isInstalled) return
    replayScope.launch {
        runCatching {
            MovitData.offlineWrites.replayPending(OutboxReplayAcquisition.TrySkipIfBusy)
        }
    }
}
