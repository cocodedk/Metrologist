package com.cocode.measureapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocode.measureapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            SectionHeading(R.string.help_stick_title)
            BodyText(R.string.help_stick_intro)
            BulletText(R.string.help_stick_flat)
            BulletText(R.string.help_stick_settings)
            BulletText(R.string.help_stick_same_surface)
            BulletText(R.string.help_stick_border)

            Spacer(Modifier.height(8.dp))
            SectionHeading(R.string.help_photo_title)
            BodyText(R.string.help_photo_intro)
            BulletText(R.string.help_photo_angle)
            BulletText(R.string.help_photo_inview)
            BulletText(R.string.help_photo_light)

            Spacer(Modifier.height(8.dp))
            SectionHeading(R.string.help_mark_title)
            BodyText(R.string.help_mark_intro)
            BulletText(R.string.help_mark_object)
            BulletText(R.string.help_mark_stick)
            BulletText(R.string.help_mark_drag)
            BulletText(R.string.help_mark_zoom)
            BulletText(R.string.help_mark_surface)
            BulletText(R.string.help_mark_buttons)

            Spacer(Modifier.height(8.dp))
            SectionHeading(R.string.help_settings_title)
            BodyText(R.string.help_settings_intro)
            BulletText(R.string.help_settings_length)
            BulletText(R.string.help_settings_width)
            BulletText(R.string.help_settings_units)
            BodyText(R.string.help_settings_saved)

            Spacer(Modifier.height(8.dp))
            SectionHeading(R.string.help_result_title)
            BodyText(R.string.help_result_intro)
            BulletText(R.string.help_result_sizes)
            BulletText(R.string.help_result_angles)
            BulletText(R.string.help_result_confidence)
            BulletText(R.string.help_result_method)
            BulletText(R.string.help_result_notes)
            BulletText(R.string.help_result_buttons)

            Spacer(Modifier.height(8.dp))
            SectionHeading(R.string.help_tips_title)
            BulletText(R.string.help_tips_accuracy)
            BulletText(R.string.help_tips_flat)
            BulletText(R.string.help_tips_shadows)
            BulletText(R.string.help_tips_low)
            BulletText(R.string.help_tips_stick_size)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeading(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun BodyText(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun BulletText(@StringRes text: Int) {
    Text(stringResource(R.string.list_item, stringResource(text)), style = MaterialTheme.typography.bodyMedium)
}
