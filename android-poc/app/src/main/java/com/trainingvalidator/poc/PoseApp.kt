package com.trainingvalidator.poc

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * PoseApp — Application class
 *
 * Provides [applicationScope]: a CoroutineScope tied to the process lifetime,
 * not to any individual Activity. Use it for fire-and-forget background operations
 * (e.g. uploading session data) that must survive Activity destruction.
 */
class PoseApp : Application() {

    /**
     * Process-wide coroutine scope.
     * Never cancelled except when the process terminates.
     * Uses [SupervisorJob] so one failed child does not cancel siblings.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private lateinit var _instance: PoseApp

        /** Safe accessor — always valid after [onCreate] */
        val instance: PoseApp get() = _instance
    }

    override fun onCreate() {
        super.onCreate()
        _instance = this
    }
}
