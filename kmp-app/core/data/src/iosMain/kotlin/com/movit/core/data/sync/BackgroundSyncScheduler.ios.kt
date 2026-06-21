package com.movit.core.data.sync

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateByAddingTimeInterval

/** Must match `BGTaskSchedulerPermittedIdentifiers` in the iOS host Info.plist. */
const val MOVIT_IOS_BACKGROUND_SYNC_TASK_ID = "com.movit.background-sync"

private val SYNC_INTERVAL_SECONDS = 6.0 * 60.0 * 60.0

/**
 * Call once before the app finishes launching (AppDelegate / `@main` App init).
 * Registers the BGAppRefresh handler and enqueues the first refresh request.
 */
fun registerIosBackgroundSyncAtLaunch() {
    BackgroundSyncScheduler.registerTaskHandlerIfNeeded()
    BackgroundSyncScheduler.schedule()
}

actual object BackgroundSyncScheduler {
    private var taskHandlerRegistered = false

    internal fun registerTaskHandlerIfNeeded() {
        if (taskHandlerRegistered) return
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = MOVIT_IOS_BACKGROUND_SYNC_TASK_ID,
            usingQueue = null,
        ) { task ->
            val bgTask = task ?: return@registerForTaskWithIdentifier
            handleBackgroundRefreshTask(bgTask)
        }
        taskHandlerRegistered = true
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun schedule() {
        registerTaskHandlerIfNeeded()
        val request = BGAppRefreshTaskRequest(MOVIT_IOS_BACKGROUND_SYNC_TASK_ID).apply {
            earliestBeginDate = NSDate().dateByAddingTimeInterval(SYNC_INTERVAL_SECONDS)
        }
        runCatching {
            BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
        }
    }

    actual fun cancel() {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(
            MOVIT_IOS_BACKGROUND_SYNC_TASK_ID,
        )
    }

    private fun handleBackgroundRefreshTask(task: BGTask) {
        val refreshTask = task as? BGAppRefreshTask ?: run {
            task.setTaskCompletedWithSuccess(false)
            return
        }
        var didExpire = false
        refreshTask.expirationHandler = {
            didExpire = true
        }
        val succeeded = if (didExpire) {
            false
        } else {
            runBlocking {
                runBackgroundSyncIfReady() != BackgroundSyncRunOutcome.Failed
            }
        }
        refreshTask.setTaskCompletedWithSuccess(succeeded)
        if (!didExpire) {
            schedule()
        }
    }
}
