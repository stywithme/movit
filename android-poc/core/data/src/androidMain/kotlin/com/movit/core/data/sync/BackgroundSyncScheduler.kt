package com.movit.core.data.sync

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.movit.core.data.local.MovitAndroidRuntime
import java.util.concurrent.TimeUnit

actual object BackgroundSyncScheduler {
    private const val WORK_NAME = "movit_periodic_background_sync"
    private val SYNC_INTERVAL_HOURS = 6L

    actual fun schedule() {
        val context = runCatching { MovitAndroidRuntime.applicationContext }.getOrNull() ?: return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<MovitBackgroundSyncWorker>(
            SYNC_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    actual fun cancel() {
        val context = runCatching { MovitAndroidRuntime.applicationContext }.getOrNull() ?: return
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
