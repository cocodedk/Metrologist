package com.cocode.measureapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.core.MeasurementView
import com.cocode.measureapp.ui.theme.Amber
import com.cocode.measureapp.ui.theme.GreenRead
import com.cocode.measureapp.ui.theme.StaffRed
import com.cocode.measureapp.ui.theme.TextSecondary

@Composable
fun ResultsScreen(
    view: MeasurementView,
    onExport: () -> Unit,
    onRemark: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Measurements", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        if (!view.usable) {
            Text(
                "Could not measure confidently — check the markers and try a moderate angle.",
                style = MaterialTheme.typography.bodyMedium,
                color = StaffRed,
            )
        }

        LabeledValue("Width",    view.width)
        LabeledValue("Height",   view.height)
        LabeledValue("Area",     view.area)
        LabeledValue("Diagonal", view.diagonal)

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        // Confidence coloured by band
        val confColor = when {
            view.confidencePercent >= 70 -> GreenRead
            view.confidencePercent >= 40 -> Amber
            else                          -> StaffRed
        }
        Text(
            "Confidence: ${view.confidenceLabel} (${view.confidencePercent}%)",
            style = MaterialTheme.typography.bodyMedium,
            color = confColor,
        )

        // Method row — shown only when the fallback solver was used
        if (!view.solverName.contains("Rectangle")) {
            Text(
                "Method: ${view.solverName}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }

        // Corner-angle hint when angles are available
        if (view.cornerAngles.isNotEmpty()) {
            val angleText = view.cornerAngles.joinToString(", ") { "$it°" }
            Text(
                "Corner angles: $angleText  (90° = square corner)",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }

        view.caveats.forEach { caveat ->
            Text("• $caveat", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRemark) { Text("Re-mark") }
            OutlinedButton(onClick = onExport, enabled = view.usable) { Text("Export") }
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = StaffRed),
            ) { Text("New") }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
        )
        Text(value, style = MaterialTheme.typography.headlineMedium, color = Amber)
    }
}
