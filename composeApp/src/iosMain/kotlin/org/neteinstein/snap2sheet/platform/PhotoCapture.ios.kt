package org.neteinstein.snap2sheet.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.useContents
import org.neteinstein.snap2sheet.data.local.toByteArray
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

private const val MAX_EDGE = 2000.0

@Composable
actual fun rememberPhotoPicker(onResult: (CapturedPhoto?) -> Unit): (PhotoSource) -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    val delegate = remember { PickerDelegate { currentOnResult.value(it) } }
    return remember(delegate) {
        { source ->
            val sourceType = if (source == PhotoSource.CAMERA &&
                UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)
            ) {
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            } else {
                // The simulator (and camera-less iPads) fall back to the library.
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            }
            val picker = UIImagePickerController().apply {
                this.sourceType = sourceType
                this.delegate = delegate
            }
            topViewController()?.presentViewController(picker, animated = true, completion = null)
                ?: currentOnResult.value(null)
        }
    }
}

private class PickerDelegate(private val onResult: (CapturedPhoto?) -> Unit) :
    NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, completion = null)
        onResult(image?.let(::normalize))
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onResult(null)
    }
}

/** Redraws the photo upright (baking in its orientation), at most [MAX_EDGE] px, as JPEG. */
private fun normalize(image: UIImage): CapturedPhoto? {
    val (width, height) = image.size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val scale = minOf(1.0, MAX_EDGE / maxOf(width, height))
    val targetWidth = width * scale
    val targetHeight = height * scale
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { this.scale = 1.0 }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(targetWidth, targetHeight), format = format)
    val upright = renderer.imageWithActions { _ -> image.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight)) }
    val data = UIImageJPEGRepresentation(upright, 0.82) ?: return null
    return CapturedPhoto(data.toByteArray(), "image/jpeg")
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}
