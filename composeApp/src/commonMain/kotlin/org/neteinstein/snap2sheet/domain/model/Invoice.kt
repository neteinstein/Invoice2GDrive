package org.neteinstein.snap2sheet.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * A single invoice scanned from a Portuguese fatura QR code (the AT Portugal scheme — see
 * [org.neteinstein.snap2sheet.domain.qr.AtQrCodeParser]), the photo of the whole document, and
 * its progress on the way to a Google Sheets row plus a file in a Google Drive folder.
 *
 * The QR code doesn't carry the merchant's name, only its NIF, so [merchantName] is either typed
 * in on the Review screen or remembered from a previous invoice with the same [nifEmitente].
 */
@Serializable
data class Invoice(
    val id: String,
    val merchantName: String,
    /** AT document type code, e.g. `FT`, `FS`, `FR` — see [DocumentType]. */
    val documentType: String,
    val documentNumber: String,
    val issueDate: LocalDate?,
    val atcud: String,
    val nifEmitente: String,
    val nifAdquirente: String,
    val taxBase: Double,
    val vat: Double,
    val total: Double,
    val status: InvoiceStatus,
    val scannedAtEpochMillis: Long,
    val destinationSpreadsheetId: String? = null,
    val destinationSpreadsheetName: String? = null,
    val destinationFolderId: String? = null,
    val destinationFolderName: String? = null,
    /** The QR payload exactly as scanned, kept so a row can be audited against its source. */
    val rawQr: String? = null,
    /** Validation issues the user saved anyway (bad NIF check digit, totals that don't add up…). */
    val warnings: List<String> = emptyList(),
    /** The photo of the whole invoice, kept on the device (see `PhotoStore`) until it's uploaded. */
    val photo: InvoicePhoto? = null,
    /** Set once the photo is in Drive, so a retry doesn't upload it twice. */
    val driveFileId: String? = null,
    val driveFileLink: String? = null,
    /** Set once the row is in the spreadsheet, so a retry doesn't append it twice. */
    val rowAppended: Boolean = false,
    /** Automatic retries spent on the current save; reset by a manual retry. */
    val attempts: Int = 0,
    /** When a [InvoiceStatus.QUEUED] invoice may be tried again (backoff after a transient failure). */
    val nextAttemptAtEpochMillis: Long = 0,
    /** Why the last save attempt failed, for [InvoiceStatus.FAILED] (and transient [InvoiceStatus.QUEUED] retries). */
    val errorMessage: String? = null,
)

/** A photo (or, on the web, possibly a PDF) of the whole invoice, stored locally under [key]. */
@Serializable
data class InvoicePhoto(val key: String, val mimeType: String) {
    val extension: String get() = if (mimeType == "application/pdf") "pdf" else "jpg"
}
