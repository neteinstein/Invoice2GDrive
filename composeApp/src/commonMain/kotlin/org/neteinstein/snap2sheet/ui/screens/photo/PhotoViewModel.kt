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
import org.neteinstein.snap2sheet.platform.cropPerspective
import org.neteinstein.snap2sheet.ui.components.defaultCorners

data class PhotoUiState(
    val invoice: Invoice? = null,
    /** The attached photo's bytes for the preview; null when there's none (or it's a PDF). */
    val previewBytes: ByteArray? = null,
    val isPdf: Boolean = false,
    val isSaving: Boolean = false,
    /** A freshly captured image waiting for the user to adjust the crop corners. */
    val pendingBytes: ByteArray? = null,
    val corners: FloatArray = defaultCorners(),
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
        if (!photo.mimeType.startsWith("image/")) {
            attach(photo.bytes, photo.mimeType)
            return
        }
        _state.update { it.copy(pendingBytes = photo.bytes, corners = defaultCorners()) }
    }

    fun onCornersChange(corners: FloatArray) = _state.update { it.copy(corners = corners) }

    /** Discards the captured image without attaching anything. */
    fun cancelCrop() = _state.update { it.copy(pendingBytes = null) }

    /** Attaches the pending image, cropped to the chosen corners unless [crop] is false. */
    fun confirmCrop(crop: Boolean) {
        val current = _state.value
        val original = current.pendingBytes ?: return
        _state.update { it.copy(pendingBytes = null, isSaving = true) }
        viewModelScope.launch {
            val bytes = if (crop) cropPerspective(original, current.corners) ?: original else original
            invoiceRepository.attachPhoto(bytes, "image/jpeg")
            _state.update { it.copy(isSaving = false) }
        }
    }

    private fun attach(bytes: ByteArray, mimeType: String) {
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            invoiceRepository.attachPhoto(bytes, mimeType)
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun removePhoto() = invoiceRepository.removeDraftPhoto()
}
