package org.neteinstein.snap2sheet.ui.screens.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.neteinstein.snap2sheet.data.repository.InvoiceRepository
import org.neteinstein.snap2sheet.domain.qr.QrParseResult

data class ScanUiState(
    /** Why the last code was rejected; cleared a few seconds later. */
    val error: String? = null,
    /** A draft is ready for review — the screen navigates and calls [ScanViewModel.onNavigated]. */
    val draftReady: Boolean = false,
)

class ScanViewModel(private val invoiceRepository: InvoiceRepository) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    // The camera reports the same code many times a second while it's in frame; a rejected one
    // is only reported once.
    private var lastRejected: String? = null
    private var clearErrorJob: Job? = null

    fun onQrCode(payload: String) {
        if (_state.value.draftReady || payload == lastRejected) return
        accept(payload)
    }

    /** A payload pasted by hand is always (re)tried, even if the camera rejected it before. */
    fun onPasted(payload: String) {
        if (_state.value.draftReady) return
        accept(payload)
    }

    fun onManualEntry() {
        invoiceRepository.startManualDraft()
        _state.update { it.copy(draftReady = true, error = null) }
    }

    fun onNavigated() {
        _state.update { it.copy(draftReady = false) }
    }

    private fun accept(payload: String) {
        when (val result = invoiceRepository.startDraftFromQr(payload)) {
            is QrParseResult.Success -> {
                clearErrorJob?.cancel()
                _state.update { it.copy(draftReady = true, error = null) }
            }
            is QrParseResult.Failure -> {
                lastRejected = payload
                _state.update { it.copy(error = result.reason) }
                clearErrorJob?.cancel()
                clearErrorJob = viewModelScope.launch {
                    delay(3500)
                    _state.update { it.copy(error = null) }
                }
            }
        }
    }
}
