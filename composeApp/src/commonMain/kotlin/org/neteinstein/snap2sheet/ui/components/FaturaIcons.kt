package org.neteinstein.snap2sheet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Small, dependency-free stand-ins for the design's inline SVG icons (chevrons, the document/
 * spreadsheet glyph, the scan grid, etc). The loopgain-style dependency set doesn't pull in
 * material-icons-extended, and hand-drawing these keeps the app visually close to the Fatura
 * design rather than substituting generic Material glyphs.
 */
object FaturaIcons {

    @Composable
    fun Back(modifier: Modifier = Modifier, tint: Color, size: androidx.compose.ui.unit.Dp = 20.dp) {
        Canvas(modifier.size(size)) {
            val stroke = Stroke(width = this.size.width * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.62f, h * 0.15f)
                lineTo(w * 0.28f, h * 0.5f)
                lineTo(w * 0.62f, h * 0.85f)
            }
            drawPath(path, color = tint, style = stroke)
        }
    }

    @Composable
    fun ChevronRight(modifier: Modifier = Modifier, tint: Color, size: androidx.compose.ui.unit.Dp = 18.dp) {
        Canvas(modifier.size(size)) {
            val stroke = Stroke(width = this.size.width * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.38f, h * 0.15f)
                lineTo(w * 0.72f, h * 0.5f)
                lineTo(w * 0.38f, h * 0.85f)
            }
            drawPath(path, color = tint, style = stroke)
        }
    }

    @Composable
    fun Search(modifier: Modifier = Modifier, tint: Color, size: androidx.compose.ui.unit.Dp = 18.dp) {
        Canvas(modifier.size(size)) {
            val stroke = Stroke(width = this.size.width * 0.1f, cap = StrokeCap.Round)
            val r = this.size.width * 0.32f
            val center = Offset(this.size.width * 0.42f, this.size.height * 0.42f)
            drawCircle(color = tint, radius = r, center = center, style = stroke)
            drawLine(
                color = tint,
                start = Offset(center.x + r * 0.75f, center.y + r * 0.75f),
                end = Offset(this.size.width * 0.9f, this.size.height * 0.9f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
    }

    @Composable
    fun Plus(modifier: Modifier = Modifier, tint: Color, size: androidx.compose.ui.unit.Dp = 16.dp) {
        Canvas(modifier.size(size)) {
            val stroke = Stroke(width = this.size.width * 0.14f, cap = StrokeCap.Round)
            drawLine(tint, Offset(this.size.width / 2, 0f), Offset(this.size.width / 2, this.size.height), stroke.width, StrokeCap.Round)
            drawLine(tint, Offset(0f, this.size.height / 2), Offset(this.size.width, this.size.height / 2), stroke.width, StrokeCap.Round)
        }
    }

    /** The rounded document / spreadsheet glyph used for merchants, sheets and empty states. */
    @Composable
    fun Document(modifier: Modifier = Modifier, tint: Color, size: androidx.compose.ui.unit.Dp = 20.dp) {
        Canvas(modifier.size(size)) {
            val stroke = Stroke(width = this.size.width * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.16f, h * 0.14f),
                size = Size(w * 0.68f, h * 0.72f),
                cornerRadius = CornerRadius(w * 0.1f, w * 0.1f),
                style = stroke,
            )
            val lineXStart = w * 0.16f
            val lineXEnd = w * 0.84f
            drawLine(tint, Offset(lineXStart, h * 0.4f), Offset(lineXEnd, h * 0.4f), stroke.width * 0.85f)
            drawLine(tint, Offset(lineXStart, h * 0.6f), Offset(lineXEnd, h * 0.6f), stroke.width * 0.85f)
            drawLine(tint, Offset(w * 0.5f, h * 0.4f), Offset(w * 0.5f, h * 0.86f), stroke.width * 0.85f)
        }
    }

    /** A Drive folder: body with a tab on the top-left. */
    @Composable
    fun Folder(modifier: Modifier = Modifier, tint: Color, size: androidx.compose.ui.unit.Dp = 20.dp) {
        Canvas(modifier.size(size)) {
            val stroke = Stroke(width = this.size.width * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.12f, h * 0.26f)
                lineTo(w * 0.40f, h * 0.26f)
                lineTo(w * 0.48f, h * 0.36f)
                lineTo(w * 0.88f, h * 0.36f)
                lineTo(w * 0.88f, h * 0.80f)
                lineTo(w * 0.12f, h * 0.80f)
                close()
            }
            drawPath(path, color = tint, style = stroke)
        }
    }

    /** The four-square "scan grid" logo mark. */
    @Composable
    fun Grid(modifier: Modifier = Modifier, tint: Color, size: androidx.compose.ui.unit.Dp = 28.dp) {
        Canvas(modifier.size(size)) {
            val w = this.size.width
            val big = w * 0.3f
            val small = w * 0.13f
            val gap = w * 0.04f
            fun sq(x: Float, y: Float, s: Float, r: Float) =
                drawRoundRect(tint, topLeft = Offset(x, y), size = Size(s, s), cornerRadius = CornerRadius(r, r))
            sq(w * 0.12f, w * 0.12f, big, w * 0.04f)
            sq(w * 0.12f + big + gap, w * 0.12f, big, w * 0.04f)
            sq(w * 0.12f, w * 0.12f + big + gap, big, w * 0.04f)
            val sx = w * 0.12f + big + gap
            val sy = w * 0.12f + big + gap
            sq(sx, sy, small, w * 0.02f)
            sq(sx + small + gap * 0.6f, sy, small, w * 0.02f)
            sq(sx, sy + small + gap * 0.6f, small, w * 0.02f)
            sq(sx + small + gap * 0.6f, sy + small + gap * 0.6f, small, w * 0.02f)
        }
    }

    @Composable
    fun Gear(modifier: Modifier = Modifier, tint: Color, size: androidx.compose.ui.unit.Dp = 20.dp) {
        Canvas(modifier.size(size)) {
            val stroke = Stroke(width = this.size.width * 0.1f, cap = StrokeCap.Round)
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val r = this.size.width * 0.16f
            drawCircle(tint, radius = r, center = center, style = stroke)
            val spokeLen = this.size.width * 0.42f
            for (i in 0 until 8) {
                val angle = (i * 45.0) * (kotlin.math.PI / 180.0)
                val inner = Offset(
                    center.x + (spokeLen * 0.62f) * kotlin.math.cos(angle).toFloat(),
                    center.y + (spokeLen * 0.62f) * kotlin.math.sin(angle).toFloat(),
                )
                val outer = Offset(
                    center.x + spokeLen * kotlin.math.cos(angle).toFloat(),
                    center.y + spokeLen * kotlin.math.sin(angle).toFloat(),
                )
                drawLine(tint, inner, outer, stroke.width, StrokeCap.Round)
            }
        }
    }

    @Composable
    fun Clock(modifier: Modifier = Modifier, tint: Color, size: androidx.compose.ui.unit.Dp = 20.dp) {
        Canvas(modifier.size(size)) {
            val stroke = Stroke(width = this.size.width * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val r = this.size.width * 0.38f
            drawCircle(tint, radius = r, center = center, style = stroke)
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x, center.y - r * 0.6f)
                lineTo(center.x, center.y)
                lineTo(center.x + r * 0.5f, center.y + r * 0.35f)
            }
            drawPath(path, color = tint, style = stroke)
        }
    }

    @Composable
    fun Camera(modifier: Modifier = Modifier, tint: Color, size: androidx.compose.ui.unit.Dp = 20.dp) {
        Canvas(modifier.size(size)) {
            val stroke = Stroke(width = this.size.width * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.1f, h * 0.28f),
                size = Size(w * 0.8f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                style = stroke,
            )
            val bumpPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.32f, h * 0.28f)
                lineTo(w * 0.4f, h * 0.16f)
                lineTo(w * 0.6f, h * 0.16f)
                lineTo(w * 0.68f, h * 0.28f)
            }
            drawPath(bumpPath, color = tint, style = stroke)
            drawCircle(tint, radius = w * 0.16f, center = Offset(w / 2, h * 0.58f), style = stroke)
        }
    }

    @Composable
    fun ScanCorners(modifier: Modifier = Modifier, tint: Color) {
        Canvas(modifier) {
            val w = this.size.width
            val h = this.size.height
            val len = w * 0.14f
            val stroke = Stroke(width = w * 0.014f, cap = StrokeCap.Round)
            fun corner(x: Float, y: Float, dx: Int, dy: Int) {
                drawLine(tint, Offset(x, y), Offset(x + len * dx, y), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(x, y), Offset(x, y + len * dy), stroke.width, StrokeCap.Round)
            }
            corner(0f, 0f, 1, 1)
            corner(w, 0f, -1, 1)
            corner(0f, h, 1, -1)
            corner(w, h, -1, -1)
        }
    }
}
