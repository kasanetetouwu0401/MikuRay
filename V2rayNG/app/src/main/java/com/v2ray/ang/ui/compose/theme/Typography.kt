package com.v2ray.ang.ui.compose.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.v2ray.ang.R

/** Material 3 default type scale — only the font family changes per user selection. */
val MikuTypography: Typography = Typography()

/**
 * Mirrors `AngApplication.getFontStyleResId` (the XML `StyleFontXxx` overlays), but resolves to a
 * Compose [FontFamily] instead of a theme overlay style resource. Keys and files match
 * `res/font/*.ttf` exactly, including the existing `incosolata.ttf` filename typo.
 */
fun mikuFontFamily(fontKey: String?): FontFamily = when (fontKey) {
    "ios15" -> FontFamily(Font(R.font.ios15))
    "google" -> FontFamily(Font(R.font.googlesansregular))
    "roboto" -> FontFamily(Font(R.font.robotoregular))
    "poppins" -> FontFamily(Font(R.font.poppinsregular))
    "chococooky" -> FontFamily(Font(R.font.chococookyregular))
    "simpleday" -> FontFamily(Font(R.font.simpleday))
    "fucek" -> FontFamily(Font(R.font.fucek))
    "sfprodisplay" -> FontFamily(Font(R.font.sfprodisplay))
    "dancingscript" -> FontFamily(Font(R.font.dancingscript))
    "cream" -> FontFamily(Font(R.font.cream))
    "oneui" -> FontFamily(Font(R.font.oneui))
    "inconsolata" -> FontFamily(Font(R.font.incosolata))
    "emilyscandy" -> FontFamily(Font(R.font.emilyscandy))
    "summerdream" -> FontFamily(Font(R.font.summerdream))
    "rine" -> FontFamily(Font(R.font.rine))
    "evolve" -> FontFamily(Font(R.font.evolvesans))
    else -> FontFamily.Default
}

/** Applies [fontFamily] to every text style in the type scale, keeping size/weight/spacing as-is. */
fun Typography.withFontFamily(fontFamily: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily),
)
