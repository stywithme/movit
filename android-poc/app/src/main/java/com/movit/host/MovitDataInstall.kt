package com.movit.host

import android.content.Context
import com.movit.core.data.MovitData
import com.movit.core.data.local.MovitAndroidRuntime
import com.movit.core.data.outbox.registerOutboxConnectivityReplay
import com.movit.core.data.platform.AndroidMovitPlatform
import com.movit.core.posecapture.di.movitPoseCaptureAndroidModule

object MovitDataInstall {

    fun install(context: Context) {
        val appContext = context.applicationContext
        MovitAndroidRuntime.applicationContext = appContext
        MovitData.install(
            additionalModules = listOf(movitPoseCaptureAndroidModule()),
            platform = AndroidMovitPlatform(appContext),
        )
        registerOutboxConnectivityReplay(appContext)
        LegacyWorkoutSyncDrain.drainPendingExecutions(appContext)
    }
}
