package com.v2ray.ang.ui.compose.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot

/**
 * Compose equivalent of `ThemeManager.applyTheme`. Precedence is kept identical to the View-based
 * screens so a screen doesn't change tone the moment it's migrated to Compose:
 *
 * 1. Banner-based dynamic color (`PREF_DYNAMIC_COLOR_BANNER`, API 31+)
 * 2. Wallpaper-based Material You (`PREF_DYNAMIC_COLOR`, API 31+)
 * 3. Custom seed color (`PREF_USE_CUSTOM_COLOR`)
 * 4. Selected palette (`PREF_APP_THEME`, see [MikuPaletteKey])
 *
 * True Black is layered on top of whichever base scheme was chosen, only while in dark mode —
 * same as `ThemeOverlay.App.TrueBlack` being applied on top of any palette in `values-night`.
 */
@Composable
fun MikuRayTheme(
    paletteKey: String,
    dynamicColor: Boolean = false,
    dynamicBannerSeed: Int? = null,
    customSeedColor: Int? = null,
    trueBlack: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    typography: Typography = MikuTypography,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    var colorScheme: ColorScheme = when {
        dynamicBannerSeed != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            SchemeTonalSpot(Hct.fromInt(dynamicBannerSeed), darkTheme, 0.0).toComposeColorScheme()

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        customSeedColor != null && customSeedColor != 0 ->
            SchemeTonalSpot(Hct.fromInt(customSeedColor), darkTheme, 0.0).toComposeColorScheme()

        else -> mikuColorScheme(paletteKey, darkTheme)
    }

    if (trueBlack && darkTheme) {
        colorScheme = colorScheme.withTrueBlackOverlay()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}
