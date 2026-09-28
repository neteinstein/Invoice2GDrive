package org.neteinstein.snap2sheet.platform

import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

private class IosBackgroundScheduler : BackgroundScheduler {
    /** iOS has no "run when there's network" job for this; the queue resumes on the next launch. */
    override fun scheduleSync() = Unit

    override suspend fun <T> keepAlive(block: suspend () -> T): T {
        val app = UIApplication.sharedApplication
        var taskId = UIBackgroundTaskInvalid
        taskId = app.beginBackgroundTaskWithName("Saving invoices") {
            // Out of background time: let iOS suspend us; the invoice stays queued.
            app.endBackgroundTask(taskId)
            taskId = UIBackgroundTaskInvalid
        }
        try {
            return block()
        } finally {
            if (taskId != UIBackgroundTaskInvalid) app.endBackgroundTask(taskId)
        }
    }
}

actual fun platformBackgroundScheduler(): BackgroundScheduler = IosBackgroundScheduler()
