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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How to measure") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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

            SectionHeading("The reference stick")
            BodyText(
                "The stick is a flat ruler with alternating red-white-red-white stripes. " +
                "It gives the app a known real-world scale — everything in the photo is " +
                "measured relative to it.",
            )
            BulletText("Lay it flat on the same surface as the object you want to measure.")
            BulletText("Enter its real length and width in Settings before you start.")
            BulletText("Keep the stick in the same plane as the object — never propped up.")

            Spacer(Modifier.height(8.dp))
            SectionHeading("Capture")
            BodyText(
                "Frame the object and stick together, then tap Capture.",
            )
            BulletText("Shoot at a moderate angle — roughly 30–60° from the surface.")
            BulletText("Do not shoot straight down (head-on) or from an extreme side angle.")
            BulletText("Good lighting and a steady hand improve accuracy.")

            Spacer(Modifier.height(8.dp))
            SectionHeading("Mark")
            BodyText(
                "Two 4-corner boxes appear over the photo:",
            )
            BulletText("Cyan box — drag the corners to the four corners of the object.")
            BulletText("Red box — drag the corners to the four corners of the stick.")
            BulletText(
                "Drag any corner to reposition it. A magnifier loupe appears while dragging " +
                "for sub-pixel precision.",
            )
            BulletText("Use two fingers to zoom in or pan the photo for tight corners.")
            BulletText("Tap Measure when both boxes are placed correctly.")

            Spacer(Modifier.height(8.dp))
            SectionHeading("Settings")
            BodyText("Before your first measurement, open Settings and enter:")
            BulletText("Stick length — the full length of the reference stick.")
            BulletText("Stick width — the short dimension of the stick.")
            BulletText("Units — metres, centimetres, or feet-and-inches.")
            BodyText("The app stores your values between sessions.")

            Spacer(Modifier.height(8.dp))
            SectionHeading("Read the result")
            BodyText("After marking you see:")
            BulletText("Width, Height, Area, Diagonal — all in your chosen unit.")
            BulletText("Corner angles — useful to check if the object is actually rectangular.")
            BulletText(
                "Confidence score — Good / Fair / Poor, plus a percentage. " +
                "Below ~60% the result may be unreliable.",
            )
            BulletText(
                "Caveats — plain-language notes about why the score is what it is " +
                "(e.g. \"extreme viewing angle\", \"stick nearly parallel to object edge\").",
            )

            Spacer(Modifier.height(8.dp))
            SectionHeading("Tips and accuracy")
            BulletText("Typical accuracy: ~1–5%. Larger objects and better angles give tighter results.")
            BulletText(
                "Everything measured must lie on the stick's plane — raised edges or " +
                "curved surfaces will be wrong.",
            )
            BulletText("Avoid shadows crossing the stick — the stripe detection relies on contrast.")
            BulletText(
                "If confidence is Poor, try a different angle or move the stick closer " +
                "to the object.",
            )
            BulletText("The stick itself is not included in the object dimensions.")

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun BodyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun BulletText(text: String) {
    Text("•  $text", style = MaterialTheme.typography.bodyMedium)
}
