package com.cocode.measureapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cocode.measureapp.ui.MeasureApp
import com.cocode.measureapp.ui.theme.MeasureAppTheme
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { OpenCVLoader.initLocal() }
        enableEdgeToEdge()
        setContent {
            MeasureAppTheme {
                MeasureApp()
            }
        }
    }
}
