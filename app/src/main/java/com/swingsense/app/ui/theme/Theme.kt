package com.swingsense.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0E1114)
val Surface1 = Color(0xFF171C21)
val Surface2 = Color(0xFF202730)
val AccentOrange = Color(0xFFF0A03C)
val AccentBlue = Color(0xFF4FB0C6)
val TextPrimary = Color(0xFFF2F5F7)
val TextDim = Color(0xFF8C98A4)
val Ok = Color(0xFF5FD08A)
val Warn = Color(0xFFE2C044)
val Bad = Color(0xFFE2725B)

private val scheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.Black,
    secondary = AccentOrange,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextDim
)

@Composable
fun SwingSenseTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
