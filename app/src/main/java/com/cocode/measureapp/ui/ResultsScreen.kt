package com.cocode.measureapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.core.MeasurementView

@Composable
fun ResultsScreen(view: MeasurementView, onExport: () -> Unit, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Measurements", style = MaterialTheme.typography.headlineSmall)
        if (!view.usable) {
            Text("Could not measure confidently — check the markers and try a moderate angle.")
        }
        LabeledValue("Width", view.width)
        LabeledValue("Height", view.height)
        LabeledValue("Area", view.area)
        LabeledValue("Diagonal", view.diagonal)
        HorizontalDivider()
        Text("Confidence: ${view.confidenceLabel} (${view.confidencePercent}%)")
        Text("Method: ${view.solverName}")
        if (view.cornerAngles.isNotEmpty()) {
            Text("Corner angles: " + view.cornerAngles.joinToString(", ") { "$it" + "°" })
        }
        view.caveats.forEach { Text("• $it") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExport, enabled = view.usable) { Text("Export") }
            Button(onClick = onDone) { Text("New measurement") }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
