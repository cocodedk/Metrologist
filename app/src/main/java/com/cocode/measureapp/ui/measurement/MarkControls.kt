package com.cocode.measureapp.ui.measurement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.ui.theme.StaffRed

/**
 * Status line above the photo: a failed attempt's correction text (kept until the user edits
 * an input, which invalidates it) takes precedence over the marking hint.
 */
@Composable
fun MarkStatus(note: String, failureMessage: String?, modifier: Modifier = Modifier) {
    if (failureMessage != null) {
        Text(failureMessage, modifier, style = MaterialTheme.typography.bodyMedium, color = StaffRed)
    } else {
        Text(note, modifier)
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
    modifier: Modifier = Modifier,
) {
    val pad = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    Row(modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = onReset, Modifier.weight(1f), contentPadding = pad) { Text("Reset") }
        OutlinedButton(onClick = onRetake, Modifier.weight(1f), contentPadding = pad) { Text("Retake") }
        OutlinedButton(onClick = onSettings, Modifier.weight(1f), contentPadding = pad) { Text("Settings") }
        Button(onClick = onMeasure, Modifier.weight(1f), contentPadding = pad) { Text("Measure") }
    }
}
