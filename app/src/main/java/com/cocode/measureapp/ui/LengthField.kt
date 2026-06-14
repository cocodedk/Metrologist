package com.cocode.measureapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.core.LengthInput
import com.cocode.measureapp.core.LengthUnit

/**
 * A unit-aware, validated single length input. Shows [valueMeters] in [unit], parses edits back
 * to meters via [LengthInput], and surfaces an inline error for unparseable / non-positive text
 * without crashing or silently storing 0. Valid edits are reported through [onValidMeters].
 */
@Composable
fun LengthField(
    label: String,
    valueMeters: Double,
    unit: LengthUnit,
    onValidMeters: (Double) -> Unit,
) {
    var text by remember(valueMeters, unit) {
        mutableStateOf(LengthInput.format(valueMeters, unit))
    }
    val parsed = LengthInput.parseToMeters(text, unit)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label (${LengthInput.unitLabel(unit)})", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                text = new
                LengthInput.parseToMeters(new, unit)?.let(onValidMeters)
            },
            isError = parsed == null,
            supportingText = {
                if (parsed == null) Text("Enter a positive number")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier,
        )
    }
}
