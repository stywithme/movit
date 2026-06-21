package com.movit.core.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.movit.core.data.MovitData
import com.movit.core.data.local.MovitAndroidRuntime
import com.movit.core.data.platform.AndroidMovitPlatform

class MovitBackgroundSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ensureDataLayerInstalled(applicationContext)
        return when (runBackgroundSyncIfReady()) {
            BackgroundSyncRunOutcome.Failed -> Result.retry()
            BackgroundSyncRunOutcome.NoOp,
            BackgroundSyncRunOutcome.Completed,
            -> Result.success()
        }
    }

    private fun ensureDataLayerInstalled(context: Context) {
        val appContext = context.applicationContext
        MovitAndroidRuntime.applicationContext = appContext
        if (!MovitData.isInstalled) {
            MovitData.install(platform = AndroidMovitPlatform(appContext))
        }
    }
}
