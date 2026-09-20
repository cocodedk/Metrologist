package com.cocode.measureapp.ui.measurement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.ui.theme.StaffRed

/**
 * Status line above the photo: a failed attempt's correction text (kept until the user edits
 * an input, which invalidates it) takes precedence over the marking hint.
 */
@Composable
fun MarkStatus(note: String, failureMessage: String?, modifier: Modifier = Modifier) {
    // Drawn over the photo, so it carries its own scrim and stays small: the marking area is
    // what the user works in, and a correction is read once and then acted on.
    val box = modifier
        .padding(8.dp)
        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
        .padding(horizontal = 8.dp, vertical = 4.dp)
    if (failureMessage != null) {
        Text(failureMessage, box, style = MaterialTheme.typography.bodySmall, color = StaffRed)
    } else {
        Text(note, box, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Marking actions. Retake and Settings stay available after any failure, so an image that
 * cannot be measured, or an invalid reference setting, never needs an app restart.
 */
@Composable
fun MarkControls(
    onReset: () -> Unit,
    onRetake: () -> Unit,
    onSettings: () -> Unit,
    onMeasure: () -> Unit,
    onToggleHint: () -> Unit,
    hintShown: Boolean,
    modifier: Modifier = Modifier,
) {
    val pad = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    // FlowRow, not Row with weights: four equal quarters fit "Settings" only at font scale 1.0.
    // A phone set to larger text (1.1 already does it) broke the word across two lines. Here each
    // button keeps its natural width and the row wraps to a second line when they no longer fit.
    FlowRow(
        modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val label = MaterialTheme.typography.labelLarge
        val short = Modifier.height(40.dp)
        OutlinedButton(onClick = onReset, short, contentPadding = pad) { Text("Reset", maxLines = 1, style = label) }
        OutlinedButton(onClick = onRetake, short, contentPadding = pad) { Text("Retake", maxLines = 1, style = label) }
        OutlinedIconButton(onClick = onSettings, Modifier.size(40.dp)) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", Modifier.size(20.dp))
        }
        // The marking hint is worth reading once, not forever: it hides behind this button so the
        // photo keeps the space. A correction after a failed measurement shows itself regardless.
        OutlinedIconButton(onClick = onToggleHint, Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Info,
                contentDescription = if (hintShown) "Hide marking help" else "Marking help",
                Modifier.size(20.dp),
            )
        }
        Button(onClick = onMeasure, short, contentPadding = pad) { Text("Measure", maxLines = 1, style = label) }
    }
}
