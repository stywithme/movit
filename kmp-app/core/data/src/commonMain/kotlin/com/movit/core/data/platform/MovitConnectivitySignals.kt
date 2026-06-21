package com.movit.core.data.platform

/**
 * Optional shell hook when platform connectivity is restored (outbox replay runs separately).
 */
object MovitConnectivitySignals {
    private var onRestored: (() -> Unit)? = null

    fun setOnConnectivityRestored(handler: (() -> Unit)?) {
        onRestored = handler
    }

    internal fun notifyConnectivityRestored() {
        onRestored?.invoke()
    }
}
