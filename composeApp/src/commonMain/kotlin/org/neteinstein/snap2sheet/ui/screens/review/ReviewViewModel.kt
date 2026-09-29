package org.neteinstein.snap2sheet.ui.screens.review

import org.neteinstein.snap2sheet.platform.tr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.neteinstein.snap2sheet.data.repository.FolderRepository
import org.neteinstein.snap2sheet.data.repository.InvoiceRepository
import org.neteinstein.snap2sheet.data.repository.SettingsRepository
import org.neteinstein.snap2sheet.data.repository.SpreadsheetRepository
import org.neteinstein.snap2sheet.data.sync.InvoiceSyncer
import org.neteinstein.snap2sheet.domain.format.Formatting
import org.neteinstein.snap2sheet.domain.model.Invoice
import org.neteinstein.snap2sheet.domain.validation.InvoiceValidator
import org.neteinstein.snap2sheet.domain.validation.Nif
import org.neteinstein.snap2sheet.ui.MessageCenter

enum class ReviewField(val label: String, val kind: Kind = Kind.TEXT) {
    MERCHANT("Fornecedor"),
    NIF_EMITENTE("NIF Emitente", Kind.NUMBER),
    NIF_ADQUIRENTE("NIF Adquirente", Kind.NUMBER),
    DOCUMENT_TYPE("Tipo de Documento"),
    DOCUMENT_NUMBER("N.º Documento"),
    DATE("Data", Kind.DATE),
    ATCUD("ATCUD"),
    TAX_BASE("Base Tributável (€)", Kind.AMOUNT),
    VAT("IVA (€)", Kind.AMOUNT),
    TOTAL("Total (€)", Kind.AMOUNT),
    ;

    enum class Kind { TEXT, NUMBER, DATE, AMOUNT }
}

data class ReviewUiState(
    val invoice: Invoice? = null,
    val fromQr: Boolean = false,
    val values: Map<ReviewField, String> = emptyMap(),
    val fieldErrors: Map<ReviewField, String> = emptyMap(),
    val warnings: List<String> = emptyList(),
    val selectedSpreadsheetName: String? = null,
    val selectedFolderName: String? = null,
    /** "Always save to my defaults" is on and both defaults exist: Save skips the targets screen. */
    val savesDirectly: Boolean = false,
    val isSaving: Boolean = false,
) {
    val canSave: Boolean get() = invoice != null && fieldErrors.isEmpty() && !isSaving
}

/** One-shot navigation outcomes of pressing Save. */
enum class ReviewEvent { CHOOSE_DESTINATION, SAVED }

class ReviewViewModel(
    private val invoiceRepository: InvoiceRepository,
    private val spreadsheetRepository: SpreadsheetRepository,
    private val folderRepository: FolderRepository,
    private val settingsRepository: SettingsRepository,
    private val syncer: InvoiceSyncer,
    private val messages: MessageCenter,
) : ViewModel() {

    private data class Form(val values: Map<ReviewField, String>, val errors: Map<ReviewField, String>, val isSaving: Boolean = false)

    private val form = MutableStateFlow(Form(invoiceRepository.draftInvoice.value?.let(::valuesFor).orEmpty(), emptyMap()))

    private val _events = MutableStateFlow<ReviewEvent?>(null)
    val events: StateFlow<ReviewEvent?> = _events

    val state: StateFlow<ReviewUiState> = combine(
        form,
        invoiceRepository.draftInvoice,
        spreadsheetRepository.selected,
        folderRepository.selected,
        settingsRepository.alwaysSaveToDefaultSpreadsheet,
    ) { form, draft, selected, folder, alwaysSave ->
        ReviewUiState(
            invoice = draft,
            fromQr = draft?.rawQr != null,
            values = form.values,
            fieldErrors = form.errors,
            warnings = draft?.let { InvoiceValidator.allWarnings(it, it.warnings) }.orEmpty(),
            selectedSpreadsheetName = selected?.name,
            selectedFolderName = folder?.name,
            savesDirectly = alwaysSave && selected != null && folder != null,
            isSaving = form.isSaving,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReviewUiState(invoice = invoiceRepository.draftInvoice.value))

    fun onValueChange(field: ReviewField, text: String) {
        val draft = invoiceRepository.draftInvoice.value ?: return
        val (updated, error) = apply(draft, field, text)
        form.update { it.copy(values = it.values + (field to text), errors = if (error == null) it.errors - field else it.errors + (field to error)) }
        if (updated != null) invoiceRepository.updateDraft(updated)
    }

    fun onSave() {
        if (!state.value.canSave) return
        val spreadsheet = spreadsheetRepository.selected.value
        val folder = folderRepository.selected.value
        if (!settingsRepository.alwaysSaveToDefaultSpreadsheet.value || spreadsheet == null || folder == null) {
            _events.value = ReviewEvent.CHOOSE_DESTINATION
            return
        }
        // isSaving stays set: the draft is gone after submitting, and the screen must leave via
        // SAVED rather than read the empty draft as "nothing to review".
        form.update { it.copy(isSaving = true) }
        invoiceRepository.submitDraft(spreadsheet, folder)
        syncer.kick()
        messages.show(tr("Saving to ${spreadsheet.name} in the background — you'll be notified when it's done.", "A guardar em ${spreadsheet.name} em segundo plano — será notificado quando terminar."))
        _events.value = ReviewEvent.SAVED
    }

    fun onEventHandled() {
        _events.value = null
    }

    /** The draft with [field] set from [text], or an error when [text] doesn't parse. */
    private fun apply(draft: Invoice, field: ReviewField, text: String): Pair<Invoice?, String?> = when (field) {
        ReviewField.MERCHANT -> draft.copy(merchantName = text) to null
        ReviewField.NIF_EMITENTE -> draft.copy(nifEmitente = Nif.normalize(text)) to null
        ReviewField.NIF_ADQUIRENTE -> draft.copy(nifAdquirente = Nif.normalize(text)) to null
        ReviewField.DOCUMENT_TYPE -> draft.copy(documentType = text.trim().uppercase()) to null
        ReviewField.DOCUMENT_NUMBER -> draft.copy(documentNumber = text.trim()) to null
        ReviewField.ATCUD -> draft.copy(atcud = text.trim()) to null
        ReviewField.DATE -> Formatting.parseDate(text)?.let { draft.copy(issueDate = it) to null } ?: (null to tr("Use DD/MM/YYYY", "Use DD/MM/AAAA"))
        ReviewField.TAX_BASE -> amount(text) { draft.copy(taxBase = it) }
        ReviewField.VAT -> amount(text) { draft.copy(vat = it) }
        ReviewField.TOTAL -> amount(text) { draft.copy(total = it) }
    }

    private inline fun amount(text: String, set: (Double) -> Invoice): Pair<Invoice?, String?> =
        Formatting.parseAmount(text)?.let { set(it) to null } ?: (null to tr("Not an amount", "Não é um valor"))

    private fun valuesFor(invoice: Invoice): Map<ReviewField, String> = mapOf(
        ReviewField.MERCHANT to invoice.merchantName,
        ReviewField.NIF_EMITENTE to Nif.format(invoice.nifEmitente),
        ReviewField.NIF_ADQUIRENTE to Nif.format(invoice.nifAdquirente),
        ReviewField.DOCUMENT_TYPE to invoice.documentType,
        ReviewField.DOCUMENT_NUMBER to invoice.documentNumber,
        ReviewField.DATE to invoice.issueDate?.let(Formatting::date).orEmpty(),
        ReviewField.ATCUD to invoice.atcud,
        ReviewField.TAX_BASE to Formatting.amount(invoice.taxBase),
        ReviewField.VAT to Formatting.amount(invoice.vat),
        ReviewField.TOTAL to Formatting.amount(invoice.total),
    )
}
