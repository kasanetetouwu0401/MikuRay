package com.v2ray.ang.ui.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.MaterialDynamicColors

/**
 * Converts a Material Color Utilities [DynamicScheme] (e.g. a `SchemeTonalSpot` built from a
 * seed color, same as `ThemeManager.getDynamicScheme` / `applyCustomColorTheme` on the View side)
 * into a Compose [ColorScheme]. `DynamicScheme` already carries `isDark`, so every resolved role
 * below is already the correct light/dark tone — the `lightColorScheme(...)` call here is just
 * used as a plain data constructor, it does not force a light appearance.
 */
fun DynamicScheme.toComposeColorScheme(): ColorScheme {
    val roles = MaterialDynamicColors()
    fun argb(block: MaterialDynamicColors.() -> com.google.android.material.color.utilities.DynamicColor) =
        Color(roles.block().getArgb(this))

    return lightColorScheme(
        primary = argb { primary() },
        onPrimary = argb { onPrimary() },
        primaryContainer = argb { primaryContainer() },
        onPrimaryContainer = argb { onPrimaryContainer() },
        inversePrimary = argb { inversePrimary() },
        secondary = argb { secondary() },
        onSecondary = argb { onSecondary() },
        secondaryContainer = argb { secondaryContainer() },
        onSecondaryContainer = argb { onSecondaryContainer() },
        tertiary = argb { tertiary() },
        onTertiary = argb { onTertiary() },
        tertiaryContainer = argb { tertiaryContainer() },
        onTertiaryContainer = argb { onTertiaryContainer() },
        background = argb { background() },
        onBackground = argb { onBackground() },
        surface = argb { surface() },
        onSurface = argb { onSurface() },
        surfaceVariant = argb { surfaceVariant() },
        onSurfaceVariant = argb { onSurfaceVariant() },
        surfaceTint = argb { primary() },
        inverseSurface = argb { inverseSurface() },
        inverseOnSurface = argb { inverseOnSurface() },
        error = argb { error() },
        onError = argb { onError() },
        errorContainer = argb { errorContainer() },
        onErrorContainer = argb { onErrorContainer() },
        outline = argb { outline() },
        outlineVariant = argb { outlineVariant() },
        scrim = argb { scrim() },
        surfaceBright = argb { surfaceBright() },
        surfaceContainer = argb { surfaceContainer() },
        surfaceContainerHigh = argb { surfaceContainerHigh() },
        surfaceContainerHighest = argb { surfaceContainerHighest() },
        surfaceContainerLow = argb { surfaceContainerLow() },
        surfaceContainerLowest = argb { surfaceContainerLowest() },
        surfaceDim = argb { surfaceDim() },
        primaryFixed = argb { primaryFixed() },
        primaryFixedDim = argb { primaryFixedDim() },
        onPrimaryFixed = argb { onPrimaryFixed() },
        onPrimaryFixedVariant = argb { onPrimaryFixedVariant() },
        secondaryFixed = argb { secondaryFixed() },
        secondaryFixedDim = argb { secondaryFixedDim() },
        onSecondaryFixed = argb { onSecondaryFixed() },
        onSecondaryFixedVariant = argb { onSecondaryFixedVariant() },
        tertiaryFixed = argb { tertiaryFixed() },
        tertiaryFixedDim = argb { tertiaryFixedDim() },
        onTertiaryFixed = argb { onTertiaryFixed() },
        onTertiaryFixedVariant = argb { onTertiaryFixedVariant() },
    )
}

/**
 * Mirrors `res/values-night/themes.xml` -> `ThemeOverlay.App.TrueBlack`: only background/surface
 * roles are pushed to near-black, everything else (primary, secondary, etc.) keeps the palette's
 * own tone. Only meant to be applied when dark theme is active, same as the View-based overlay.
 */
fun ColorScheme.withTrueBlackOverlay(): ColorScheme = copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF1A1A1A),
    surfaceContainerLow = Color(0xFF131313),
    surfaceContainerHigh = Color(0xFF212121),
    surfaceContainerHighest = Color(0xFF212121),
)
