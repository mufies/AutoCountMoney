package com.example.autocountmoney.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF17644B), onPrimary = Color.White,
    primaryContainer = Color(0xFFDEF1E4), onPrimaryContainer = Color(0xFF123B2D),
    secondary = Color(0xFF986020), secondaryContainer = Color(0xFFFFEDD3),
    background = Color(0xFFF5F6F0), surface = Color(0xFFFEFFF9),
    onBackground = Color(0xFF182E25), onSurface = Color(0xFF182E25),
    surfaceVariant = Color(0xFFE8EDE5), onSurfaceVariant = Color(0xFF57675D),
    outline = Color(0xFFB8C5BA)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BDBB8), primaryContainer = Color(0xFF194D38),
    background = Color(0xFF101C16), surface = Color(0xFF18271F),
    secondaryContainer = Color(0xFF574220), onSecondaryContainer = Color(0xFFFFEDD3)
)

@Composable
fun AutoCountMoneyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography, content = content)
}
