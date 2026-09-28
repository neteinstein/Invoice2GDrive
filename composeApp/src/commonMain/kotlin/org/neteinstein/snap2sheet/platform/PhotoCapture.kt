package org.neteinstein.snap2sheet.platform

import androidx.compose.runtime.Composable

/** A photo of the whole invoice, already scaled down (≤ 2000 px) and JPEG-encoded — or a PDF on the web. */
class CapturedPhoto(val bytes: ByteArray, val mimeType: String)

enum class PhotoSource { CAMERA, LIBRARY }

/**
 * Returns a launcher for the system camera / photo library (Android `TakePicture` and
 * `PickVisualMedia`, iOS `UIImagePickerController`, a file input on the web). [onResult] gets
 * null when the user cancels.
 */
@Composable
expect fun rememberPhotoPicker(onResult: (CapturedPhoto?) -> Unit): (PhotoSource) -> Unit
