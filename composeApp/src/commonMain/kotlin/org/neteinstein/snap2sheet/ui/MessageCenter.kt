package org.neteinstein.snap2sheet.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-off messages ("Saved to Despesas 2026", "Couldn't save…") that outlive the screen that
 * produced them — a save finishes on Destination but is announced on Home. `App` shows them in
 * a snackbar and [consume]s them.
 */
class MessageCenter {
    private val _current = MutableStateFlow<String?>(null)
    val current: StateFlow<String?> = _current.asStateFlow()

    fun show(message: String) {
        _current.value = message
    }

    fun consume() {
        _current.value = null
    }
}
