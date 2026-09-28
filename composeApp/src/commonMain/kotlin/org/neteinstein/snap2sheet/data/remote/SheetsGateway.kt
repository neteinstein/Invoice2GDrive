package org.neteinstein.snap2sheet.data.remote

import org.neteinstein.snap2sheet.domain.model.AppendRules
import org.neteinstein.snap2sheet.domain.model.DriveItem
import org.neteinstein.snap2sheet.domain.model.Invoice

sealed interface AppendOutcome {
    val tab: String

    data class Appended(override val tab: String) : AppendOutcome

    /** Skipped: [AppendRules.skipDuplicateInvoices] is on and the ATCUD is already in [tab]. */
    data class Duplicate(override val tab: String) : AppendOutcome
}

/** A file uploaded to Drive; [link] opens it in the browser and goes into the spreadsheet row. */
data class UploadedFile(val id: String, val link: String)

/** A failure talking to Google, with a message fit to show the user as-is. */
class SheetsException(message: String, val kind: Kind, cause: Throwable? = null) : Exception(message, cause) {
    enum class Kind { NOT_SIGNED_IN, PERMISSION, NOT_FOUND, NETWORK, OTHER }

    /** Worth retrying automatically: the same request may well work in a minute. */
    val isTransient: Boolean get() = kind == Kind.NETWORK || kind == Kind.OTHER
}

/** Everything that would otherwise need Google: spreadsheets, folders, rows and file uploads. */
interface SheetsGateway {
    suspend fun listSpreadsheets(): List<DriveItem>
    suspend fun createSpreadsheet(name: String): DriveItem

    suspend fun listFolders(): List<DriveItem>
    suspend fun createFolder(name: String): DriveItem

    /** The tab already holding [invoice]'s ATCUD, or null — checked before uploading anything. */
    suspend fun findDuplicate(spreadsheetId: String, invoice: Invoice, rules: AppendRules): String?

    suspend fun appendInvoice(spreadsheetId: String, invoice: Invoice, rules: AppendRules): AppendOutcome

    suspend fun uploadFile(folderId: String, name: String, mimeType: String, bytes: ByteArray): UploadedFile
}
