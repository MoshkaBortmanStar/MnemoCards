package com.mnemocards.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MnemoColorScheme = darkColorScheme(
    primary = Violet,
    onPrimary = Ink,
    secondary = Blue,
    onSecondary = Ink,
    tertiary = Green,
    onTertiary = Ink,
    error = Red,
    onError = Ink,

    background = Ink,
    onBackground = TextMain,
    surface = Surface1,
    onSurface = TextMain,
    surfaceVariant = Surface2,
    onSurfaceVariant = Turquoise,
    outline = Outline
)

@Composable
fun MnemoCardsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MnemoColorScheme, typography = Typography, content = content
    )
}