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
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.R
import com.cocode.measureapp.model.UiText
import com.cocode.measureapp.ui.text.asString
import com.cocode.measureapp.ui.theme.StaffRed

/** Bottom controls: optional retry message, Settings, Help and the Capture button. */
@Composable
internal fun CameraControls(
    captureEnabled: Boolean,
    capturing: Boolean,
    message: UiText?,
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
                message.asString(),
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
            // Icons without labels: the words pushed this row past the screen at a larger system
            // font, which clipped the capture button's own label. The gear and the (i) carry the
            // meaning on their own, and the label survives as the accessibility description.
            OutlinedIconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings), Modifier.size(22.dp))
            }
            OutlinedIconButton(onClick = onHelp) {
                Icon(Icons.Default.Info, contentDescription = stringResource(R.string.action_help), Modifier.size(22.dp))
            }
            Button(
                enabled = captureEnabled,
                onClick = onCapture,
                colors = ButtonDefaults.buttonColors(containerColor = StaffRed),
                modifier = Modifier.height(48.dp),
            ) {
                Text(stringResource(if (capturing) R.string.camera_capturing else R.string.camera_capture), maxLines = 1)
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
        Text(stringResource(R.string.camera_permission_needed))
        Button(onClick = onGrant) { Text(stringResource(R.string.camera_permission_grant)) }
        OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.camera_permission_open_settings)) }
    }
}
