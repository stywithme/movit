package com.movit.core.data.outbox

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.movit.core.data.MovitData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val replayScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun registerOutboxConnectivityReplay(context: Context) {
    val connectivityManager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return

    if (outboxNetworkCallback != null) return

    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        .build()

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            replayPendingOutboxIfInstalled()
        }
    }

    outboxNetworkCallback = callback
    connectivityManager.registerNetworkCallback(request, callback)
}

private var outboxNetworkCallback: ConnectivityManager.NetworkCallback? = null

internal fun replayPendingOutboxIfInstalled() {
    if (!MovitData.isInstalled) return
    replayScope.launch {
        runCatching { MovitData.offlineWrites.replayPending() }
    }
}
