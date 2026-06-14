package com.cocode.measureapp.ui

import android.graphics.Paint as NativePaint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.ui.theme.Amber
import com.cocode.measureapp.ui.theme.GreenRead
import com.cocode.measureapp.ui.theme.OverlayHandle
import com.cocode.measureapp.ui.theme.OverlayObject
import com.cocode.measureapp.ui.theme.OverlayStick
import com.cocode.measureapp.ui.theme.TextPrimary
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val LOUPE_R = 175f
private const val LOUPE_ZOOM = 3f

/** Distance (in display px) within which a touch is considered a handle grab. */
fun handleGrabThresholdPx(density: Float): Float = 60.dp.value * density

/**
 * Returns the index of the nearest handle (0..3 object, 4..7 stick) if it is within
 * [thresholdPx] of [pos], or -1 (no grab → pan).
 */
fun nearestHandleOrPan(
    pos: Offset,
    corners: List<Vec2>,
    stick: List<Vec2>,
    s: Float,
    tx: Float,
    ty: Float,
    thresholdPx: Float,
): Int {
    var best = -1
    var bestD = Float.MAX_VALUE
    (corners + stick).forEachIndexed { i, p ->
        val d = hypot(pos.x - (p.x.toFloat() * s + tx), pos.y - (p.y.toFloat() * s + ty))
        if (d < bestD) { bestD = d; best = i }
    }
    return if (bestD <= thresholdPx) best else -1
}

/** Index of the handle nearest [pos] — always returns one (legacy, used by tests). */
fun nearestHandle(
    pos: Offset,
    corners: List<Vec2>,
    stick: List<Vec2>,
    s: Float,
    tx: Float,
    ty: Float,
): Int {
    var best = 0
    var bestD = Float.MAX_VALUE
    (corners + stick).forEachIndexed { i, p ->
        val d = hypot(pos.x - (p.x.toFloat() * s + tx), pos.y - (p.y.toFloat() * s + ty))
        if (d < bestD) { bestD = d; best = i }
    }
    return best
}

/**
 * Draws a quad outline + draggable handles using brand colours.
 * [label] is drawn at the top-left corner of the quad.
 * The active handle (index base+i) is highlighted with white.
 */
fun DrawScope.drawQuad(
    pts: List<Offset>,
    color: Color,
    active: Int,
    base: Int,
    label: String = "",
) {
    for (i in pts.indices) drawLine(color, pts[i], pts[(i + 1) % pts.size], strokeWidth = 3f)
    pts.forEachIndexed { i, p ->
        val isActive = active == base + i
        drawCircle(if (isActive) OverlayHandle else color, 20f, p)
        drawCircle(Color.Black, 20f, p, style = Stroke(2f))
    }
    if (label.isNotEmpty() && pts.isNotEmpty()) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(
                label,
                pts[0].x + 8f,
                pts[0].y - 8f,
                NativePaint().apply {
                    setColor(android.graphics.Color.WHITE)
                    textSize = 36f
                    isAntiAlias = true
                },
            )
        }
    }
}

/**
 * Circular loupe magnifying the image around the active handle for precise placement.
 * Sits at the top of the screen when the finger is low; bottom otherwise.
 */
fun DrawScope.drawMagnifier(img: ImageBitmap, handleImg: Vec2, handleScreen: Offset, s: Float) {
    val cy = if (handleScreen.y > size.height / 2f) LOUPE_R + 24f else size.height - LOUPE_R - 24f
    val center = Offset(size.width / 2f, cy)
    val half = (LOUPE_R / (s * LOUPE_ZOOM)).toDouble()
    val full = 2 * half
    val sx = (handleImg.x - half).coerceIn(0.0, (img.width - full).coerceAtLeast(0.0))
    val sy = (handleImg.y - half).coerceIn(0.0, (img.height - full).coerceAtLeast(0.0))
    val ssz = IntSize(
        full.roundToInt().coerceIn(1, img.width),
        full.roundToInt().coerceIn(1, img.height),
    )
    val path = Path().apply {
        addOval(Rect(center.x - LOUPE_R, center.y - LOUPE_R, center.x + LOUPE_R, center.y + LOUPE_R))
    }
    clipPath(path) {
        drawImage(
            img,
            srcOffset = IntOffset(sx.roundToInt(), sy.roundToInt()),
            srcSize = ssz,
            dstOffset = IntOffset((center.x - LOUPE_R).roundToInt(), (center.y - LOUPE_R).roundToInt()),
            dstSize = IntSize((2 * LOUPE_R).roundToInt(), (2 * LOUPE_R).roundToInt()),
        )
    }
    drawCircle(TextPrimary, LOUPE_R, center, style = Stroke(3f))
    drawLine(TextPrimary, Offset(center.x - LOUPE_R, center.y), Offset(center.x + LOUPE_R, center.y), 1.5f)
    drawLine(TextPrimary, Offset(center.x, center.y - LOUPE_R), Offset(center.x, center.y + LOUPE_R), 1.5f)
    drawCircle(Amber, 7f, center)
}

/** Brand colour for the object (cyan → GreenRead) quad. */
val objectQuadColor: Color get() = OverlayObject

/** Brand colour for the stick (red → StaffRed) quad. */
val stickQuadColor: Color get() = OverlayStick

/** Brand colour for stick end dots (yellow → Amber). */
val stickEndDotColor: Color get() = OverlayHandle
