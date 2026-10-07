package com.cocode.measureapp.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.R
import com.cocode.measureapp.core.LengthUnit

@StringRes
private fun LengthUnit.displayName(): Int = when (this) {
    LengthUnit.METERS      -> R.string.unit_meters
    LengthUnit.CENTIMETERS -> R.string.unit_centimeters
    LengthUnit.FEET_INCHES -> R.string.unit_feet_inches
}

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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LengthField(R.string.settings_stick_length, stickLengthMeters, unit, onLength)
            LengthField(R.string.settings_stick_width,  stickWidthMeters,  unit, onWidth)
            Text(stringResource(R.string.settings_units), style = MaterialTheme.typography.titleMedium)
            LengthUnit.entries.forEach { u ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = u == unit, onClick = { onUnit(u) })
                    Text(stringResource(u.displayName()))
                }
            }
        }
    }
}
