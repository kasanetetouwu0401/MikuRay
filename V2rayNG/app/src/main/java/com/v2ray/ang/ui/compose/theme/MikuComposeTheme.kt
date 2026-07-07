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
    val m = com.google.android.material.R.attr
    fun c(@AttrRes attr: Int) = context.themeColor(attr)

    return lightColorScheme(
        primary = c(m.colorPrimary),
        onPrimary = c(m.colorOnPrimary),
        primaryContainer = c(m.colorPrimaryContainer),
        onPrimaryContainer = c(m.colorOnPrimaryContainer),
        secondary = c(m.colorSecondary),
        onSecondary = c(m.colorOnSecondary),
        secondaryContainer = c(m.colorSecondaryContainer),
        onSecondaryContainer = c(m.colorOnSecondaryContainer),
        tertiary = c(m.colorTertiary),
        onTertiary = c(m.colorOnTertiary),
        tertiaryContainer = c(m.colorTertiaryContainer),
        onTertiaryContainer = c(m.colorOnTertiaryContainer),
        error = c(m.colorError),
        onError = c(m.colorOnError),
        errorContainer = c(m.colorErrorContainer),
        onErrorContainer = c(m.colorOnErrorContainer),
        background = context.themeColor(android.R.attr.colorBackground),
        onBackground = c(m.colorOnBackground),
        surface = c(m.colorSurface),
        onSurface = c(m.colorOnSurface),
        surfaceVariant = c(m.colorSurfaceVariant),
        onSurfaceVariant = c(m.colorOnSurfaceVariant),
        outline = c(m.colorOutline),
        outlineVariant = c(m.colorOutlineVariant),
        inverseSurface = c(m.colorSurfaceInverse),
        inverseOnSurface = c(m.colorOnSurfaceInverse),
        inversePrimary = c(m.colorPrimaryInverse),
        surfaceContainer = c(m.colorSurfaceContainer),
        surfaceContainerLow = c(m.colorSurfaceContainerLow),
        surfaceContainerHigh = c(m.colorSurfaceContainerHigh),
        surfaceContainerLowest = c(m.colorSurfaceContainerLowest),
        surfaceContainerHighest = c(m.colorSurfaceContainerHighest),
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
