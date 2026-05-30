package com.zva.agent.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── Warm Sci-Fi Anime Palette ────────────────────────────────────────────────

// Zva: soft purple → warm violet
val ZvaPrimary = Color(0xFF9C7CFF)
val ZvaOnPrimary = Color(0xFFFFFFFF)
val ZvaPrimaryContainer = Color(0xFFE8DEFF)
val ZvaOnPrimaryContainer = Color(0xFF2D004F)

// Dia: warm green → mint
val DiaPrimary = Color(0xFF5ECA8A)
val DiaOnPrimary = Color(0xFFFFFFFF)
val DiaPrimaryContainer = Color(0xFFD4FFE2)
val DiaOnPrimaryContainer = Color(0xFF002110)

// Accent: warm pink
val AccentPink = Color(0xFFFF6D9F)
val AccentPinkContainer = Color(0xFFFFD9E6)

// Warm surfaces
val SurfaceWarmDark = Color(0xFF1A1625)
val SurfaceWarmLight = Color(0xFFFFFBFE)

// ── Dark Theme ───────────────────────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary = ZvaPrimary,
    onPrimary = ZvaOnPrimary,
    primaryContainer = Color(0xFF3D2066),
    onPrimaryContainer = ZvaPrimaryContainer,
    secondary = DiaPrimary,
    onSecondary = DiaOnPrimary,
    secondaryContainer = Color(0xFF1A3A28),
    onSecondaryContainer = DiaPrimaryContainer,
    tertiary = AccentPink,
    surface = SurfaceWarmDark,
    surfaceVariant = Color(0xFF2A2438),
    onSurfaceVariant = Color(0xFFCAC4D0),
    background = Color(0xFF12101A),
    onBackground = Color(0xFFE6E0E9),
    outline = Color(0xFF938F99),
)

// ── Light Theme ──────────────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = ZvaOnPrimary,
    primaryContainer = ZvaPrimaryContainer,
    onPrimaryContainer = ZvaOnPrimaryContainer,
    secondary = Color(0xFF386A1F),
    onSecondary = DiaOnPrimary,
    secondaryContainer = DiaPrimaryContainer,
    onSecondaryContainer = DiaOnPrimaryContainer,
    tertiary = Color(0xFFBA1A1A),
    surface = SurfaceWarmLight,
    surfaceVariant = Color(0xFFF3EFF4),
    onSurfaceVariant = Color(0xFF49454F),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    outline = Color(0xFF79747E),
)

// ── Theme Composable ─────────────────────────────────────────────────────────

@Composable
fun ZvaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // default off for consistent anime aesthetic
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
