package com.cocode.measureapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.core.LengthUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    stickLengthMeters: Double,
    stickWidthMeters: Double,
    unit: LengthUnit,
    onLength: (Double) -> Unit,
    onWidth: (Double) -> Unit,
    onUnit: (LengthUnit) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LengthField("Reference stick length", stickLengthMeters, unit, onLength)
            LengthField("Reference stick width", stickWidthMeters, unit, onWidth)
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
