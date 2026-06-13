package com.cocode.measureapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.core.LengthUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    stickLengthMeters: Double,
    unit: LengthUnit,
    onLength: (Double) -> Unit,
    onUnit: (LengthUnit) -> Unit,
    onBack: () -> Unit,
) {
    var lengthText by remember(stickLengthMeters) { mutableStateOf(stickLengthMeters.toString()) }
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Reference stick length (meters)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = lengthText,
                onValueChange = { text -> lengthText = text; text.toDoubleOrNull()?.let(onLength) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
            Text("Display units", style = MaterialTheme.typography.titleMedium)
            LengthUnit.entries.forEach { u ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = u == unit, onClick = { onUnit(u) })
                    Text(u.name)
                }
            }
            Button(onClick = onBack) { Text("Done") }
        }
    }
}
