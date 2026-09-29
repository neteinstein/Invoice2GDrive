package org.neteinstein.snap2sheet.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.hypot
import kotlin.math.roundToInt

actual suspend fun cropPerspective(bytes: ByteArray, corners: FloatArray): ByteArray? = withContext(Dispatchers.Default) {
    runCatching {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
        val src = FloatArray(8) { if (it % 2 == 0) corners[it] * source.width else corners[it] * source.height }
        fun edge(a: Int, b: Int) = hypot(src[a * 2] - src[b * 2], src[a * 2 + 1] - src[b * 2 + 1])
        val width = ((edge(0, 1) + edge(3, 2)) / 2f).roundToInt()
        val height = ((edge(0, 3) + edge(1, 2)) / 2f).roundToInt()
        if (width < 16 || height < 16) return@runCatching null

        val matrix = Matrix()
        val dst = floatArrayOf(0f, 0f, width.toFloat(), 0f, width.toFloat(), height.toFloat(), 0f, height.toFloat())
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return@runCatching null
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }.getOrNull()
}
