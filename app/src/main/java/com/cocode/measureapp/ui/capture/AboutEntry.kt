package com.cocode.measureapp.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.R
import com.cocode.measureapp.ui.theme.TextPrimary

/**
 * The way to the About screen: a small labelled button in the top corner of the camera view.
 * It sits on its own scrim, so it stays readable over any scene, and it is a word rather than
 * a third icon in the bottom row, which already runs out of width at a large system font.
 */
@Composable
internal fun AboutEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp)),
    ) {
        Text(stringResource(R.string.action_about), color = TextPrimary, style = MaterialTheme.typography.labelLarge)
    }
}
