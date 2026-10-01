package org.neteinstein.snap2sheet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

/** The default crop: the whole photo, slightly inset so the handles are easy to grab. */
fun defaultCorners(): FloatArray = floatArrayOf(0.06f, 0.06f, 0.94f, 0.06f, 0.94f, 0.94f, 0.06f, 0.94f)

/**
 * Shows [image] with four draggable corner handles. [corners] (8 fractions of the image size:
 * TL, TR, BR, BL) is reported through [onCornersChange] while dragging.
 */
@Composable
fun CornerEditor(
    image: ImageBitmap,
    corners: FloatArray,
    onCornersChange: (FloatArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    var active by remember { mutableStateOf(-1) }
    // The drag handler below is installed once per image/size, so it must read the latest corners
    // and callback through these holders instead of the values captured when it was created.
    val currentCorners by rememberUpdatedState(corners)
    val currentOnCornersChange by rememberUpdatedState(onCornersChange)

    // Where the image is drawn (ContentScale.Fit, centred) inside the box.
    val scale = if (box.width == 0) 1f else minOf(box.width / image.width.toFloat(), box.height / image.height.toFloat())
    val drawnW = image.width * scale
    val drawnH = image.height * scale
    val originX = (box.width - drawnW) / 2f
    val originY = (box.height - drawnH) / 2f

    val accent = FaturaColors.Accent

    fun toScreen(i: Int) = Offset(originX + currentCorners[i * 2] * drawnW, originY + currentCorners[i * 2 + 1] * drawnH)

    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { box = it }
            .pointerInput(image, box) {
                val grabRadius = 56.dp.toPx()
                detectDragGestures(
                    onDragStart = { start ->
                        val nearest = (0..3).minByOrNull { (toScreen(it) - start).getDistance() } ?: -1
                        active = if (nearest >= 0 && (toScreen(nearest) - start).getDistance() <= grabRadius) nearest else -1
                    },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                ) { change, drag ->
                    val i = active
                    if (i < 0 || drawnW <= 0f || drawnH <= 0f) return@detectDragGestures
                    change.consume()
                    val next = currentCorners.copyOf()
                    next[i * 2] = (next[i * 2] + drag.x / drawnW).coerceIn(0f, 1f)
                    next[i * 2 + 1] = (next[i * 2 + 1] + drag.y / drawnH).coerceIn(0f, 1f)
                    currentOnCornersChange(next)
                }
            },
    ) {
        Image(bitmap = image, contentDescription = "Invoice photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pts = (0..3).map { toScreen(it) }
            val quad = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                (1..3).forEach { lineTo(pts[it].x, pts[it].y) }
                close()
            }
            drawPath(quad, accent.copy(alpha = 0.18f), style = Fill)
            drawPath(quad, accent, style = Stroke(width = 2.dp.toPx()))
            pts.forEachIndexed { i, p ->
                drawCircle(Color.White, radius = 13.dp.toPx(), center = p)
                drawCircle(accent, radius = 13.dp.toPx(), center = p, style = Stroke(width = if (i == active) 5.dp.toPx() else 3.dp.toPx()))
            }
        }
    }
}
