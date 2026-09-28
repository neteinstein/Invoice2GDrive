package org.neteinstein.snap2sheet.platform

private class WebBackgroundScheduler : BackgroundScheduler {
    /** A page can't run once its tab is closed; queued saves resume the next time it's opened. */
    override fun scheduleSync() = Unit
    override suspend fun <T> keepAlive(block: suspend () -> T): T = block()
}

actual fun platformBackgroundScheduler(): BackgroundScheduler = WebBackgroundScheduler()
