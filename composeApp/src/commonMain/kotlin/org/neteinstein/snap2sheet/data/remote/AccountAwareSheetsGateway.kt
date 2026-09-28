package org.neteinstein.snap2sheet.data.remote

import org.neteinstein.snap2sheet.data.repository.AccountRepository
import org.neteinstein.snap2sheet.domain.model.AppendRules
import org.neteinstein.snap2sheet.domain.model.Invoice

/** Routes to [demo] while the signed-in account is a demo account, to [google] otherwise. */
class AccountAwareSheetsGateway(
    private val accounts: AccountRepository,
    private val google: SheetsGateway,
    private val demo: SheetsGateway,
) : SheetsGateway {

    private val current: SheetsGateway
        get() {
            val account = accounts.account.value
                ?: throw SheetsException("Sign in to Google first.", SheetsException.Kind.NOT_SIGNED_IN)
            return if (account.isDemo) demo else google
        }

    override suspend fun listSpreadsheets() = current.listSpreadsheets()
    override suspend fun createSpreadsheet(name: String) = current.createSpreadsheet(name)
    override suspend fun listFolders() = current.listFolders()
    override suspend fun createFolder(name: String) = current.createFolder(name)

    override suspend fun findDuplicate(spreadsheetId: String, invoice: Invoice, rules: AppendRules) =
        current.findDuplicate(spreadsheetId, invoice, rules)

    override suspend fun appendInvoice(spreadsheetId: String, invoice: Invoice, rules: AppendRules) =
        current.appendInvoice(spreadsheetId, invoice, rules)

    override suspend fun uploadFile(folderId: String, name: String, mimeType: String, bytes: ByteArray) =
        current.uploadFile(folderId, name, mimeType, bytes)
}
