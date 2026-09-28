package org.neteinstein.snap2sheet.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.neteinstein.snap2sheet.data.repository.AccountRepository
import org.neteinstein.snap2sheet.data.repository.FolderRepository
import org.neteinstein.snap2sheet.data.repository.InvoiceRepository
import org.neteinstein.snap2sheet.data.repository.SettingsRepository
import org.neteinstein.snap2sheet.data.repository.SpreadsheetRepository
import org.neteinstein.snap2sheet.domain.model.AppendRules
import org.neteinstein.snap2sheet.domain.model.GoogleAccount
import org.neteinstein.snap2sheet.ui.MessageCenter

data class SettingsUiState(
    val account: GoogleAccount? = null,
    val defaultSpreadsheetName: String? = null,
    val defaultFolderName: String? = null,
    val appendRules: AppendRules = AppendRules(),
    val notifyWhenSaveFinishes: Boolean = true,
    val isReconnecting: Boolean = false,
)

class SettingsViewModel(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val spreadsheetRepository: SpreadsheetRepository,
    private val folderRepository: FolderRepository,
    private val invoiceRepository: InvoiceRepository,
    private val messages: MessageCenter,
) : ViewModel() {

    private val isReconnecting = MutableStateFlow(false)

    private val base = combine(
        accountRepository.account,
        spreadsheetRepository.selected,
        folderRepository.selected,
        settingsRepository.appendRules,
        settingsRepository.notifyWhenSaveFinishes,
    ) { account, sheet, folder, rules, notify ->
        SettingsUiState(
            account = account,
            defaultSpreadsheetName = sheet?.name,
            defaultFolderName = folder?.name,
            appendRules = rules,
            notifyWhenSaveFinishes = notify,
        )
    }

    val state: StateFlow<SettingsUiState> = combine(base, isReconnecting) { state, reconnecting ->
        state.copy(isReconnecting = reconnecting)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    /** Signs out and forgets everything tied to the account: history, photos, Drive lists, defaults. */
    fun signOut() {
        viewModelScope.launch {
            accountRepository.signOut()
            invoiceRepository.clear()
            spreadsheetRepository.clear()
            folderRepository.clear()
        }
    }

    /** Re-runs Google sign-in without clearing anything — for an expired or revoked session. */
    fun reconnect() {
        if (isReconnecting.value) return
        isReconnecting.value = true
        viewModelScope.launch {
            try {
                val account = accountRepository.signInWithGoogle()
                messages.show("Reconnected as ${account.email}.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.show(e.message ?: "Couldn't reconnect to Google.")
            } finally {
                isReconnecting.value = false
            }
        }
    }

    fun setMatchColumnsByHeader(value: Boolean) {
        settingsRepository.setAppendRules(settingsRepository.appendRules.value.copy(matchColumnsByHeader = value))
    }

    fun setSkipDuplicates(value: Boolean) {
        settingsRepository.setAppendRules(settingsRepository.appendRules.value.copy(skipDuplicateInvoices = value))
    }

    fun setNewSheetTabEachMonth(value: Boolean) {
        settingsRepository.setAppendRules(settingsRepository.appendRules.value.copy(newSheetTabEachMonth = value))
    }

    fun setNotifyWhenSaveFinishes(value: Boolean) = settingsRepository.setNotifyWhenSaveFinishes(value)
}
