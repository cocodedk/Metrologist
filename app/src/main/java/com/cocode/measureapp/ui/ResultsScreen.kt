package com.cocode.measureapp.ui

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.R
import com.cocode.measureapp.core.MeasurementView
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.model.TextKey
import com.cocode.measureapp.model.UiText
import com.cocode.measureapp.ui.text.asString
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
    /** Export eligibility of the current revision; never inferred from a stale result. */
    exportEnabled: Boolean = view.usable,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.results_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        if (!view.usable) {
            Text(
                (view.message ?: UiText(TextKey.MEASURE_FAILED)).asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = StaffRed,
            )
        }

        LabeledValue(R.string.results_width,    view.width)
        LabeledValue(R.string.results_height,   view.height)
        LabeledValue(R.string.results_area,     view.area)
        LabeledValue(R.string.results_diagonal, view.diagonal)

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        // Confidence coloured by band
        val confColor = when {
            view.confidencePercent >= 70 -> GreenRead
            view.confidencePercent >= 40 -> Amber
            else                          -> StaffRed
        }
        Text(
            stringResource(R.string.results_confidence, view.confidenceLabel.asString(), view.confidencePercent),
            style = MaterialTheme.typography.bodyMedium,
            color = confColor,
        )

        // Method row — shown only when the fallback solver was used
        if (view.solver == SolverKind.GRAVITY) {
            Text(
                stringResource(R.string.results_method, stringResource(R.string.method_tilt_sensor)),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }

        // Corner-angle hint when angles are available
        if (view.cornerAngles.isNotEmpty()) {
            val angleText = view.cornerAngles.joinToString(", ") { "$it°" }
            Text(
                stringResource(R.string.results_corner_angles, angleText),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }

        view.caveats.forEach { caveat ->
            Text(stringResource(R.string.list_item, caveat.asString()), style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRemark) { Text(stringResource(R.string.results_remark)) }
            OutlinedButton(onClick = onExport, enabled = exportEnabled && view.usable) { Text(stringResource(R.string.results_export)) }
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = StaffRed),
            ) { Text(stringResource(R.string.results_new)) }
        }
    }
}

@Composable
private fun LabeledValue(@StringRes label: Int, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
        )
        Text(value, style = MaterialTheme.typography.headlineMedium, color = Amber)
    }
}
