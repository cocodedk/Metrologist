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
import androidx.compose.ui.graphics.Color
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
 * Marking screen: two adjustable 4-corner boxes (cyan over the object, red around the stick) on the
 * captured photo. Pinch to zoom and drag empty space to pan; drag a handle to move it, with a
 * magnifier loupe for precise placement. The stick's two ends (yellow) come from its box; the
 * object corners are canonicalised by CornerOrdering before measuring.
 */
@Composable
fun MarkScreen(
    image: CapturedImage,
    stickLengthMeters: Double,
    unit: LengthUnit,
    detector: StickDetector = DeferredStickDetector,
    onMeasured: (MeasurementView) -> Unit,
    onBack: () -> Unit,
) {
    val bmp = image.bitmap
    val img = remember(bmp) { bmp.asImageBitmap() }
    val w = bmp.width.toDouble()
    val h = bmp.height.toDouble()
    fun defCorners() = listOf(
        Vec2(w * 0.20, h * 0.20), Vec2(w * 0.80, h * 0.20),
        Vec2(w * 0.80, h * 0.80), Vec2(w * 0.20, h * 0.80),
    )
    fun defStick() = listOf(
        Vec2(w * 0.35, h * 0.64), Vec2(w * 0.65, h * 0.64),
        Vec2(w * 0.65, h * 0.72), Vec2(w * 0.35, h * 0.72),
    )
    val hint = "Drag any corner to adjust it · two fingers to zoom & pan"

    val corners = remember { mutableStateListOf<Vec2>().apply { addAll(defCorners()) } }
    val stick = remember { mutableStateListOf<Vec2>().apply { addAll(defStick()) } }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var active by remember { mutableStateOf(-1) }
    var resetGen by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf(hint) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(image) {
        val gen = resetGen
        val r = withContext(Dispatchers.Default) { detector.detect(bmp) }
        val a = r?.points?.firstOrNull()
        val b = r?.points?.lastOrNull()
        if (a != null && b != null && a.distanceTo(b) > 1.0 && resetGen == gen) {
            val dir = (b - a).normalized()
            val perp = Vec2(-dir.y, dir.x)
            val hw = maxOf(8.0, a.distanceTo(b) * 0.06)
            stick[0] = a + perp * hw; stick[1] = b + perp * hw
            stick[2] = b - perp * hw; stick[3] = a - perp * hw
            note = "Stick auto-detected (${(r.confidence * 100).toInt()}%) — drag to fine-tune"
        }
    }

    fun fit() = if (canvasSize.width > 0f && canvasSize.height > 0f)
        minOf(canvasSize.width / bmp.width, canvasSize.height / bmp.height) else 1f
    fun sNow() = fit() * zoom
    fun txNow() = (canvasSize.width - bmp.width * sNow()) / 2f + pan.x
    fun tyNow() = (canvasSize.height - bmp.height * sNow()) / 2f + pan.y
    val s = sNow()
    val tx = txNow()
    val ty = tyNow()
    fun toScreen(p: Vec2) = Offset(p.x.toFloat() * s + tx, p.y.toFloat() * s + ty)
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
                            val handle = nearestHandle(down.position, corners, stick, sNow(), txNow(), tyNow())
                            do {
                                val e = awaitPointerEvent()
                                if (e.changes.count { it.pressed } >= 2) {
                                    // two fingers → zoom + pan
                                    active = -1
                                    val zc = e.calculateZoom()
                                    val pc = e.calculatePan()
                                    if (zc != 1f || pc != Offset.Zero) {
                                        val sc = sNow()
                                        zoom = (zoom * zc).coerceIn(1f, 6f)
                                        val limX = bmp.width * sc / 2f
                                        val limY = bmp.height * sc / 2f
                                        pan = Offset((pan.x + pc.x).coerceIn(-limX, limX), (pan.y + pc.y).coerceIn(-limY, limY))
                                    }
                                    e.changes.forEach { it.consume() }
                                } else {
                                    // one finger → always move the nearest handle
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
                    dstOffset = IntOffset(tx.toInt(), ty.toInt()),
                    dstSize = IntSize((bmp.width * s).toInt(), (bmp.height * s).toInt()),
                )
                drawQuad(corners.map { toScreen(it) }, Color.Cyan, active, 0)
                drawQuad(stick.map { toScreen(it) }, Color.Red, active, 4)
                StickBox.ends(stick.toList()).forEach { drawCircle(Color.Yellow, 10f, toScreen(it)) }
                if (active >= 0) drawMagnifier(img, handlePos(active), toScreen(handlePos(active)), s)
            }
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                resetGen++
                corners.clear(); corners.addAll(defCorners())
                stick.clear(); stick.addAll(defStick())
                zoom = 1f; pan = Offset.Zero
                note = hint
            }) { Text("Reset") }
            Button(onClick = onBack) { Text("Retake") }
            Button(onClick = {
                val ordered = runCatching { CornerOrdering.order(corners.toList()) }.getOrNull()
                if (ordered == null) {
                    note = "Spread the 4 object corners apart — they overlap"
                } else {
                    onMeasured(
                        MeasurementPresenter.present(
                            corners = ordered,
                            stick = StickBox.ends(stick.toList()),
                            intrinsics = image.scene.intrinsics,
                            gravity = image.scene.gravity,
                            profile = StickProfile(stickLengthMeters),
                            orientation = SurfaceOrientation.VERTICAL,
                            unit = unit,
                        ),
                    )
                }
            }) { Text("Measure") }
        }
    }
}
