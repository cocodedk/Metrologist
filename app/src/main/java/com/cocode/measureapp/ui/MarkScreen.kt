package com.cocode.measureapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.hypot

private const val GRAB_RADIUS = 70f

/**
 * Marking screen: two adjustable 4-corner boxes — a cyan quad over the object and a red box around
 * the stick. Drag any of the 8 handles onto its target; the stick's two ends (yellow) are derived
 * from its box. The object corners may be dragged in any arrangement — CornerOrdering canonicalises
 * them before measuring. Handles live in image-pixel space, the space the engine uses.
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
    val hint = "Drag the cyan box onto the object and the red box around the stick"

    val corners = remember { mutableStateListOf<Vec2>().apply { addAll(defCorners()) } }
    val stick = remember { mutableStateListOf<Vec2>().apply { addAll(defStick()) } }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var active by remember { mutableStateOf(-1) }
    var resetGen by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf(hint) }

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

    val scale = if (canvasSize.width > 0f && canvasSize.height > 0f)
        minOf(canvasSize.width / bmp.width, canvasSize.height / bmp.height) else 1f
    val offX = if (canvasSize.width > 0f) (canvasSize.width - bmp.width * scale) / 2f else 0f
    val offY = if (canvasSize.height > 0f) (canvasSize.height - bmp.height * scale) / 2f else 0f
    fun toScreen(p: Vec2) = Offset(p.x.toFloat() * scale + offX, p.y.toFloat() * scale + offY)

    Column(Modifier.fillMaxSize()) {
        Text(note, Modifier.padding(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(canvasSize) {
                        detectDragGestures(
                            onDragStart = { active = nearestHandle(it, corners, stick, scale, offX, offY) },
                            onDragEnd = { active = -1 },
                            onDragCancel = { active = -1 },
                            onDrag = { change, d ->
                                change.consume()
                                val delta = Vec2((d.x / scale).toDouble(), (d.y / scale).toDouble())
                                val i = active
                                if (i in 0..3) corners[i] = corners[i] + delta
                                else if (i in 4..7) stick[i - 4] = stick[i - 4] + delta
                            },
                        )
                    },
            ) {
                drawImage(
                    bmp.asImageBitmap(),
                    dstOffset = IntOffset(offX.toInt(), offY.toInt()),
                    dstSize = IntSize((bmp.width * scale).toInt(), (bmp.height * scale).toInt()),
                )
                drawQuad(corners.map { toScreen(it) }, Color.Cyan, active, 0)
                drawQuad(stick.map { toScreen(it) }, Color.Red, active, 4)
                StickBox.ends(stick.toList()).forEach { drawCircle(Color.Yellow, 10f, toScreen(it)) }
            }
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                resetGen++
                corners.clear(); corners.addAll(defCorners())
                stick.clear(); stick.addAll(defStick())
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

private fun DrawScope.drawQuad(pts: List<Offset>, color: Color, active: Int, base: Int) {
    for (i in pts.indices) drawLine(color, pts[i], pts[(i + 1) % pts.size], strokeWidth = 3f)
    pts.forEachIndexed { i, p ->
        drawCircle(if (active == base + i) Color.White else color, 20f, p)
        drawCircle(Color.Black, 20f, p, style = Stroke(2f))
    }
}

private fun nearestHandle(
    pos: Offset,
    corners: List<Vec2>,
    stick: List<Vec2>,
    scale: Float,
    offX: Float,
    offY: Float,
): Int {
    var best = -1
    var bestD = GRAB_RADIUS
    (corners + stick).forEachIndexed { i, p ->
        val d = hypot(pos.x - (p.x.toFloat() * scale + offX), pos.y - (p.y.toFloat() * scale + offY))
        if (d < bestD) { bestD = d; best = i }
    }
    return best
}
