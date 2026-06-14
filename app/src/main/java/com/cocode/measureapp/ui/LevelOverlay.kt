package com.cocode.measureapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.measureapp.geometry.TiltAngles
import com.cocode.measureapp.ui.theme.GreenRead
import com.cocode.measureapp.ui.theme.StaffRed
import kotlin.math.roundToInt

/**
 * Centered tilt level for the camera: a crosshair ring with the pitch/roll readout
 * beneath it. Green when the device is square to the surface (level), red otherwise.
 */
@Composable
fun LevelOverlay(tilt: TiltAngles, modifier: Modifier = Modifier) {
    val color = if (tilt.isLevel(1.0)) GreenRead else StaffRed
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(96.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f - 2f
            drawCircle(color, r, c, style = Stroke(3f))
            drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 2f)
            drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = 2f)
            drawCircle(color, 6f, c)
        }
        Text(
            "↕ ${tilt.pitchDeg.roundToInt()}°   ↔ ${tilt.rollDeg.roundToInt()}°",
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 18.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC000000))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
