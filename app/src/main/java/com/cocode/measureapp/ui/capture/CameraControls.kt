package com.cocode.measureapp.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.ui.theme.StaffRed

/** Bottom controls: optional retry message, Settings, Help and the Capture button. */
@Composable
internal fun CameraControls(
    captureEnabled: Boolean,
    capturing: Boolean,
    message: String?,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (message != null) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Settings")
            }
            OutlinedButton(onClick = onHelp) {
                Icon(Icons.Default.Info, contentDescription = "Help", Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Help")
            }
            Button(
                enabled = captureEnabled,
                onClick = onCapture,
                colors = ButtonDefaults.buttonColors(containerColor = StaffRed),
                modifier = Modifier.height(48.dp),
            ) {
                Text(if (capturing) "Capturing…" else "Capture")
            }
        }
    }
}

@Composable
internal fun PermissionDeniedPanel(
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera permission is needed to capture.")
        Button(onClick = onGrant) { Text("Grant permission") }
        OutlinedButton(onClick = onOpenSettings) { Text("Open settings") }
    }
}
