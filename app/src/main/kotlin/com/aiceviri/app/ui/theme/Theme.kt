package com.aiceviri.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF0F172A)
val Sky = Color(0xFF0EA5E9)
val Orange = Color(0xFFF97316)
val Slate100 = Color(0xFFF1F5F9)

private val Light = lightColorScheme(
    primary = Sky,
    onPrimary = Color.White,
    secondary = Orange,
    onSecondary = Color.White,
    background = Slate100,
    surface = Color.White,
    onSurface = Navy,
    onBackground = Navy,
)

private val Dark = darkColorScheme(
    primary = Sky,
    onPrimary = Color.White,
    secondary = Orange,
    onSecondary = Color.White,
    background = Navy,
    surface = Color(0xFF1E293B),
)

@Composable
fun AiCeviriTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
