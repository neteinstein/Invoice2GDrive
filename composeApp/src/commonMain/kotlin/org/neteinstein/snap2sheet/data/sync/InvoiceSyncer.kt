package org.neteinstein.snap2sheet.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.neteinstein.snap2sheet.data.remote.AppendOutcome
import org.neteinstein.snap2sheet.data.remote.SheetsException
import org.neteinstein.snap2sheet.data.remote.SheetsGateway
import org.neteinstein.snap2sheet.data.repository.InvoiceRepository
import org.neteinstein.snap2sheet.data.repository.SettingsRepository
import org.neteinstein.snap2sheet.domain.format.Formatting
import org.neteinstein.snap2sheet.domain.model.Invoice
import org.neteinstein.snap2sheet.domain.model.InvoiceStatus
import org.neteinstein.snap2sheet.domain.sheets.InvoiceSheetLayout
import org.neteinstein.snap2sheet.platform.BackgroundScheduler
import org.neteinstein.snap2sheet.platform.Notifier
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** A one-line account of a finished save, for the in-app snackbar. */
fun interface SaveReporter {
    fun report(message: String)
}

/**
 * Saves queued invoices in the background, one at a time: checks the spreadsheet for a
 * duplicate ATCUD, uploads the photo to the Drive folder, then appends the row (with a link to
 * the photo). Each step is recorded on the invoice as it completes, so a retry — automatic or by
 * hand, in this process or after a restart — picks up where the last attempt stopped and never
 * uploads or appends twice.
 *
 * Transient failures (no network, Google hiccups) are retried automatically with backoff;
 * anything needing the user (session expired, sheet deleted) fails straight away. Either way a
 * notification is posted when an invoice finishes, if the user asked for them.
 */
class InvoiceSyncer(
    private val invoices: InvoiceRepository,
    private val gateway: SheetsGateway,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
    private val scheduler: BackgroundScheduler,
    private val reporter: SaveReporter,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private var loop: Job? = null

    /** Resumes whatever was queued when the app last stopped. Call once at startup. */
    fun start() {
        if (hasQueued()) kick()
    }

    /** New work was queued: process it now, and make sure the OS will finish it if we get closed. */
    fun kick() {
        scheduler.scheduleSync()
        wakeups.trySend(Unit)
        if (loop?.isActive != true) {
            loop = scope.launch { runLoop() }
        }
    }

    /** Puts a failed invoice back in the queue with a fresh set of automatic retries. */
    fun retry(invoiceId: String) {
        val invoice = invoices.invoices.value.firstOrNull { it.id == invoiceId } ?: return
        if (invoice.status != InvoiceStatus.FAILED) return
        invoices.update(invoice.copy(status = InvoiceStatus.QUEUED, attempts = 0, nextAttemptAtEpochMillis = 0, errorMessage = null))
        kick()
    }

    fun hasQueued(): Boolean = invoices.invoices.value.any { it.status == InvoiceStatus.QUEUED }

    /** Processes every queued invoice that's due now. Safe to call concurrently (e.g. from WorkManager). */
    suspend fun processDue() {
        mutex.withLock {
            scheduler.keepAlive {
                while (true) {
                    val now = clock.now().toEpochMilliseconds()
                    // Oldest first, re-read each time: the user may delete or retry meanwhile.
                    val next = invoices.invoices.value
                        .lastOrNull { it.status == InvoiceStatus.QUEUED && it.nextAttemptAtEpochMillis <= now }
                        ?: break
                    process(next)
                }
            }
        }
    }

    private suspend fun runLoop() {
        while (true) {
            processDue()
            val wait = timeUntilNextAttempt() ?: break
            // Sleep until the next backoff expires, or until something new is queued.
            withTimeoutOrNull(wait) { wakeups.receive() }
        }
    }

    private fun timeUntilNextAttempt(): Duration? {
        val now = clock.now().toEpochMilliseconds()
        return invoices.invoices.value
            .filter { it.status == InvoiceStatus.QUEUED }
            .minOfOrNull { it.nextAttemptAtEpochMillis }
            ?.let { (it - now).coerceAtLeast(0).milliseconds }
    }

    private suspend fun process(queued: Invoice) {
        var invoice = queued
        try {
            val spreadsheetId = invoice.destinationSpreadsheetId
                ?: throw SheetsException("No spreadsheet was chosen for this invoice.", SheetsException.Kind.NOT_FOUND)
            val rules = settings.appendRules.value

            // 1. Duplicate check first, so a duplicate doesn't leave an orphan photo in Drive.
            if (!invoice.rowAppended && invoice.driveFileId == null) {
                val duplicateTab = gateway.findDuplicate(spreadsheetId, invoice, rules)
                if (duplicateTab != null) {
                    finish(invoice.copy(status = InvoiceStatus.DUPLICATE), duplicateTab)
                    return
                }
            }

            // 2. Photo → Drive folder.
            val photo = invoice.photo
            if (photo != null && invoice.driveFileId == null) {
                val folderId = invoice.destinationFolderId
                    ?: throw SheetsException("No Drive folder was chosen for this invoice.", SheetsException.Kind.NOT_FOUND)
                val bytes = invoices.loadPhoto(photo)
                if (bytes != null) {
                    val uploaded = gateway.uploadFile(folderId, InvoiceSheetLayout.photoFileName(invoice, photo.extension), photo.mimeType, bytes)
                    invoice = invoice.copy(driveFileId = uploaded.id, driveFileLink = uploaded.link)
                    invoices.update(invoice)
                } else {
                    invoice = invoice.copy(warnings = invoice.warnings + "The photo was lost before it could be uploaded.")
                }
            }

            // 3. Row → spreadsheet.
            var tab: String? = null
            if (!invoice.rowAppended) {
                when (val outcome = gateway.appendInvoice(spreadsheetId, invoice, rules)) {
                    is AppendOutcome.Appended -> invoice = invoice.copy(rowAppended = true).also { tab = outcome.tab }
                    is AppendOutcome.Duplicate -> {
                        finish(invoice.copy(status = InvoiceStatus.DUPLICATE), outcome.tab)
                        return
                    }
                }
                invoices.update(invoice)
            }

            val status = if (invoice.warnings.isEmpty()) InvoiceStatus.SYNCED else InvoiceStatus.NEEDS_REVIEW
            finish(invoice.copy(status = status), tab)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e as? SheetsException
                ?: SheetsException(e.message ?: "Something went wrong while saving.", SheetsException.Kind.OTHER, e)
            val attempts = invoice.attempts + 1
            if (error.isTransient && attempts < MAX_ATTEMPTS) {
                val backoff = BACKOFF.getOrElse(attempts - 1) { BACKOFF.last() }
                invoices.update(
                    invoice.copy(
                        attempts = attempts,
                        nextAttemptAtEpochMillis = (clock.now() + backoff).toEpochMilliseconds(),
                        errorMessage = "${error.message} Retrying automatically…",
                    )
                )
            } else {
                invoices.update(invoice.copy(status = InvoiceStatus.FAILED, attempts = attempts, errorMessage = error.message))
                announce(
                    title = "Couldn't save ${label(invoice)}",
                    message = "${error.message} Open Fatura to retry.",
                )
            }
        }
    }

    private fun finish(invoice: Invoice, tab: String?) {
        invoice.photo?.let(invoices::deletePhoto)
        val done = invoice.copy(photo = null, attempts = 0, nextAttemptAtEpochMillis = 0, errorMessage = null)
        invoices.update(done)
        when (done.status) {
            InvoiceStatus.DUPLICATE -> announce(
                title = "Already saved: ${label(done)}",
                message = "It's already in ${done.destinationSpreadsheetName}${tab?.let { " ($it)" }.orEmpty()}, so it was skipped.",
            )
            else -> announce(
                title = "Saved ${label(done)}",
                message = buildString {
                    append("Added to ${done.destinationSpreadsheetName}")
                    if (done.driveFileId != null) append(" · photo in ${done.destinationFolderName}")
                    if (done.status == InvoiceStatus.NEEDS_REVIEW) append(". Worth a second look.")
                },
            )
        }
    }

    private fun announce(title: String, message: String) {
        reporter.report("$title. $message")
        if (settings.notifyWhenSaveFinishes.value) notifier.notify(title, message)
    }

    private fun label(invoice: Invoice): String =
        listOf(invoice.merchantName.ifBlank { "invoice" }, Formatting.euros(invoice.total)).joinToString(" · ")

    companion object {
        const val MAX_ATTEMPTS = 6
        private val BACKOFF = listOf(5.seconds, 20.seconds, 1.minutes, 5.minutes, 15.minutes)
    }
}
