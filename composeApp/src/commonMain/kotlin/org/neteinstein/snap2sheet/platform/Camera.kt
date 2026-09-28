package org.neteinstein.snap2sheet.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

enum class PermissionStatus { GRANTED, DENIED, NOT_DETERMINED }

@Stable
interface PermissionState {
    /** Observable: reading it from composition recomposes when the answer changes. */
    val status: PermissionStatus

    /** Shows the system prompt (a no-op once denied for good — use [openSettings] then). */
    fun request()

    /** Opens the app's system settings, where a denied permission can be re-enabled. */
    fun openSettings()

    /** False where the OS has no per-app settings screen to send the user to (the browser). */
    val canOpenSettings: Boolean
}

@Composable
expect fun rememberCameraPermissionState(): PermissionState

/**
 * True on Android/iOS, where [QrScanner] is a live preview drawn inside the Compose layout. On
 * the web the browser scanner is a DOM overlay on top of the whole page, so [QrScanner] draws
 * nothing and returns after each scan.
 */
expect val hasInlineCameraPreview: Boolean

/**
 * Scans QR codes with the back camera: CameraX + ML Kit on Android, AVFoundation on iOS,
 * `getUserMedia` + `BarcodeDetector`/jsQR on the web. [onQrCode] may fire repeatedly for the
 * same code while it stays in frame; callers dedupe. [onClosed] only fires on the web, when the
 * user dismisses the overlay without scanning anything. Compose only once camera permission is
 * granted (except on the web, where the overlay asks for it itself).
 */
@Composable
expect fun QrScanner(onQrCode: (String) -> Unit, onClosed: () -> Unit, modifier: Modifier = Modifier)
