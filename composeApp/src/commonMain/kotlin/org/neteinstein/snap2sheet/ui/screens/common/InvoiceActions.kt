package org.neteinstein.snap2sheet.ui.screens.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.neteinstein.snap2sheet.data.repository.InvoiceRepository
import org.neteinstein.snap2sheet.data.sync.InvoiceSyncer

/**
 * The detail-sheet actions shared by Home and History: which invoice is open, retrying a failed
 * save (back into the background queue), removing an invoice from history.
 */
class InvoiceActions(
    private val invoiceRepository: InvoiceRepository,
    private val syncer: InvoiceSyncer,
) {
    private val _openInvoiceId = MutableStateFlow<String?>(null)
    val openInvoiceId: StateFlow<String?> = _openInvoiceId.asStateFlow()

    fun open(id: String) {
        _openInvoiceId.value = id
    }

    fun close() {
        _openInvoiceId.value = null
    }

    fun retry(id: String) = syncer.retry(id)

    fun retryAllFailed(ids: List<String>) = ids.forEach(syncer::retry)

    fun delete(id: String) {
        invoiceRepository.delete(id)
        close()
    }
}
