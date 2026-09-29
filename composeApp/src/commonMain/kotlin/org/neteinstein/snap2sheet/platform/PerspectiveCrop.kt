package org.neteinstein.snap2sheet.platform

/**
 * Crops the region bounded by [corners] out of a JPEG/PNG photo and straightens it into a
 * rectangle (perspective correction). [corners] holds 8 values — x,y of top-left, top-right,
 * bottom-right, bottom-left — each as a fraction (0..1) of the image's width / height.
 * Returns JPEG bytes, or null when the image can't be decoded or the corners are degenerate.
 */
expect suspend fun cropPerspective(bytes: ByteArray, corners: FloatArray): ByteArray?
