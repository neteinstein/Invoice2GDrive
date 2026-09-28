package org.neteinstein.snap2sheet.platform

import androidx.compose.runtime.Composable

/** Local notifications — "Invoice saved" / "Couldn't save invoice" when a background save finishes. */
interface Notifier {
    fun notify(title: String, message: String)
}

expect fun platformNotifier(): Notifier

@Composable
expect fun rememberNotificationPermissionState(): PermissionState
