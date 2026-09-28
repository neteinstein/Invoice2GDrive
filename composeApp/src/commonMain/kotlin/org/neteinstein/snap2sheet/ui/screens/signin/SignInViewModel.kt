package org.neteinstein.snap2sheet.ui.screens.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.neteinstein.snap2sheet.data.repository.AccountRepository
import org.neteinstein.snap2sheet.data.repository.FolderRepository
import org.neteinstein.snap2sheet.data.repository.SpreadsheetRepository

data class SignInUiState(
    val isGoogleSignInAvailable: Boolean = true,
    val isSigningIn: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
)

class SignInViewModel(
    private val accountRepository: AccountRepository,
    private val spreadsheetRepository: SpreadsheetRepository,
    private val folderRepository: FolderRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState(isGoogleSignInAvailable = accountRepository.isGoogleSignInAvailable))
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    fun signInWithGoogle() {
        if (_state.value.isSigningIn) return
        _state.update { it.copy(isSigningIn = true, error = null) }
        viewModelScope.launch {
            try {
                accountRepository.signInWithGoogle()
                onSignedIn()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSigningIn = false, error = e.message ?: "Google sign-in failed.") }
            }
        }
    }

    fun startDemo() {
        accountRepository.startDemo()
        viewModelScope.launch { onSignedIn() }
    }

    private suspend fun onSignedIn() {
        // Signing out clears history and Drive lists, so there's nothing from another account here.
        // Warm the lists up for the setup screen that follows.
        spreadsheetRepository.refresh()
        folderRepository.refresh()
        _state.update { it.copy(isSigningIn = false, signedIn = true) }
    }
}
