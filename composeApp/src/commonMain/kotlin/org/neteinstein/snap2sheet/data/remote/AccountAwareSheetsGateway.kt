package org.neteinstein.snap2sheet.data.remote

import org.neteinstein.snap2sheet.data.repository.AccountRepository
import org.neteinstein.snap2sheet.domain.model.AppendRules
import org.neteinstein.snap2sheet.domain.model.Invoice

/** Delegates to [google], failing with NOT_SIGNED_IN while no account is signed in. */
class AccountAwareSheetsGateway(
    private val accounts: AccountRepository,
    private val google: SheetsGateway,
) : SheetsGateway {

    private val current: SheetsGateway
        get() {
            if (accounts.account.value == null) {
                throw SheetsException("Sign in to Google first.", SheetsException.Kind.NOT_SIGNED_IN)
            }
            return google
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
