package org.neteinstein.snap2sheet.ui.screens.photo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.neteinstein.snap2sheet.data.repository.InvoiceRepository
import org.neteinstein.snap2sheet.domain.model.Invoice
import org.neteinstein.snap2sheet.platform.CapturedPhoto

data class PhotoUiState(
    val invoice: Invoice? = null,
    /** The attached photo's bytes for the preview; null when there's none (or it's a PDF). */
    val previewBytes: ByteArray? = null,
    val isPdf: Boolean = false,
    val isSaving: Boolean = false,
) {
    val hasPhoto: Boolean get() = invoice?.photo != null
}

class PhotoViewModel(private val invoiceRepository: InvoiceRepository) : ViewModel() {

    private val _state = MutableStateFlow(PhotoUiState(invoice = invoiceRepository.draftInvoice.value))
    val state: StateFlow<PhotoUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            invoiceRepository.draftInvoice.distinctUntilChangedBy { it?.photo }.collectLatest { draft ->
                val photo = draft?.photo
                val bytes = photo?.takeIf { it.mimeType.startsWith("image/") }?.let { invoiceRepository.loadPhoto(it) }
                _state.update { it.copy(invoice = draft, previewBytes = bytes, isPdf = photo?.mimeType == "application/pdf") }
            }
        }
    }

    fun onCaptured(photo: CapturedPhoto?) {
        if (photo == null) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            invoiceRepository.attachPhoto(photo.bytes, photo.mimeType)
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun removePhoto() = invoiceRepository.removeDraftPhoto()
}
