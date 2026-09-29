package org.neteinstein.snap2sheet.ui.screens.history

import org.neteinstein.snap2sheet.platform.tr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.neteinstein.snap2sheet.data.repository.InvoiceRepository
import org.neteinstein.snap2sheet.data.sync.InvoiceSyncer
import org.neteinstein.snap2sheet.domain.format.Formatting
import org.neteinstein.snap2sheet.domain.model.Invoice
import org.neteinstein.snap2sheet.domain.model.InvoiceStatus
import org.neteinstein.snap2sheet.ui.screens.common.InvoiceActions
import org.neteinstein.snap2sheet.ui.screens.home.InvoiceListItem
import kotlin.time.Clock
import kotlin.time.Instant

enum class HistoryFilter(val label: String) {
    ALL(tr("All", "Todas")), SAVING(tr("Saving", "A guardar")), SYNCED(tr("Synced", "Sincronizada")), NEEDS_REVIEW(tr("Needs review", "Requer revisão")), FAILED(tr("Failed", "Falhou"))
}

sealed interface HistoryRow {
    data class Header(val label: String) : HistoryRow
    data class Item(val item: InvoiceListItem) : HistoryRow
}

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter.ALL,
    val rows: List<HistoryRow> = emptyList(),
    val isEmpty: Boolean = true,
    val openInvoice: Invoice? = null,
)

class HistoryViewModel(
    invoiceRepository: InvoiceRepository,
    syncer: InvoiceSyncer,
    private val clock: Clock,
) : ViewModel() {

    val actions = InvoiceActions(invoiceRepository, syncer)
    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val state: StateFlow<HistoryUiState> = combine(
        invoiceRepository.invoices,
        filter,
        actions.openInvoiceId,
    ) { invoices, f, openId ->
        val filtered = when (f) {
            HistoryFilter.ALL -> invoices
            HistoryFilter.SAVING -> invoices.filter { it.status == InvoiceStatus.QUEUED }
            HistoryFilter.SYNCED -> invoices.filter { it.status == InvoiceStatus.SYNCED }
            HistoryFilter.NEEDS_REVIEW -> invoices.filter { it.status == InvoiceStatus.NEEDS_REVIEW }
            HistoryFilter.FAILED -> invoices.filter { it.status == InvoiceStatus.FAILED }
        }
        HistoryUiState(
            filter = f,
            rows = groupIntoRows(filtered),
            isEmpty = filtered.isEmpty(),
            openInvoice = invoices.firstOrNull { it.id == openId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }

    private fun groupIntoRows(invoices: List<Invoice>): List<HistoryRow> {
        val now = clock.now().toEpochMilliseconds()
        val zone = TimeZone.currentSystemDefault()
        val rows = mutableListOf<HistoryRow>()
        var lastGroup: String? = null
        for (invoice in invoices) {
            val group = Formatting.scanGroup(invoice.scannedAtEpochMillis, now, zone)
            if (group != lastGroup) {
                rows += HistoryRow.Header(group)
                lastGroup = group
            }
            val scanned = Instant.fromEpochMilliseconds(invoice.scannedAtEpochMillis).toLocalDateTime(zone)
            val time = scanned.hour.toString().padStart(2, '0') + ":" + scanned.minute.toString().padStart(2, '0')
            val whenLabel = if (group == tr("Today", "Hoje") || group == tr("Yesterday", "Ontem")) time else Formatting.date(scanned.date)
            rows += HistoryRow.Item(InvoiceListItem(invoice, listOfNotNull(whenLabel, invoice.destinationSpreadsheetName).joinToString(" · ")))
        }
        return rows
    }
}
