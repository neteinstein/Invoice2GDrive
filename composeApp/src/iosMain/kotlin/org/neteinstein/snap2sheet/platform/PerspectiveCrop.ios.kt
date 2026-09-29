package org.neteinstein.snap2sheet.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.neteinstein.snap2sheet.data.local.toByteArray
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.CIVector
import platform.CoreImage.createCGImage
import platform.CoreImage.filterWithName
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.setValue
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

actual suspend fun cropPerspective(bytes: ByteArray, corners: FloatArray): ByteArray? {
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    val image = UIImage.imageWithData(data) ?: return null
    val input = CIImage(cGImage = image.CGImage)
    val (w, h) = input.extent.useContents { size.width to size.height }
    if (w <= 0.0 || h <= 0.0) return null

    // Core Image's origin is bottom-left, so flip y.
    fun point(i: Int) = CIVector.vectorWithX(corners[i * 2] * w, Y = (1.0 - corners[i * 2 + 1]) * h)
    val filter = CIFilter.filterWithName("CIPerspectiveCorrection") ?: return null
    filter.setValue(input, forKey = "inputImage")
    filter.setValue(point(0), forKey = "inputTopLeft")
    filter.setValue(point(1), forKey = "inputTopRight")
    filter.setValue(point(2), forKey = "inputBottomRight")
    filter.setValue(point(3), forKey = "inputBottomLeft")
    val output = filter.outputImage ?: return null
    val extent = output.extent
    if (CGRectGetWidth(extent) < 16.0 || CGRectGetHeight(extent) < 16.0) return null
    val cg = CIContext().createCGImage(output, fromRect = extent) ?: return null
    return UIImageJPEGRepresentation(UIImage.imageWithCGImage(cg), 0.85)?.toByteArray()
}
