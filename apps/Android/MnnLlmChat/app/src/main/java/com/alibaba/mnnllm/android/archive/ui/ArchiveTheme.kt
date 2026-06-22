package com.alibaba.mnnllm.android.archive.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette — calm indigo/teal, friendly and modern.
private val BrandPrimary = Color(0xFF4F5BD5)
private val BrandPrimaryDark = Color(0xFF3A45B0)
private val BrandSecondary = Color(0xFF12B5A8)
private val BrandTertiary = Color(0xFFFF8A5B)
private val SurfaceLight = Color(0xFFF6F7FB)
private val SurfaceDark = Color(0xFF14151A)

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E4FB),
    onPrimaryContainer = Color(0xFF1A1F66),
    secondary = BrandSecondary,
    onSecondary = Color.White,
    tertiary = BrandTertiary,
    background = SurfaceLight,
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF0F7),
    onSurface = Color(0xFF1B1C20),
    onSurfaceVariant = Color(0xFF5A5D66),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AA3F5),
    onPrimary = Color(0xFF101440),
    primaryContainer = BrandPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF5BD5C9),
    tertiary = BrandTertiary,
    background = SurfaceDark,
    surface = Color(0xFF1D1F26),
    surfaceVariant = Color(0xFF2A2C35),
    onSurface = Color(0xFFE8E9EE),
    onSurfaceVariant = Color(0xFFB6B9C4),
)

@Composable
fun ArchiveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
