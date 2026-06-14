package com.cocode.measureapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.core.CornerOrdering
import com.cocode.measureapp.core.LengthUnit
import com.cocode.measureapp.core.MeasurementPresenter
import com.cocode.measureapp.core.MeasurementView
import com.cocode.measureapp.detect.DeferredStickDetector
import com.cocode.measureapp.detect.StickDetector
import com.cocode.measureapp.geometry.StickProfile
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.stick.StickBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Marking screen: two adjustable 4-corner boxes (GreenRead over the object, StaffRed around the
 * stick) on the captured photo. One-finger drag near a handle moves the handle; one-finger drag
 * far from any handle pans. Two-finger pinch zooms + pans. A magnifier loupe aids precise placement.
 */
@Composable
fun MarkScreen(
    image: CapturedImage,
    stickLengthMeters: Double,
    stickWidthMeters: Double,
    unit: LengthUnit,
    initialCorners: List<Vec2>? = null,
    initialStick: List<Vec2>? = null,
    onMarkChanged: (List<Vec2>, List<Vec2>) -> Unit = { _, _ -> },
    detector: StickDetector = DeferredStickDetector,
    onMeasured: (MeasurementView) -> Unit,
    onBack: () -> Unit,
) {
    val bmp = image.bitmap
    val img = remember(bmp) { bmp.asImageBitmap() }
    val w = bmp.width.toDouble(); val h = bmp.height.toDouble()
    fun defCorners() = listOf(
        Vec2(w * 0.20, h * 0.20), Vec2(w * 0.80, h * 0.20),
        Vec2(w * 0.80, h * 0.80), Vec2(w * 0.20, h * 0.80),
    )
    fun defStick() = listOf(
        Vec2(w * 0.35, h * 0.64), Vec2(w * 0.65, h * 0.64),
        Vec2(w * 0.65, h * 0.72), Vec2(w * 0.35, h * 0.72),
    )
    val cornerDragHint = "Drag a corner to adjust · two fingers to zoom & pan"

    val corners = remember { mutableStateListOf<Vec2>().apply { addAll(initialCorners ?: defCorners()) } }
    val stick   = remember { mutableStateListOf<Vec2>().apply { addAll(initialStick ?: defStick()) } }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var active by remember { mutableStateOf(-1) }
    var resetGen by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf(if (initialStick == null) "Detecting stick…" else cornerDragHint) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(image) {
        if (initialStick != null) return@LaunchedEffect   // re-marking: keep the user's stick box
        val gen = resetGen
        val r = withContext(Dispatchers.Default) { detector.detect(bmp) }
        val a = r?.points?.firstOrNull(); val b = r?.points?.lastOrNull()
        if (a != null && b != null && a.distanceTo(b) > 1.0 && resetGen == gen) {
            val dir = (b - a).normalized()
            val perp = Vec2(-dir.y, dir.x)
            val hw = maxOf(8.0, a.distanceTo(b) * 0.06)
            stick[0] = a + perp * hw; stick[1] = b + perp * hw
            stick[2] = b - perp * hw; stick[3] = a - perp * hw
            note = "Stick auto-detected (${(r.confidence * 100).toInt()}%) — drag to fine-tune"
        } else if (resetGen == gen) {
            note = cornerDragHint
        }
    }

    fun fit() = if (canvasSize.width > 0f && canvasSize.height > 0f)
        minOf(canvasSize.width / bmp.width, canvasSize.height / bmp.height) else 1f
    fun sNow() = fit() * zoom
    fun txNow() = (canvasSize.width - bmp.width * sNow()) / 2f + pan.x
    fun tyNow() = (canvasSize.height - bmp.height * sNow()) / 2f + pan.y
    fun toScreen(p: Vec2) = Offset(p.x.toFloat() * sNow() + txNow(), p.y.toFloat() * sNow() + tyNow())
    fun handlePos(i: Int) = if (i in 0..3) corners[i] else stick[i - 4]

    Column(Modifier.fillMaxSize()) {
        Text(note, Modifier.padding(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(canvasSize) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // One finger always grabs the nearest handle; two fingers zoom + pan.
                            val handle = nearestHandle(down.position, corners, stick, sNow(), txNow(), tyNow())
                            do {
                                val e = awaitPointerEvent()
                                if (e.changes.count { it.pressed } >= 2) {
                                    active = -1
                                    val zc = e.calculateZoom(); val pc = e.calculatePan()
                                    if (zc != 1f || pc != Offset.Zero) {
                                        val sc = sNow()
                                        zoom = (zoom * zc).coerceIn(1f, 6f)
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
                                        e.changes.forEach { it.consume() }
                                    }
                                }
                            } while (e.changes.any { it.pressed })
                            active = -1
                        }
                    },
            ) {
                drawImage(
                    img,
                    dstOffset = IntOffset(txNow().toInt(), tyNow().toInt()),
                    dstSize = IntSize((bmp.width * sNow()).toInt(), (bmp.height * sNow()).toInt()),
                )
                drawQuad(corners.map { toScreen(it) }, objectQuadColor, active, 0, "Object")
                drawQuad(stick.map { toScreen(it) }, stickQuadColor, active, 4, "Stick")
                StickBox.ends(stick.toList()).forEach { drawCircle(stickEndDotColor, 10f, toScreen(it)) }
                if (active >= 0) drawMagnifier(img, handlePos(active), toScreen(handlePos(active)), sNow())
            }
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                resetGen++
                corners.clear(); corners.addAll(defCorners())
                stick.clear(); stick.addAll(defStick())
                zoom = 1f; pan = Offset.Zero; note = cornerDragHint
            }) { Text("Reset") }
            Button(onClick = onBack) { Text("Retake") }
            Button(onClick = {
                val ordered = runCatching { CornerOrdering.order(corners.toList()) }.getOrNull()
                if (ordered == null) {
                    note = "Spread the 4 object corners apart — they overlap"
                } else {
                    onMarkChanged(corners.toList(), stick.toList())   // keep placements for re-mark
                    onMeasured(
                        MeasurementPresenter.present(
                            corners = ordered, stick = stick.toList(),
                            intrinsics = image.scene.intrinsics, gravity = image.scene.gravity,
                            profile = StickProfile(stickLengthMeters, width = stickWidthMeters),
                            orientation = SurfaceOrientation.VERTICAL, unit = unit,
                        ),
                    )
                }
            }) { Text("Measure") }
        }
    }
}
