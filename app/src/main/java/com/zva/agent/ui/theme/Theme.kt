package com.zva.agent.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── Colors ───────────────────────────────────────────────────────────────────

val ZvaPrimary = Color(0xFF6750A4)
val ZvaOnPrimary = Color(0xFFFFFFFF)
val ZvaPrimaryContainer = Color(0xFFEADDFF)
val ZvaOnPrimaryContainer = Color(0xFF21005D)

val DiaPrimary = Color(0xFF386A1F)
val DiaOnPrimary = Color(0xFFFFFFFF)
val DiaPrimaryContainer = Color(0xFFB8F397)
val DiaOnPrimaryContainer = Color(0xFF042100)

val SurfaceDark = Color(0xFF1C1B1F)
val SurfaceLight = Color(0xFFFFFBFE)

// ── Theme ────────────────────────────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary = ZvaPrimary,
    onPrimary = ZvaOnPrimary,
    primaryContainer = ZvaPrimaryContainer,
    onPrimaryContainer = ZvaOnPrimaryContainer,
    surface = SurfaceDark,
)

private val LightColorScheme = lightColorScheme(
    primary = ZvaPrimary,
    onPrimary = ZvaOnPrimary,
    primaryContainer = ZvaPrimaryContainer,
    onPrimaryContainer = ZvaOnPrimaryContainer,
    surface = SurfaceLight,
)

@Composable
fun ZvaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
