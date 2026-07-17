package com.pang.mdreader.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.pang.mdreader.model.ReaderTheme

private val WarmLightColorScheme = lightColorScheme(
    primary = WarmLightPrimary,
    onPrimary = WarmLightOnPrimary,
    primaryContainer = WarmLightPrimaryContainer,
    onPrimaryContainer = WarmLightOnPrimaryContainer,
    secondary = WarmLightSecondary,
    onSecondary = WarmLightOnSecondary,
    secondaryContainer = WarmLightSecondaryContainer,
    onSecondaryContainer = WarmLightOnSecondaryContainer,
    tertiary = WarmLightTertiary,
    onTertiary = WarmLightOnTertiary,
    tertiaryContainer = WarmLightTertiaryContainer,
    onTertiaryContainer = WarmLightOnTertiaryContainer,
    background = WarmLightBackground,
    onBackground = WarmLightOnBackground,
    surface = WarmLightSurface,
    onSurface = WarmLightOnSurface,
    surfaceVariant = WarmLightSurfaceVariant,
    onSurfaceVariant = WarmLightOnSurfaceVariant,
    surfaceContainerLowest = WarmLightSurfaceContainerLowest,
    surfaceContainerLow = WarmLightSurfaceContainerLow,
    surfaceContainer = WarmLightSurfaceContainer,
    surfaceContainerHigh = WarmLightSurfaceContainerHigh,
    surfaceContainerHighest = WarmLightSurfaceContainerHighest,
    outline = WarmLightOutline,
    outlineVariant = WarmLightOutlineVariant,
    error = WarmLightError,
)

private val WarmDarkColorScheme = darkColorScheme(
    primary = WarmDarkPrimary,
    onPrimary = WarmDarkOnPrimary,
    primaryContainer = WarmDarkPrimaryContainer,
    onPrimaryContainer = WarmDarkOnPrimaryContainer,
    secondary = WarmDarkSecondary,
    onSecondary = WarmDarkOnSecondary,
    secondaryContainer = WarmDarkSecondaryContainer,
    onSecondaryContainer = WarmDarkOnSecondaryContainer,
    tertiary = WarmDarkTertiary,
    onTertiary = WarmDarkOnTertiary,
    tertiaryContainer = WarmDarkTertiaryContainer,
    onTertiaryContainer = WarmDarkOnTertiaryContainer,
    background = WarmDarkBackground,
    onBackground = WarmDarkOnBackground,
    surface = WarmDarkSurface,
    onSurface = WarmDarkOnSurface,
    surfaceVariant = WarmDarkSurfaceVariant,
    onSurfaceVariant = WarmDarkOnSurfaceVariant,
    surfaceContainerLowest = WarmDarkSurfaceContainerLowest,
    surfaceContainerLow = WarmDarkSurfaceContainerLow,
    surfaceContainer = WarmDarkSurfaceContainer,
    surfaceContainerHigh = WarmDarkSurfaceContainerHigh,
    surfaceContainerHighest = WarmDarkSurfaceContainerHighest,
    outline = WarmDarkOutline,
    outlineVariant = WarmDarkOutlineVariant,
    error = WarmDarkError,
)

private val GitHubLightColorScheme = lightColorScheme(
    primary = GitHubLightPrimary,
    onPrimary = GitHubLightOnPrimary,
    primaryContainer = GitHubLightPrimaryContainer,
    onPrimaryContainer = GitHubLightOnPrimaryContainer,
    secondary = GitHubLightSecondary,
    onSecondary = GitHubLightOnSecondary,
    secondaryContainer = GitHubLightSecondaryContainer,
    onSecondaryContainer = GitHubLightOnSecondaryContainer,
    background = GitHubLightBackground,
    onBackground = GitHubLightOnBackground,
    surface = GitHubLightSurface,
    onSurface = GitHubLightOnSurface,
    surfaceVariant = GitHubLightSurfaceVariant,
    onSurfaceVariant = GitHubLightOnSurfaceVariant,
    surfaceContainerLowest = GitHubLightSurfaceContainerLowest,
    surfaceContainerLow = GitHubLightSurfaceContainerLow,
    surfaceContainer = GitHubLightSurfaceContainer,
    surfaceContainerHigh = GitHubLightSurfaceContainerHigh,
    surfaceContainerHighest = GitHubLightSurfaceContainerHighest,
    outline = GitHubLightOutline,
    outlineVariant = GitHubLightOutlineVariant,
)

/**
 * Determine the app shell theme based on ReaderTheme and system dark mode.
 */
@Composable
fun MdReaderTheme(
    readerTheme: ReaderTheme = ReaderTheme.WARM_LIGHT,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()

    val (colorScheme, useDark) = when (readerTheme) {
        ReaderTheme.WARM_LIGHT -> WarmLightColorScheme to false
        ReaderTheme.WARM_DARK -> WarmDarkColorScheme to true
        ReaderTheme.GITHUB -> GitHubLightColorScheme to false
        ReaderTheme.SYSTEM -> {
            if (systemDark) WarmDarkColorScheme to true
            else WarmLightColorScheme to false
        }
    }

    // Edge-to-edge status bar
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
