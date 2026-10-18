package org.neteinstein.snap2sheet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

/**
 * Small, dependency-free stand-ins for the design's inline SVG icons (chevrons, the document/
 * spreadsheet glyph, the scan grid, etc). The loopgain-style dependency set doesn't pull in
 * material-icons-extended, and hand-drawing these keeps the app visually close to the Fatura
 * design rather than substituting generic Material glyphs.
 */
object FaturaIcons {

    /** Sun (shown in dark mode, to switch to light). */
    @Composable
    fun Sun(modifier: Modifier = Modifier, tint: Color, size: androidx.compose.ui.unit.Dp = 20.dp) {
        Canvas(modifier.size(size)) {
            val w = this.size.width
            val c = androidx.compose.ui.geometry.Offset(w / 2, w / 2)
            drawCircle(tint, radius = w * 0.2f, center = c)
            val stroke = w * 0.09f
            for (i in 0 until 8) {
                val a = i * kotlin.math.PI / 4
                val dx = kotlin.math.cos(a).toFloat()
                val dy = kotlin.math.sin(a).toFloat()
                drawLine(
                    tint,
                    start = androidx.compose.ui.geometry.Offset(c.x + dx * w * 0.32f, c.y + dy * w * 0.32f),
                    end = androidx.compose.ui.geometry.Offset(c.x + dx * w * 0.44f, c.y + dy * w * 0.44f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }

    /** Crescent moon (shown in light mode, to switch to dark). */
    @Composable
    fun Moon(modifier: Modifier = Modifier, tint: Color, size: androidx.compose.ui.unit.Dp = 20.dp) {
        Canvas(modifier.size(size)) {
            val w = this.size.width
            val disc = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset(w * 0.15f, w * 0.15f), androidx.compose.ui.geometry.Size(w * 0.7f, w * 0.7f)))
            }
            val bite = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset(w * 0.4f, w * 0.05f), androidx.compose.ui.geometry.Size(w * 0.65f, w * 0.65f)))
            }
            val moon = androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, disc, bite)
            drawPath(moon, tint)
        }
    }

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

    /**
     * The full-colour app icon (docs/logo/logo.svg): a receipt with a coral check badge, on the
     * violet gradient tile. Drawn in the SVG's 1024-unit space and scaled to [size].
     */
    @Composable
    fun AppLogo(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 72.dp) {
        Canvas(modifier.size(size)) {
            scale(this.size.width / 1024f, pivot = Offset.Zero) {
                drawRoundRect(
                    Brush.linearGradient(listOf(Color(0xFF5B3BD6), Color(0xFF2F1A86)), start = Offset.Zero, end = Offset(1024f, 1024f)),
                    size = Size(1024f, 1024f),
                    cornerRadius = CornerRadius(228f, 228f),
                )
                val shadow = Color(0xFF150B45).copy(alpha = 0.3f)

                // Receipt with a torn (zigzag) bottom edge.
                val receipt = Path().apply {
                    moveTo(298f, 196f)
                    lineTo(594f, 196f)
                    arcTo(Rect(558f, 196f, 630f, 268f), -90f, 90f, false)
                    lineTo(630f, 800f)
                    var x = 630f
                    while (x > 262f) {
                        lineTo(x - 23f, 776f)
                        lineTo(x - 46f, 800f)
                        x -= 46f
                    }
                    lineTo(262f, 232f)
                    arcTo(Rect(262f, 196f, 334f, 268f), 180f, 90f, false)
                    close()
                }
                translate(top = 14f) { drawPath(receipt, shadow) }
                drawPath(receipt, Color.White)

                drawRoundRect(Color(0xFF4B2DB3), topLeft = Offset(314f, 266f), size = Size(180f, 40f), cornerRadius = CornerRadius(20f, 20f))
                val lineColor = Color(0xFFDCD3F7)
                fun line(y: Float, w: Float) =
                    drawRoundRect(lineColor, topLeft = Offset(314f, y), size = Size(w, 24f), cornerRadius = CornerRadius(12f, 12f))
                line(350f, 264f)
                line(402f, 200f)
                line(454f, 264f)
                line(526f, 110f)

                // The "saved" check badge.
                drawCircle(shadow, radius = 140f, center = Offset(640f, 714f))
                drawCircle(Color(0xFFFF6A3D), radius = 140f, center = Offset(640f, 700f))
                val check = Path().apply {
                    moveTo(576f, 704f); lineTo(622f, 750f); lineTo(708f, 656f)
                }
                drawPath(check, Color.White, style = Stroke(width = 42f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
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

    /** Circular arrow, for reloading a list. */
    @Composable
    fun Refresh(modifier: Modifier = Modifier, tint: Color, size: androidx.compose.ui.unit.Dp = 20.dp) {
        Canvas(modifier.size(size)) {
            val w = this.size.width
            val stroke = Stroke(width = w * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val r = w * 0.32f
            val c = Offset(w / 2, w / 2)
            // An open ring with a gap at the top right; the arrowhead sits at its end.
            drawArc(tint, startAngle = -40f, sweepAngle = 300f, useCenter = false, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = stroke)
            val endAngle = -100.0 * kotlin.math.PI / 180.0
            val end = Offset(c.x + r * kotlin.math.cos(endAngle).toFloat(), c.y + r * kotlin.math.sin(endAngle).toFloat())
            val head = androidx.compose.ui.graphics.Path().apply {
                moveTo(end.x - w * 0.02f, end.y - w * 0.16f)
                lineTo(end.x + w * 0.14f, end.y)
                lineTo(end.x - w * 0.02f, end.y + w * 0.16f)
            }
            drawPath(head, color = tint, style = stroke)
        }
    }

    /** A door with an arrow leaving it, for signing out. */
    @Composable
    fun SignOut(modifier: Modifier = Modifier, tint: Color, size: androidx.compose.ui.unit.Dp = 20.dp) {
        Canvas(modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val stroke = Stroke(width = w * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val door = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.45f, h * 0.15f)
                lineTo(w * 0.18f, h * 0.15f)
                lineTo(w * 0.18f, h * 0.85f)
                lineTo(w * 0.45f, h * 0.85f)
            }
            drawPath(door, color = tint, style = stroke)
            drawLine(tint, Offset(w * 0.4f, h * 0.5f), Offset(w * 0.86f, h * 0.5f), stroke.width, StrokeCap.Round)
            val head = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.7f, h * 0.33f)
                lineTo(w * 0.87f, h * 0.5f)
                lineTo(w * 0.7f, h * 0.67f)
            }
            drawPath(head, color = tint, style = stroke)
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
