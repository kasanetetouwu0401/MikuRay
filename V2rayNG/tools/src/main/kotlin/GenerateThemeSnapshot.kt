package com.v2ray.ang.tools

/*
 * Theme snapshot generator (batch + verify).
 *
 * Generates static AppTheme_<Name> color/style blocks from seed colors,
 * using the same HCT / SchemeTonalSpot pipeline as ThemeManager's dynamic
 * color path — matching the exact format of the existing AppTheme_Red,
 * AppTheme_Teal, etc.
 */

import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot
import java.io.File

// ---- Token list, in the exact order used by the existing themes ----

private data class TokenSpec(val suffix: String, val getter: MaterialDynamicColors.() -> com.google.android.material.color.utilities.DynamicColor)

private val TOKENS: List<TokenSpec> = listOf(
    TokenSpec("primary") { primary() },
    TokenSpec("onPrimary") { onPrimary() },
    TokenSpec("primaryContainer") { primaryContainer() },
    TokenSpec("onPrimaryContainer") { onPrimaryContainer() },
    TokenSpec("secondary") { secondary() },
    TokenSpec("onSecondary") { onSecondary() },
    TokenSpec("secondaryContainer") { secondaryContainer() },
    TokenSpec("onSecondaryContainer") { onSecondaryContainer() },
    TokenSpec("tertiary") { tertiary() },
    TokenSpec("onTertiary") { onTertiary() },
    TokenSpec("tertiaryContainer") { tertiaryContainer() },
    TokenSpec("onTertiaryContainer") { onTertiaryContainer() },
    TokenSpec("error") { error() },
    TokenSpec("onError") { onError() },
    TokenSpec("errorContainer") { errorContainer() },
    TokenSpec("onErrorContainer") { onErrorContainer() },
    TokenSpec("background") { background() },
    TokenSpec("onBackground") { onBackground() },
    TokenSpec("surface") { surface() },
    TokenSpec("onSurface") { onSurface() },
    TokenSpec("surfaceVariant") { surfaceVariant() },
    TokenSpec("onSurfaceVariant") { onSurfaceVariant() },
    TokenSpec("outline") { outline() },
    TokenSpec("outlineVariant") { outlineVariant() },
    TokenSpec("inverseSurface") { inverseSurface() },
    TokenSpec("inverseOnSurface") { inverseOnSurface() },
    TokenSpec("inversePrimary") { inversePrimary() },
    TokenSpec("primaryFixed") { primaryFixed() },
    TokenSpec("onPrimaryFixed") { onPrimaryFixed() },
    TokenSpec("primaryFixedDim") { primaryFixedDim() },
    TokenSpec("onPrimaryFixedVariant") { onPrimaryFixedVariant() },
    TokenSpec("secondaryFixed") { secondaryFixed() },
    TokenSpec("onSecondaryFixed") { onSecondaryFixed() },
    TokenSpec("secondaryFixedDim") { secondaryFixedDim() },
    TokenSpec("onSecondaryFixedVariant") { onSecondaryFixedVariant() },
    TokenSpec("tertiaryFixed") { tertiaryFixed() },
    TokenSpec("onTertiaryFixed") { onTertiaryFixed() },
    TokenSpec("tertiaryFixedDim") { tertiaryFixedDim() },
    TokenSpec("onTertiaryFixedVariant") { onTertiaryFixedVariant() },
    TokenSpec("surfaceDim") { surfaceDim() },
    TokenSpec("surfaceBright") { surfaceBright() },
    TokenSpec("surfaceContainerLowest") { surfaceContainerLowest() },
    TokenSpec("surfaceContainerLow") { surfaceContainerLow() },
    TokenSpec("surfaceContainer") { surfaceContainer() },
    TokenSpec("surfaceContainerHigh") { surfaceContainerHigh() },
    TokenSpec("surfaceContainerHighest") { surfaceContainerHighest() },
)

private val STYLE_ATTR_OVERRIDES = mapOf(
    "background" to "android:colorBackground",
    "inverseSurface" to "colorSurfaceInverse",
    "inverseOnSurface" to "colorOnSurfaceInverse",
    "inversePrimary" to "colorPrimaryInverse",
)

private fun styleAttrFor(suffix: String): String =
    STYLE_ATTR_OVERRIDES[suffix] ?: "color${suffix.replaceFirstChar { it.uppercase() }}"

private fun hex(argb: Int): String {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "#%02X%02X%02X".format(r, g, b)
}

/** One theme to generate: [key] is the colors.xml prefix (lowerCamel, e.g. "deepPurple"),
 * [displayName] is the AppTheme_<displayName> suffix (PascalCase, e.g. "DeepPurple"),
 * [seedHex] is the RRGGBB seed color (no #). */
private data class ThemeSpec(val key: String, val displayName: String, val seedHex: String)

private fun computeTokens(seedHex: String, isDark: Boolean): List<Pair<TokenSpec, Int>> {
    val seedArgb = (0xFF shl 24) or seedHex.toInt(16)
    val hct = Hct.fromInt(seedArgb)
    val scheme = SchemeTonalSpot(hct, isDark, 0.0)
    val mdc = MaterialDynamicColors()
    return TOKENS.map { token -> token to token.getter(mdc).getArgb(scheme) }
}

private fun generateColorsBlock(themeKey: String, seedHex: String, isDark: Boolean): String {
    val sb = StringBuilder()
    for ((token, argb) in computeTokens(seedHex, isDark)) {
        sb.appendLine("    <color name=\"${themeKey}_${token.suffix}\">${hex(argb)}</color>")
    }
    return sb.toString()
}

private fun generateStyleBlock(themeKey: String, displayName: String): String {
    val sb = StringBuilder()
    sb.appendLine()
    sb.appendLine("    <style name=\"AppTheme_$displayName\" parent=\"AppThemeBase\">")
    for (token in TOKENS) {
        sb.appendLine("        <item name=\"${styleAttrFor(token.suffix)}\">@color/${themeKey}_${token.suffix}</item>")
    }
    sb.appendLine("    </style>")
    return sb.toString()
}

private fun insertBeforeClosingTag(file: File, block: String, closingTag: String = "</resources>") {
    val content = file.readText()
    if (!content.contains(closingTag)) error("Could not find $closingTag in ${file.path}")
    val idx = content.lastIndexOf(closingTag)
    file.writeText(content.substring(0, idx) + block + "\n" + content.substring(idx))
}

private fun existingColorsFile(): File = File("app/src/main/res/values/colors.xml")
private fun existingColorsNightFile(): File = File("app/src/main/res/values-night/colors.xml")
private fun existingThemesFile(): File = File("app/src/main/res/values/themes.xml")

/** Extracts an existing `<color name="key_suffix">HEX</color>` value from a colors.xml's raw text, or null if absent. */
private fun extractExistingColor(fileText: String, themeKey: String, suffix: String): String? {
    val regex = Regex("<color name=\"${Regex.escape(themeKey)}_${Regex.escape(suffix)}\">([^<]*)</color>")
    return regex.find(fileText)?.groupValues?.get(1)
}

private fun verify(themes: List<ThemeSpec>) {
    val lightText = existingColorsFile().readText()
    var totalMismatches = 0
    for (theme in themes) {
        println("==== Verifying AppTheme_${theme.displayName} (key: ${theme.key}, seed: #${theme.seedHex}) ====")
        val generated = computeTokens(theme.seedHex, isDark = false)
        var themeMismatches = 0
        for ((token, argb) in generated) {
            val generatedHex = hex(argb)
            val existingHex = extractExistingColor(lightText, theme.key, token.suffix)
            when {
                existingHex == null -> {
                    println("  [NEW]      ${token.suffix}: no existing value (theme not in colors.xml yet)")
                }
                existingHex.equals(generatedHex, ignoreCase = true) -> {
                    // match, silent
                }
                else -> {
                    themeMismatches++
                    println("  [MISMATCH] ${token.suffix}: existing=$existingHex  generated=$generatedHex")
                }
            }
        }
        if (themeMismatches == 0) {
            println("  All ${generated.size} tokens match existing colors.xml. Safe to regenerate.")
        } else {
            println("  $themeMismatches token(s) differ — do NOT delete the handwritten block yet.")
        }
        totalMismatches += themeMismatches
        println()
    }
    println(if (totalMismatches == 0) "VERIFY PASSED for all ${themes.size} theme(s)." else "VERIFY FOUND $totalMismatches mismatch(es) total — review above before deleting anything.")
}

private fun generate(themes: List<ThemeSpec>) {
    val colorsLight = existingColorsFile()
    val colorsNight = existingColorsNightFile()
    val themesLight = existingThemesFile()
    for (f in listOf(colorsLight, colorsNight, themesLight)) {
        if (!f.exists()) error("Expected file not found: ${f.path} (run this from the project root)")
    }

    for (theme in themes) {
        if (colorsLight.readText().contains("${theme.key}_primary")) {
            error("Theme key '${theme.key}' already has tokens in ${colorsLight.path}. " +
                "Remove the existing handwritten block for AppTheme_${theme.displayName} first, or use --verify to confirm it matches before deleting.")
        }
    }

    for (theme in themes) {
        val header = "\n    \n"
        insertBeforeClosingTag(colorsLight, header + generateColorsBlock(theme.key, theme.seedHex, isDark = false))
        insertBeforeClosingTag(colorsNight, header + generateColorsBlock(theme.key, theme.seedHex, isDark = true))
        insertBeforeClosingTag(themesLight, generateStyleBlock(theme.key, theme.displayName))
        println("Generated AppTheme_${theme.displayName} (key: ${theme.key}, seed: #${theme.seedHex})")
    }
    println()
    println("Wrote ${themes.size} theme(s) to colors.xml, colors-night.xml, and themes.xml.")
    println("Remaining manual steps per theme: ThemeManager.getThemeStyleRes(key), and the color picker grid (ThemeColorDialog).")
}

// ============================================================================
// MAIN FUNCTION - Entry Point
// ============================================================================
fun main() {
    // "verify"   -> dry run, diffs against existing colors.xml, writes nothing
    // "generate" -> actually writes new color/style blocks
    val mode = "generate"

    // Material Design 2 classic palette, used as seeds for all 19 themes.
    // (DeepPurple/LightBlue/DeepOrange/Grey are new additions to the original 16.)
    val themes = listOf(
        ThemeSpec("red", "Red", "F44336"),
        ThemeSpec("pink", "Pink", "E91E63"),
        ThemeSpec("purple", "Purple", "9C27B0"),
        ThemeSpec("deepPurple", "DeepPurple", "673AB7"),
        ThemeSpec("indigo", "Indigo", "3F51B5"),
        ThemeSpec("blue", "Blue", "2196F3"),
        ThemeSpec("lightBlue", "LightBlue", "03A9F4"),
        ThemeSpec("cyan", "Cyan", "00BCD4"),
        ThemeSpec("teal", "Teal", "009688"),
        ThemeSpec("green", "Green", "4CAF50"),
        ThemeSpec("lightGreen", "LightGreen", "8BC34A"),
        ThemeSpec("lime", "Lime", "CDDC39"),
        ThemeSpec("yellow", "Yellow", "FFEB3B"),
        ThemeSpec("amber", "Amber", "FFC107"),
        ThemeSpec("orange", "Orange", "FF9800"),
        ThemeSpec("deepOrange", "DeepOrange", "FF5722"),
        ThemeSpec("brown", "Brown", "795548"),
        ThemeSpec("grey", "Grey", "9E9E9E"),
        ThemeSpec("blueGrey", "BlueGrey", "607D8B")
    )

    when (mode) {
        "verify" -> verify(themes)
        "generate" -> generate(themes)
        else -> println("Unknown mode '$mode' — use \"verify\" or \"generate\".")
    }
}
