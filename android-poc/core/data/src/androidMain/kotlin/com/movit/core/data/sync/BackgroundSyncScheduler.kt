package com.movit.core.data.sync

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.movit.core.data.local.MovitAndroidRuntime
import java.util.concurrent.TimeUnit

actual object BackgroundSyncScheduler {
    private const val WORK_NAME = "movit_periodic_background_sync"
    private const val ONE_TIME_WORK_NAME = "movit_background_sync_now"
    private val SYNC_INTERVAL_HOURS = 6L

    actual fun schedule() {
        val context = runCatching { MovitAndroidRuntime.applicationContext }.getOrNull() ?: return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<MovitBackgroundSyncWorker>(
            SYNC_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        enqueueOneTimeSync(context, constraints)
    }

    actual fun cancel() {
        val context = runCatching { MovitAndroidRuntime.applicationContext }.getOrNull() ?: return
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME_WORK_NAME)
    }

    internal fun requestNow() {
        val context = runCatching { MovitAndroidRuntime.applicationContext }.getOrNull() ?: return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        enqueueOneTimeSync(context, constraints)
    }

    private fun enqueueOneTimeSync(
        context: android.content.Context,
        constraints: Constraints,
    ) {
        val request = OneTimeWorkRequestBuilder<MovitBackgroundSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
