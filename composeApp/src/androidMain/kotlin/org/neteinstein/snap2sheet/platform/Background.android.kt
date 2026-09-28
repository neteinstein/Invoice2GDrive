package org.neteinstein.snap2sheet.platform

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.neteinstein.snap2sheet.data.local.AndroidAppContext
import org.neteinstein.snap2sheet.data.sync.InvoiceSyncer
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "fatura-invoice-sync"

/**
 * Processes the save queue from WorkManager, so invoices queued just before the app was closed
 * — or while offline — are still saved once there's a network. Retried with backoff while
 * anything is left in the queue.
 */
class InvoiceSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val syncer: InvoiceSyncer by inject()

    override suspend fun doWork(): Result {
        syncer.processDue()
        return if (syncer.hasQueued()) Result.retry() else Result.success()
    }
}

private class AndroidBackgroundScheduler(private val context: Context) : BackgroundScheduler {
    override fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<InvoiceSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override suspend fun <T> keepAlive(block: suspend () -> T): T = block()
}

actual fun platformBackgroundScheduler(): BackgroundScheduler = AndroidBackgroundScheduler(AndroidAppContext.instance)
