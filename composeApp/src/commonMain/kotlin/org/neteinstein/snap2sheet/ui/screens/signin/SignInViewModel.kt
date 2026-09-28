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

/** The OAuth client ID form, on platforms where the ID can be entered in the app. */
data class ClientIdUiState(
    /** "Web application" or "iOS" — the client type to create in Google Cloud Console. */
    val clientType: String,
    /** What to register on that client for this app (the site's origin, the bundle ID). */
    val registration: String,
    val clientId: String,
    val isEnteredInApp: Boolean,
)

data class SignInUiState(
    val isGoogleSignInAvailable: Boolean = true,
    val clientIdSetup: ClientIdUiState? = null,
    val isEditingClientId: Boolean = false,
    val clientIdError: String? = null,
    val isSigningIn: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
)

class SignInViewModel(
    private val accountRepository: AccountRepository,
    private val spreadsheetRepository: SpreadsheetRepository,
    private val folderRepository: FolderRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(currentConfig(SignInUiState()))
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

    fun editClientId() {
        _state.update { it.copy(isEditingClientId = true, clientIdError = null) }
    }

    fun dismissClientIdEditor() {
        _state.update { it.copy(isEditingClientId = false, clientIdError = null) }
    }

    /** Saves the pasted client ID; blank goes back to the build's own (if it has one). */
    fun saveClientId(clientId: String) {
        val setup = accountRepository.oauthClientSetup ?: return
        try {
            setup.update(clientId)
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(clientIdError = e.message) }
            return
        }
        _state.update { currentConfig(it).copy(isEditingClientId = false, clientIdError = null, error = null) }
    }

    fun resetClientId() {
        accountRepository.oauthClientSetup?.update(null)
        _state.update { currentConfig(it).copy(isEditingClientId = false, clientIdError = null, error = null) }
    }

    private fun currentConfig(state: SignInUiState): SignInUiState = state.copy(
        isGoogleSignInAvailable = accountRepository.isGoogleSignInAvailable,
        clientIdSetup = accountRepository.oauthClientSetup?.let {
            ClientIdUiState(
                clientType = it.clientType,
                registration = it.registration,
                clientId = it.clientId,
                isEnteredInApp = it.isEnteredInApp,
            )
        },
    )

    private suspend fun onSignedIn() {
        // Signing out clears history and Drive lists, so there's nothing from another account here.
        // Warm the lists up for the setup screen that follows.
        spreadsheetRepository.refresh()
        folderRepository.refresh()
        _state.update { it.copy(isSigningIn = false, signedIn = true) }
    }
}
