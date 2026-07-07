package com.v2ray.ang.ui.compose.theme

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.annotation.AttrRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.color.MaterialColors
import com.v2ray.ang.R

/**
 * Warna kustom MikuRay (colorBg, colorCard) yang tidak punya slot resmi di
 * androidx.compose.material3.ColorScheme. Diambil dari attr XML yang sama
 * persis dengan yang dipakai View, jadi tidak ada palette kedua yang harus
 * dirawat manual di Compose.
 */
data class MikuExtraColors(
    val colorBg: Color,
    val colorCard: Color,
)

private val LocalMikuExtraColors = compositionLocalOf {
    MikuExtraColors(colorBg = Color.Unspecified, colorCard = Color.Unspecified)
}

/** Pemakaian: `MikuTheme.extraColors.colorCard` di dalam Composable manapun. */
object MikuTheme {
    val extraColors: MikuExtraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMikuExtraColors.current
}

/** Resolve satu attr tema (mis. com.google.android.material.R.attr.colorPrimary) ke Compose Color. */
private fun Context.themeColor(@AttrRes attr: Int): Color =
    Color(MaterialColors.getColor(this, attr, AndroidColor.MAGENTA))

/**
 * Baca ColorScheme M3 langsung dari attr tema yang SEDANG aktif di [context]
 * (Activity yang sudah lewat ThemeManager.applyTheme()). Karena dibaca dari
 * resolved theme attrs, ini otomatis ikut:
 * - 4 palet warna (red/blue/dst) di themes.xml
 * - dynamic color (Material You)
 * - custom color user
 * - true black mode
 */
private fun buildColorSchemeFromXmlTheme(context: Context): ColorScheme {
    fun c(@AttrRes attr: Int) = context.themeColor(attr)

    return lightColorScheme(
        primary = c(com.google.android.material.R.attr.colorPrimary),
        onPrimary = c(com.google.android.material.R.attr.colorOnPrimary),
        primaryContainer = c(com.google.android.material.R.attr.colorPrimaryContainer),
        onPrimaryContainer = c(com.google.android.material.R.attr.colorOnPrimaryContainer),
        secondary = c(com.google.android.material.R.attr.colorSecondary),
        onSecondary = c(com.google.android.material.R.attr.colorOnSecondary),
        secondaryContainer = c(com.google.android.material.R.attr.colorSecondaryContainer),
        onSecondaryContainer = c(com.google.android.material.R.attr.colorOnSecondaryContainer),
        tertiary = c(com.google.android.material.R.attr.colorTertiary),
        onTertiary = c(com.google.android.material.R.attr.colorOnTertiary),
        tertiaryContainer = c(com.google.android.material.R.attr.colorTertiaryContainer),
        onTertiaryContainer = c(com.google.android.material.R.attr.colorOnTertiaryContainer),
        error = c(com.google.android.material.R.attr.colorError),
        onError = c(com.google.android.material.R.attr.colorOnError),
        errorContainer = c(com.google.android.material.R.attr.colorErrorContainer),
        onErrorContainer = c(com.google.android.material.R.attr.colorOnErrorContainer),
        background = context.themeColor(android.R.attr.colorBackground),
        onBackground = c(com.google.android.material.R.attr.colorOnBackground),
        surface = c(com.google.android.material.R.attr.colorSurface),
        onSurface = c(com.google.android.material.R.attr.colorOnSurface),
        surfaceVariant = c(com.google.android.material.R.attr.colorSurfaceVariant),
        onSurfaceVariant = c(com.google.android.material.R.attr.colorOnSurfaceVariant),
        outline = c(com.google.android.material.R.attr.colorOutline),
        outlineVariant = c(com.google.android.material.R.attr.colorOutlineVariant),
        inverseSurface = c(com.google.android.material.R.attr.colorSurfaceInverse),
        inverseOnSurface = c(com.google.android.material.R.attr.colorOnSurfaceInverse),
        inversePrimary = c(com.google.android.material.R.attr.colorPrimaryInverse),
        surfaceContainer = c(com.google.android.material.R.attr.colorSurfaceContainer),
        surfaceContainerLow = c(com.google.android.material.R.attr.colorSurfaceContainerLow),
        surfaceContainerHigh = c(com.google.android.material.R.attr.colorSurfaceContainerHigh),
        surfaceContainerLowest = c(com.google.android.material.R.attr.colorSurfaceContainerLowest),
        surfaceContainerHighest = c(com.google.android.material.R.attr.colorSurfaceContainerHighest),
    )
}

/**
 * Bungkus Composable yang ditempel ke Activity/Fragment lama dengan ini, misal:
 *
 * binding.composeSlot.setContent {
 *     MikuComposeTheme {
 *         AboutSourceRow(...)
 *     }
 * }
 *
 * Tampilannya identik dengan View lama karena warna diambil dari attr tema
 * yang sama persis, bukan didefinisikan ulang di sisi Compose.
 */
@Composable
fun MikuComposeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current

    // remember(context): kalau Activity di-recreate (ganti tema/palet/dark-light),
    // context instance-nya beda -> ColorScheme dibaca ulang otomatis.
    val colorScheme = remember(context) { buildColorSchemeFromXmlTheme(context) }
    val extraColors = remember(context) {
        MikuExtraColors(
            colorBg = context.themeColor(R.attr.colorBg),
            colorCard = context.themeColor(R.attr.colorCard),
        )
    }

    CompositionLocalProvider(LocalMikuExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content,
        )
    }
}
