package com.movit.core.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class MovitBackgroundSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return when (runBackgroundSyncIfReady()) {
            BackgroundSyncRunOutcome.Failed -> Result.retry()
            BackgroundSyncRunOutcome.NoOp,
            BackgroundSyncRunOutcome.Completed,
            -> Result.success()
        }
    }
}
