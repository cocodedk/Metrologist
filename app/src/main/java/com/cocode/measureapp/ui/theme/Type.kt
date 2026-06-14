package com.cocode.measureapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    headlineMedium = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.Bold,
        fontSize     = 28.sp,
        lineHeight   = 34.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.Medium,
        fontSize     = 20.sp,
        lineHeight   = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.Medium,
        fontSize     = 16.sp,
        lineHeight   = 22.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily   = FontFamily.Monospace,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.25.sp,
    ),
)
