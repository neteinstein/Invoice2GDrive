package org.neteinstein.snap2sheet.ui.screens.common

import org.neteinstein.snap2sheet.data.repository.AccountRepository
import org.neteinstein.snap2sheet.data.repository.FolderRepository
import org.neteinstein.snap2sheet.data.repository.InvoiceRepository
import org.neteinstein.snap2sheet.data.repository.SpreadsheetRepository

/** Signs out and forgets everything tied to the account: history, photos, Drive lists, defaults. */
class SignOutAction(
    private val accountRepository: AccountRepository,
    private val invoiceRepository: InvoiceRepository,
    private val spreadsheetRepository: SpreadsheetRepository,
    private val folderRepository: FolderRepository,
) {
    suspend operator fun invoke() {
        accountRepository.signOut()
        invoiceRepository.clear()
        spreadsheetRepository.clear()
        folderRepository.clear()
    }
}
