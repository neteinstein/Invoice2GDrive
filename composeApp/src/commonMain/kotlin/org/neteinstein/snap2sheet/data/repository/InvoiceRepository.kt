package org.neteinstein.snap2sheet.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.neteinstein.snap2sheet.data.local.KeyValueStore
import org.neteinstein.snap2sheet.data.local.PhotoStore
import org.neteinstein.snap2sheet.domain.model.DriveItem
import org.neteinstein.snap2sheet.domain.model.Invoice
import org.neteinstein.snap2sheet.domain.model.InvoicePhoto
import org.neteinstein.snap2sheet.domain.model.InvoiceStatus
import org.neteinstein.snap2sheet.domain.qr.AtQrCodeParser
import org.neteinstein.snap2sheet.domain.qr.QrParseResult
import org.neteinstein.snap2sheet.domain.validation.InvoiceValidator
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val KEY_INVOICES = "invoices"

/**
 * Scanned invoices (most recent first, persisted locally) and the draft currently under review.
 * Submitting the draft queues it; [org.neteinstein.snap2sheet.data.sync.InvoiceSyncer] does the
 * uploading and appending in the background and reports progress back through [update].
 */
interface InvoiceRepository {
    val invoices: StateFlow<List<Invoice>>

    /** The invoice being put together: QR data (or typed in), then its photo, then reviewed. */
    val draftInvoice: StateFlow<Invoice?>

    /** Decodes a scanned QR payload into a new [draftInvoice]; leaves the draft alone on failure. */
    fun startDraftFromQr(payload: String): QrParseResult

    /** Starts an empty draft for typing an invoice in by hand (no QR code, or it won't scan). */
    fun startManualDraft()

    fun updateDraft(invoice: Invoice)

    /** Stores the photo of the whole invoice on the device and attaches it to the draft. */
    suspend fun attachPhoto(bytes: ByteArray, mimeType: String)
    fun removeDraftPhoto()
    suspend fun loadPhoto(photo: InvoicePhoto): ByteArray?

    fun discardDraft()

    /** Queues the draft for saving to [spreadsheet] and [folder]; returns the queued invoice. */
    fun submitDraft(spreadsheet: DriveItem, folder: DriveItem): Invoice?

    /** Replaces the stored invoice with the same id (no-op if it was deleted meanwhile). */
    fun update(invoice: Invoice)

    /** Frees an uploaded photo's local copy. */
    fun deletePhoto(photo: InvoicePhoto)

    fun delete(invoiceId: String)

    /** Forgets all invoices, photos and the draft — on sign-out. */
    fun clear()
}

class DefaultInvoiceRepository(
    private val store: KeyValueStore,
    private val photos: PhotoStore,
    private val json: Json,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : InvoiceRepository {

    private val _invoices = MutableStateFlow(
        store.getString(KEY_INVOICES)?.let { runCatching { json.decodeFromString<List<Invoice>>(it) }.getOrNull() }.orEmpty()
    )
    override val invoices: StateFlow<List<Invoice>> = _invoices.asStateFlow()

    private val _draftInvoice = MutableStateFlow<Invoice?>(null)
    override val draftInvoice: StateFlow<Invoice?> = _draftInvoice.asStateFlow()

    override fun startDraftFromQr(payload: String): QrParseResult {
        val result = AtQrCodeParser.parse(payload)
        if (result is QrParseResult.Success) {
            val data = result.data
            replaceDraft(
                Invoice(
                    id = Uuid.random().toString(),
                    merchantName = knownMerchantName(data.nifEmitente).orEmpty(),
                    documentType = data.documentType,
                    documentNumber = data.documentNumber,
                    issueDate = data.issueDate,
                    atcud = data.atcud,
                    nifEmitente = data.nifEmitente,
                    nifAdquirente = data.nifAdquirente,
                    taxBase = data.taxBase,
                    vat = data.vat,
                    total = data.total,
                    status = InvoiceStatus.QUEUED,
                    scannedAtEpochMillis = clock.now().toEpochMilliseconds(),
                    rawQr = payload.trim(),
                    warnings = result.warnings,
                )
            )
        }
        return result
    }

    override fun startManualDraft() {
        replaceDraft(
            Invoice(
                id = Uuid.random().toString(),
                merchantName = "",
                documentType = "FT",
                documentNumber = "",
                issueDate = clock.now().toLocalDateTime(timeZone).date,
                atcud = "",
                nifEmitente = "",
                nifAdquirente = "",
                taxBase = 0.0,
                vat = 0.0,
                total = 0.0,
                status = InvoiceStatus.QUEUED,
                scannedAtEpochMillis = clock.now().toEpochMilliseconds(),
            )
        )
    }

    override fun updateDraft(invoice: Invoice) {
        _draftInvoice.value = invoice
    }

    override suspend fun attachPhoto(bytes: ByteArray, mimeType: String) {
        val key = photos.save(bytes)
        val draft = _draftInvoice.value
        if (draft == null) {
            photos.delete(key)
            return
        }
        draft.photo?.let(::deletePhoto)
        _draftInvoice.value = draft.copy(photo = InvoicePhoto(key, mimeType))
    }

    override fun removeDraftPhoto() {
        _draftInvoice.update { draft -> draft?.photo?.let(::deletePhoto); draft?.copy(photo = null) }
    }

    override suspend fun loadPhoto(photo: InvoicePhoto): ByteArray? = photos.load(photo.key)

    override fun discardDraft() = replaceDraft(null)

    override fun submitDraft(spreadsheet: DriveItem, folder: DriveItem): Invoice? {
        val draft = _draftInvoice.value ?: return null
        val queued = draft.copy(
            merchantName = draft.merchantName.trim(),
            warnings = InvoiceValidator.allWarnings(draft, draft.warnings),
            status = InvoiceStatus.QUEUED,
            destinationSpreadsheetId = spreadsheet.id,
            destinationSpreadsheetName = spreadsheet.name,
            destinationFolderId = folder.id,
            destinationFolderName = folder.name,
            attempts = 0,
            nextAttemptAtEpochMillis = 0,
            errorMessage = null,
        )
        _draftInvoice.value = null // The photo now belongs to the queued invoice; don't delete it.
        var list = _invoices.value
        if (queued.atcud.isNotBlank()) {
            // A re-scan of a document that failed before replaces the stale failure.
            list = list.filterNot { old ->
                val stale = old.status == InvoiceStatus.FAILED && old.atcud == queued.atcud && old.nifEmitente == queued.nifEmitente
                if (stale) old.photo?.let(::deletePhoto)
                stale
            }
        }
        setInvoices(listOf(queued) + list)
        return queued
    }

    override fun update(invoice: Invoice) {
        val list = _invoices.value
        val index = list.indexOfFirst { it.id == invoice.id }
        if (index >= 0) setInvoices(list.toMutableList().also { it[index] = invoice })
    }

    override fun deletePhoto(photo: InvoicePhoto) {
        scope.launch { photos.delete(photo.key) }
    }

    override fun delete(invoiceId: String) {
        _invoices.value.firstOrNull { it.id == invoiceId }?.photo?.let(::deletePhoto)
        setInvoices(_invoices.value.filterNot { it.id == invoiceId })
    }

    override fun clear() {
        replaceDraft(null)
        _invoices.value.mapNotNull { it.photo }.forEach(::deletePhoto)
        setInvoices(emptyList())
    }

    private fun replaceDraft(invoice: Invoice?) {
        _draftInvoice.value?.photo?.let(::deletePhoto)
        _draftInvoice.value = invoice
    }

    private fun knownMerchantName(nif: String): String? =
        _invoices.value.firstOrNull { it.nifEmitente == nif && it.merchantName.isNotBlank() }?.merchantName

    private fun setInvoices(list: List<Invoice>) {
        _invoices.value = list
        store.putString(KEY_INVOICES, json.encodeToString(list))
    }
}
