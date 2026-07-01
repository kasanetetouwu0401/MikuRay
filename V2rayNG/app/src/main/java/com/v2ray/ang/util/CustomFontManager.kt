package com.v2ray.ang.util

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import java.io.File

/**
 * Handles picking a custom font (.ttf/.otf/.ttc) from the device's internal storage via SAF,
 * persisting a private copy of it under the app's filesDir, loading it as a [Typeface], and
 * applying it as the process-wide default font (since a runtime file can't be compiled into a
 * @font resource, it can't be wired into the existing StyleFont* theme overlays).
 */
object CustomFontManager {

    private const val TAG = "CustomFontManager"
    private const val FONT_DIR = "custom_font"
    private val SUPPORTED_EXTENSIONS = setOf("ttf", "otf", "ttc")

    @Volatile
    private var cachedTypeface: Typeface? = null

    @Volatile
    private var cachedPath: String? = null

    /**
     * Copies the font pointed to by [uri] into internal storage, replacing any previously
     * saved custom font. Returns the saved [File] on success, or null if the file couldn't be
     * read or isn't a valid font.
     */
    fun saveFontFile(context: Context, uri: Uri, displayName: String?): File? {
        return try {
            val ext = displayName?.substringAfterLast('.', "")?.lowercase()
                ?.takeIf { it in SUPPORTED_EXTENSIONS } ?: "ttf"

            val dir = File(context.filesDir, FONT_DIR).apply { mkdirs() }
            // clear out any previously saved custom font file(s) first
            dir.listFiles()?.forEach { it.delete() }

            val destFile = File(dir, "font.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            // Validate it's actually a usable font before accepting it
            val typeface = try {
                Typeface.createFromFile(destFile)
            } catch (e: Exception) {
                null
            }
            if (typeface == null || typeface == Typeface.DEFAULT) {
                destFile.delete()
                return null
            }

            MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_CUSTOM_NAME, displayName ?: destFile.name)

            cachedTypeface = typeface
            cachedPath = destFile.absolutePath
            destFile
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to save custom font: ${e.message}")
            null
        }
    }

    /**
     * Same as [saveFontFile] but copies from a local [File] instead of a content [Uri].
     * Used when restoring a font file from a backup archive.
     */
    fun restoreFontFile(context: Context, srcFile: File, displayName: String?): File? {
        return try {
            val ext = srcFile.extension.lowercase().takeIf { it in SUPPORTED_EXTENSIONS } ?: "ttf"

            val dir = File(context.filesDir, FONT_DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }

            val destFile = File(dir, "font.$ext")
            srcFile.copyTo(destFile, overwrite = true)

            val typeface = try {
                Typeface.createFromFile(destFile)
            } catch (e: Exception) {
                null
            }
            if (typeface == null || typeface == Typeface.DEFAULT) {
                destFile.delete()
                return null
            }

            MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_CUSTOM_NAME, displayName ?: destFile.name)

            cachedTypeface = typeface
            cachedPath = destFile.absolutePath
            destFile
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to restore custom font: ${e.message}")
            null
        }
    }

    /** Returns the saved custom font file, if any. */
    fun getFontFile(context: Context): File? {
        val dir = File(context.filesDir, FONT_DIR)
        val file = dir.listFiles()?.firstOrNull { it.isFile }
        return file?.takeIf { it.exists() }
    }

    /** Deletes the saved custom font and resets cached state. */
    fun clearFont(context: Context) {
        File(context.filesDir, FONT_DIR).listFiles()?.forEach { it.delete() }
        MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_CUSTOM_NAME, "")
        cachedTypeface = null
        cachedPath = null
    }

    /** Display name of the currently saved custom font, if any. */
    fun getFontDisplayName(): String? =
        MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT_CUSTOM_NAME)?.takeIf { it.isNotEmpty() }

    /** Loads (and caches) the [Typeface] for the saved custom font file, or null if none/invalid. */
    fun getTypeface(context: Context): Typeface? {
        val file = getFontFile(context) ?: return null

        cachedTypeface?.let { if (cachedPath == file.absolutePath) return it }

        return try {
            val typeface = Typeface.createFromFile(file)
            cachedTypeface = typeface
            cachedPath = file.absolutePath
            typeface
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to load custom font: ${e.message}")
            null
        }
    }

    /**
     * Applies the custom font as the process-wide default typeface via reflection, so that
     * stock widgets which don't explicitly set a fontFamily (and therefore fall back to
     * Typeface.DEFAULT / the "sans-serif" family) pick it up too. Safe no-op on failure.
     */
    fun applyGlobalOverride(context: Context) {
        val typeface = getTypeface(context) ?: return
        replaceStaticField("DEFAULT", typeface)
        replaceStaticField("DEFAULT_BOLD", Typeface.create(typeface, Typeface.BOLD))
        replaceStaticField("SANS_SERIF", typeface)
        replaceSystemFontMapEntries(typeface)
    }

    private fun replaceStaticField(fieldName: String, typeface: Typeface) {
        try {
            val field = Typeface::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, typeface)
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to override Typeface.$fieldName: ${e.message}")
        }
    }

    // On API 28+, Typeface.create()/default widget styles resolve "sans-serif" (and friends)
    // through an internal static font map rather than the DEFAULT/SANS_SERIF fields directly.
    private fun replaceSystemFontMapEntries(typeface: Typeface) {
        try {
            val field = Typeface::class.java.getDeclaredField("sSystemFontMap")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val map = field.get(null) as? Map<String, Typeface> ?: return
            val mutableMap = HashMap(map)
            listOf(
                "sans-serif", "sans-serif-medium", "sans-serif-light",
                "sans-serif-thin", "sans-serif-black", "sans-serif-condensed",
                "normal", "default"
            ).forEach { mutableMap[it] = typeface }
            field.set(null, mutableMap)
        } catch (e: Exception) {
            // Not available on this API level / OEM build; the DEFAULT field override above
            // still covers most stock widgets.
            LogUtil.d(TAG, "sSystemFontMap override unavailable: ${e.message}")
        }
    }
}
