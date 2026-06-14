package com.cocode.measureapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrandDarkScheme = darkColorScheme(
    primary          = StaffRed,
    onPrimary        = TextPrimary,
    primaryContainer = StaffRedDim,
    secondary        = Amber,
    onSecondary      = BgDeep,
    tertiary         = GreenRead,
    background       = BgDeep,
    surface          = BgSurface,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    surfaceVariant   = BgCard,
    outline          = Color(0xFF4A524A),
)

@Composable
fun MeasureAppTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = BrandDarkScheme,
        typography  = Typography,
        content     = content,
    )
}
