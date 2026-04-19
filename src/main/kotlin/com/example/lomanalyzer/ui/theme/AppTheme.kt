package com.example.lomanalyzer.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColors(
    primary = Color(0xFF1565C0),
    primaryVariant = Color(0xFF0D47A1),
    secondary = Color(0xFF00897B),
    secondaryVariant = Color(0xFF00695C),
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    error = Color(0xFFD32F2F),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF212121),
    onSurface = Color(0xFF212121),
    onError = Color.White,
)

@Composable
@Suppress("FunctionNaming")
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = LightColors, content = content)
}
