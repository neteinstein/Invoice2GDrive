package org.neteinstein.snap2sheet.platform

/**
 * Keeps queued saves going beyond the foreground app. Android hands the queue to WorkManager,
 * which runs it when there's a network even after the app is closed; iOS asks for the extra
 * background time `beginBackgroundTask` grants, and anything left resumes on the next launch;
 * the web can only work while the tab is open.
 */
interface BackgroundScheduler {
    /** Makes sure the queue gets processed eventually, even if the app is closed now. */
    fun scheduleSync()

    /** Runs [block], asking the OS not to suspend the app until it finishes. */
    suspend fun <T> keepAlive(block: suspend () -> T): T
}

expect fun platformBackgroundScheduler(): BackgroundScheduler
