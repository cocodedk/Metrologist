package com.cocode.measureapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.core.LengthUnit
import com.cocode.measureapp.core.MeasurementPresenter
import com.cocode.measureapp.core.MeasurementView
import com.cocode.measureapp.geometry.StickProfile
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.Vec2

/**
 * Lets the user place the 4 wall corners and the 2 stick ends on the captured photo, then
 * runs the metrology engine. Tap coordinates are mapped from the (letterboxed) on-screen
 * image back into image-pixel space, which is the space the engine works in.
 */
@Composable
fun MarkScreen(
    image: CapturedImage,
    stickLengthMeters: Double,
    unit: LengthUnit,
    onMeasured: (MeasurementView) -> Unit,
    onBack: () -> Unit,
) {
    val bmp = image.bitmap
    val corners = remember { mutableStateListOf<Vec2>() }
    val stick = remember { mutableStateListOf<Vec2>() }
    var markingStick by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // Derived layout values — computed from remembered canvasSize, not inside DrawScope
    val scale = if (canvasSize.width > 0f && canvasSize.height > 0f) {
        minOf(canvasSize.width / bmp.width, canvasSize.height / bmp.height)
    } else 1f
    val offsetX = if (canvasSize.width > 0f) (canvasSize.width - bmp.width * scale) / 2f else 0f
    val offsetY = if (canvasSize.height > 0f) (canvasSize.height - bmp.height * scale) / 2f else 0f

    Column(Modifier.fillMaxSize()) {
        Text(
            if (!markingStick) "Tap the 4 corners (${corners.size}/4)"
            else "Tap the 2 stick ends (${stick.size}/2)",
            Modifier.padding(12.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        canvasSize = Size(size.width.toFloat(), size.height.toFloat())
                    }
                    .pointerInput(markingStick, canvasSize) {
                        detectTapGestures { tap ->
                            val ix = (tap.x - offsetX) / scale
                            val iy = (tap.y - offsetY) / scale
                            if (ix < 0f || iy < 0f || ix >= bmp.width || iy >= bmp.height) return@detectTapGestures
                            val p = Vec2(ix.toDouble(), iy.toDouble())
                            if (!markingStick) {
                                if (corners.size < 4) corners.add(p)
                            } else if (stick.size < 2) {
                                stick.add(p)
                            }
                        }
                    },
            ) {
                drawImage(
                    bmp.asImageBitmap(),
                    dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                    dstSize = IntSize((bmp.width * scale).toInt(), (bmp.height * scale).toInt()),
                )
                corners.forEach {
                    drawCircle(Color.Cyan, 14f, Offset((it.x.toFloat() * scale) + offsetX, (it.y.toFloat() * scale) + offsetY))
                }
                stick.forEach {
                    drawCircle(Color.Red, 14f, Offset((it.x.toFloat() * scale) + offsetX, (it.y.toFloat() * scale) + offsetY))
                }
            }
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { corners.clear(); stick.clear(); markingStick = false }) { Text("Reset") }
            Button(onClick = onBack) { Text("Retake") }
            if (!markingStick) {
                Button(enabled = corners.size == 4, onClick = { markingStick = true }) { Text("Next: stick") }
            } else {
                Button(enabled = stick.size == 2, onClick = {
                    onMeasured(
                        MeasurementPresenter.present(
                            corners = corners.toList(),
                            stick = stick.toList(),
                            intrinsics = image.scene.intrinsics,
                            gravity = image.scene.gravity,
                            profile = StickProfile(stickLengthMeters),
                            orientation = SurfaceOrientation.VERTICAL,
                            unit = unit,
                        ),
                    )
                }) { Text("Measure") }
            }
        }
    }
}
