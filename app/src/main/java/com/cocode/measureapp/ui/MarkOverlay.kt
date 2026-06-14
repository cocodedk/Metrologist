package com.cocode.measureapp.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.cocode.measureapp.geometry.Vec2
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val LOUPE_R = 175f
private const val LOUPE_ZOOM = 3f

/** Draws a quad outline + draggable handles; the active handle (index base+i) is highlighted. */
fun DrawScope.drawQuad(pts: List<Offset>, color: Color, active: Int, base: Int) {
    for (i in pts.indices) drawLine(color, pts[i], pts[(i + 1) % pts.size], strokeWidth = 3f)
    pts.forEachIndexed { i, p ->
        drawCircle(if (active == base + i) Color.White else color, 20f, p)
        drawCircle(Color.Black, 20f, p, style = Stroke(2f))
    }
}

/**
 * Circular loupe magnifying the image around the active handle, for precise placement. Sits at the
 * top of the screen when the finger is low, and at the bottom when the finger is high, so the
 * fingertip never covers it. [handleImg] is the handle in image px; [s] is the current image scale.
 */
fun DrawScope.drawMagnifier(img: ImageBitmap, handleImg: Vec2, handleScreen: Offset, s: Float) {
    val cy = if (handleScreen.y > size.height / 2f) LOUPE_R + 24f else size.height - LOUPE_R - 24f
    val center = Offset(size.width / 2f, cy)
    val half = (LOUPE_R / (s * LOUPE_ZOOM)).toDouble()
    val full = (2 * half)
    val sx = (handleImg.x - half).coerceIn(0.0, (img.width - full).coerceAtLeast(0.0))
    val sy = (handleImg.y - half).coerceIn(0.0, (img.height - full).coerceAtLeast(0.0))
    val ssz = IntSize(full.roundToInt().coerceIn(1, img.width), full.roundToInt().coerceIn(1, img.height))
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
    drawCircle(Color.White, LOUPE_R, center, style = Stroke(3f))
    drawLine(Color.White, Offset(center.x - LOUPE_R, center.y), Offset(center.x + LOUPE_R, center.y), 1.5f)
    drawLine(Color.White, Offset(center.x, center.y - LOUPE_R), Offset(center.x, center.y + LOUPE_R), 1.5f)
    drawCircle(Color.Yellow, 7f, center)
}

/** Index of the handle nearest [pos] (0..3 object corners, 4..7 stick corners). Always returns one. */
fun nearestHandle(pos: Offset, corners: List<Vec2>, stick: List<Vec2>, s: Float, tx: Float, ty: Float): Int {
    var best = 0
    var bestD = Float.MAX_VALUE
    (corners + stick).forEachIndexed { i, p ->
        val d = hypot(pos.x - (p.x.toFloat() * s + tx), pos.y - (p.y.toFloat() * s + ty))
        if (d < bestD) { bestD = d; best = i }
    }
    return best
}
