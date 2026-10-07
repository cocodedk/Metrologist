package com.cocode.measureapp.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.stick.StickBox

/**
 * The photo with its two adjustable boxes. One finger always grabs the nearest handle and moves
 * it; two fingers zoom and pan. The zoom and pan live here and start over whenever [viewResets]
 * changes. [onMarksEdited] is called once a drag that moved a handle has finished.
 */
@Composable
internal fun MarkCanvas(
    bmp: Bitmap,
    img: ImageBitmap,
    corners: SnapshotStateList<Vec2>,
    stick: SnapshotStateList<Vec2>,
    objectLabel: String,
    stickLabel: String,
    viewResets: Int,
    onMarksEdited: () -> Unit,
) {
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var active by remember { mutableStateOf(-1) }
    var zoom by remember(viewResets) { mutableStateOf(1f) }
    var pan by remember(viewResets) { mutableStateOf(Offset.Zero) }
    val reportEdited by rememberUpdatedState(onMarksEdited)

    // Fill the screen rather than fit inside it: a 16:9 photo on a 20:9 screen left a fifth of
    // the view as empty bands, and the marks live in image coordinates either way. Pinching
    // below 1 zooms out past the fill, which is how the edges of the frame stay reachable.
    fun fit() = if (canvasSize.width > 0f && canvasSize.height > 0f)
        maxOf(canvasSize.width / bmp.width, canvasSize.height / bmp.height) else 1f
    fun sNow() = fit() * zoom
    fun txNow() = (canvasSize.width - bmp.width * sNow()) / 2f + pan.x
    fun tyNow() = (canvasSize.height - bmp.height * sNow()) / 2f + pan.y
    fun toScreen(p: Vec2) = Offset(p.x.toFloat() * sNow() + txNow(), p.y.toFloat() * sNow() + tyNow())
    fun handlePos(i: Int) = if (i in 0..3) corners[i] else stick[i - 4]

    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
            // Keyed by viewResets too: a Reset makes new zoom and pan states, and a gesture block that
            // was started before it would keep writing to the old ones.
            .pointerInput(canvasSize, viewResets) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // One finger always grabs the nearest handle; two fingers zoom + pan.
                    val handle = nearestHandle(down.position, corners, stick, sNow(), txNow(), tyNow())
                    var moved = false
                    do {
                        val e = awaitPointerEvent()
                        if (e.changes.count { it.pressed } >= 2) {
                            active = -1
                            val zc = e.calculateZoom(); val pc = e.calculatePan()
                            if (zc != 1f || pc != Offset.Zero) {
                                val sc = sNow()
                                zoom = (zoom * zc).coerceIn(0.55f, 6f)
                                pan = Offset(
                                    (pan.x + pc.x).coerceIn(-bmp.width * sc / 2f, bmp.width * sc / 2f),
                                    (pan.y + pc.y).coerceIn(-bmp.height * sc / 2f, bmp.height * sc / 2f),
                                )
                            }
                            e.changes.forEach { it.consume() }
                        } else {
                            active = handle
                            val d = e.changes.firstOrNull { it.id == down.id }?.positionChange() ?: Offset.Zero
                            if (d != Offset.Zero) {
                                val sc = sNow()
                                val delta = Vec2((d.x / sc).toDouble(), (d.y / sc).toDouble())
                                if (handle in 0..3) corners[handle] = corners[handle] + delta
                                else stick[handle - 4] = stick[handle - 4] + delta
                                moved = true
                                e.changes.forEach { it.consume() }
                            }
                        }
                    } while (e.changes.any { it.pressed })
                    active = -1
                    // A finished edit invalidates any result for the previous marks.
                    if (moved) reportEdited()
                }
            },
    ) {
        drawImage(
            img,
            dstOffset = IntOffset(txNow().toInt(), tyNow().toInt()),
            dstSize = IntSize((bmp.width * sNow()).toInt(), (bmp.height * sNow()).toInt()),
        )
        drawQuad(corners.map { toScreen(it) }, objectQuadColor, active, 0, objectLabel)
        drawQuad(stick.map { toScreen(it) }, stickQuadColor, active, 4, stickLabel)
        StickBox.ends(stick.toList()).forEach { drawCircle(stickEndDotColor, 10f, toScreen(it)) }
        if (active >= 0) drawMagnifier(img, handlePos(active), toScreen(handlePos(active)), sNow())
    }
}
