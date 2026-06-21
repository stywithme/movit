package com.movit.feature.shell

/**
 * Bridges platform lifecycle/connectivity hooks to the active [MovitAppShellViewModel].
 */
object ShellSyncCoordinator {
    private var requestHandler: ((forceCheck: Boolean) -> Unit)? = null

    fun install(handler: (forceCheck: Boolean) -> Unit) {
        requestHandler = handler
    }

    fun requestSync(forceCheck: Boolean = false) {
        requestHandler?.invoke(forceCheck)
    }

    fun clear() {
        requestHandler = null
    }
}
