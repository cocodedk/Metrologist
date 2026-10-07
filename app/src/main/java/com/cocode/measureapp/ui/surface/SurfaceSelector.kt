package com.cocode.measureapp.ui.surface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.R
import com.cocode.measureapp.geometry.SurfaceOrientation

/** The string resource that labels each surface choice. */
@StringRes
fun surfaceLabel(orientation: SurfaceOrientation): Int = when (orientation) {
    SurfaceOrientation.VERTICAL -> R.string.surface_wall
    SurfaceOrientation.HORIZONTAL -> R.string.surface_floor
}

/**
 * Wall vs floor/table choice for the marked surface. The current [selected] value is always
 * shown as a checked radio button, so the default wall assumption is never hidden.
 */
@Composable
fun SurfaceSelector(
    selected: SurfaceOrientation,
    onSelected: (SurfaceOrientation) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Compact on purpose: this sits under the photo, and every dp it takes is a dp the user
    // cannot mark in. Labels use the small label style; the radios keep their own touch target.
    val label = MaterialTheme.typography.labelLarge
    Row(
        modifier.selectableGroup().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.surface_label), style = label)
        SurfaceOrientation.entries.forEach { option ->
            Row(
                Modifier.selectable(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    role = Role.RadioButton,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null, modifier = Modifier.size(32.dp))
                Text(stringResource(surfaceLabel(option)), style = label, maxLines = 1)
            }
        }
    }
}
