package me.rerere.readstack.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightScheme = lightColorScheme(
    primary = Indigo500,
    onPrimary = Cream50,
    primaryContainer = Indigo100,
    onPrimaryContainer = Indigo900,
    secondary = Indigo700,
    onSecondary = Cream50,
    secondaryContainer = Indigo50,
    onSecondaryContainer = Indigo900,
    tertiary = Amber500,
    onTertiary = Cream50,
    tertiaryContainer = Amber200,
    onTertiaryContainer = Amber800,
    error = Rose500,
    onError = Cream50,
    errorContainer = Rose200,
    onErrorContainer = Rose800,
    background = Cream50,
    onBackground = Cream900,
    surface = Cream50,
    onSurface = Cream900,
    surfaceVariant = Cream100,
    onSurfaceVariant = Cream700,
    surfaceContainerLowest = Cream50,
    surfaceContainerLow = Cream100,
    surfaceContainer = Cream100,
    surfaceContainerHigh = Cream200,
    surfaceContainerHighest = Cream300,
    outline = Cream500,
    outlineVariant = Cream300,
    inverseSurface = Cream900,
    inverseOnSurface = Cream100,
    inversePrimary = Indigo200,
    scrim = Neutral900,
)

private val DarkScheme = darkColorScheme(
    primary = IndigoDarkPrimary,
    onPrimary = IndigoDarkOnPrimary,
    primaryContainer = Indigo700,
    onPrimaryContainer = Indigo100,
    secondary = Indigo300,
    onSecondary = Indigo900,
    secondaryContainer = Indigo800,
    onSecondaryContainer = Indigo100,
    tertiary = Amber200,
    onTertiary = Amber800,
    tertiaryContainer = Amber800,
    onTertiaryContainer = Amber200,
    error = Rose200,
    onError = Rose800,
    errorContainer = Rose800,
    onErrorContainer = Rose200,
    background = CreamDarkBg,
    onBackground = CreamDarkOn,
    surface = CreamDarkBg,
    onSurface = CreamDarkOn,
    surfaceVariant = CreamDarkSurface,
    onSurfaceVariant = Cream200,
    surfaceContainerLowest = CreamDarkBg,
    surfaceContainerLow = CreamDarkSurface,
    surfaceContainer = CreamDarkSurface,
    surfaceContainerHigh = Neutral800,
    surfaceContainerHighest = Neutral800,
    outline = Cream500,
    outlineVariant = Neutral800,
    inverseSurface = Cream50,
    inverseOnSurface = Cream900,
    inversePrimary = Indigo500,
    scrim = Neutral900,
)

@Composable
fun ReadStackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ReadStackTypography,
        content = content,
    )
}
