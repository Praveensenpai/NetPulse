package com.netpulse.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SamsungBluePrimary,
    onPrimary = Color.White,
    secondary = SamsungBlueSecondary,
    onSecondary = Color.White,
    background = SamsungDarkBackground,
    onBackground = Color.White,
    surface = SamsungDarkSurface,
    onSurface = Color.White,
    surfaceVariant = SamsungDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC0C8D8),
    outline = SamsungDarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = SamsungBluePrimary,
    onPrimary = Color.White,
    secondary = SamsungBlueSecondary,
    onSecondary = Color.White,
    background = SamsungLightBackground,
    onBackground = Color(0xFF141824),
    surface = SamsungLightSurface,
    onSurface = Color(0xFF141824),
    surfaceVariant = SamsungLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF4A5568),
    outline = SamsungLightOutline
)

@Composable
fun NetPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NetPulseTypography,
        content = content
    )
}
