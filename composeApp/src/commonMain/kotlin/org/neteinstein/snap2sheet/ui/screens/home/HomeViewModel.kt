package org.neteinstein.snap2sheet.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.neteinstein.snap2sheet.data.repository.AccountRepository
import org.neteinstein.snap2sheet.data.repository.InvoiceRepository
import org.neteinstein.snap2sheet.data.repository.SpreadsheetRepository
import org.neteinstein.snap2sheet.data.sync.InvoiceSyncer
import org.neteinstein.snap2sheet.domain.format.Formatting
import org.neteinstein.snap2sheet.domain.model.GoogleAccount
import org.neteinstein.snap2sheet.domain.model.Invoice
import org.neteinstein.snap2sheet.domain.model.InvoiceStatus
import org.neteinstein.snap2sheet.ui.screens.common.InvoiceActions
import kotlin.time.Clock
import kotlin.time.Instant

data class InvoiceListItem(val invoice: Invoice, val subtitle: String)

data class HomeUiState(
    val account: GoogleAccount? = null,
    val recentInvoices: List<InvoiceListItem> = emptyList(),
    val invoicesThisMonth: Int = 0,
    val totalThisMonth: Double = 0.0,
    val primarySpreadsheetName: String? = null,
    /** Failed saves, surfaced in a banner with a one-tap retry. */
    val failedInvoiceIds: List<String> = emptyList(),
    /** Invoices still being saved in the background. */
    val savingCount: Int = 0,
    val openInvoice: Invoice? = null,
)

class HomeViewModel(
    invoiceRepository: InvoiceRepository,
    accountRepository: AccountRepository,
    spreadsheetRepository: SpreadsheetRepository,
    syncer: InvoiceSyncer,
    private val clock: Clock,
) : ViewModel() {

    val actions = InvoiceActions(invoiceRepository, syncer)

    private val listState = combine(
        invoiceRepository.invoices,
        accountRepository.account,
        spreadsheetRepository.selected,
    ) { invoices, account, selected ->
        val now = clock.now()
        val zone = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(zone).date
        // A duplicate is an invoice already counted once; don't count it twice.
        val thisMonth = invoices.filter { it.status != InvoiceStatus.DUPLICATE }.filter {
            val scanned = Instant.fromEpochMilliseconds(it.scannedAtEpochMillis).toLocalDateTime(zone).date
            scanned.year == today.year && scanned.month == today.month
        }
        HomeUiState(
            account = account,
            recentInvoices = invoices.take(3).map {
                InvoiceListItem(it, Formatting.scannedAt(it.scannedAtEpochMillis, now.toEpochMilliseconds(), zone))
            },
            invoicesThisMonth = thisMonth.size,
            totalThisMonth = thisMonth.sumOf { it.total },
            primarySpreadsheetName = selected?.name,
            failedInvoiceIds = invoices.filter { it.status == InvoiceStatus.FAILED }.map { it.id },
            savingCount = invoices.count { it.status == InvoiceStatus.QUEUED },
        )
    }

    val state: StateFlow<HomeUiState> = combine(
        listState,
        invoiceRepository.invoices,
        actions.openInvoiceId,
    ) { base, invoices, openId ->
        base.copy(openInvoice = invoices.firstOrNull { it.id == openId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
}
