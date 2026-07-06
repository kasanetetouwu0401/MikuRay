package com.v2ray.ang.ui.compose

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.compose.theme.MikuRayTheme
import com.v2ray.ang.ui.compose.theme.MikuTypography
import com.v2ray.ang.ui.compose.theme.mikuFontFamily
import com.v2ray.ang.ui.compose.theme.withFontFamily
import com.v2ray.ang.util.CustomFontManager
import com.v2ray.ang.util.DPIController
import com.v2ray.ang.util.MyContextWrapper

/**
 * Compose equivalent of `BaseActivity`. Screens migrated to Compose extend this instead of
 * `BaseActivity`, and call [setMikuContent] instead of `setContent` directly, so palette/dynamic
 * color/true-black/font/locale/DPI parity with the still-XML screens doesn't need to be
 * hand-rolled on every screen.
 *
 * Not yet ported here on purpose (left for the screen that needs it, during its own migration
 * step, to avoid guessing behavior this Fase 0 pass didn't verify):
 * - `WindowBlurUtils.applyWindowBlur` on child dialogs (depends on the Fase 1 blur modifier).
 * - The loading overlay (`showLoading`/`hideLoading`) — will become a composable overlay.
 * - Collapsing toolbar custom-font application from `onPostCreate` — only relevant to screens
 *   that still use the XML collapsing toolbar.
 */
abstract class ComposeBaseActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context?) {
        if (newBase == null) {
            super.attachBaseContext(newBase)
            return
        }
        val dpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
        val localeWrapped = MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        val finalContext = if (dpi > 0) DPIController.wrapWithDpi(localeWrapped, dpi) else localeWrapped
        super.attachBaseContext(finalContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    /** Reads current theme/font settings from MMKV and wraps [content] in [MikuRayTheme]. */
    protected fun setMikuContent(content: @Composable () -> Unit) {
        setContent {
            val paletteKey = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_THEME) ?: "8"
            val dynamicColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
            val useCustomColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_CUSTOM_COLOR, false)
            val customColor = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_COLOR, 0)
            val trueBlack = MmkvManager.decodeSettingsBool(AppConfig.PREF_TRUE_BLACK, false)
            val bannerColor = MmkvManager.decodeSettingsInt(AppConfig.PREF_BANNER_COLOR, 0)
            val dynamicBanner = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)
            val useCustomFont = MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)
            val fontKey = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT)

            // NOTE: FontFamily(Typeface) below assumes the Compose UI version on this project
            // exposes that constructor overload for a plain android.graphics.Typeface. Not
            // verified against this project's exact Compose UI version in this sandbox (no
            // Android SDK / network here) — double check this compiles, and if the overload
            // isn't available, fall back to `FontFamily.Default` and file it as a follow-up.
            val typography = if (useCustomFont) {
                CustomFontManager.getTypeface(this@ComposeBaseActivity)?.let { typeface ->
                    MikuTypography.withFontFamily(androidx.compose.ui.text.font.FontFamily(typeface))
                } ?: MikuTypography
            } else {
                MikuTypography.withFontFamily(mikuFontFamily(fontKey))
            }

            MikuRayTheme(
                paletteKey = paletteKey,
                dynamicColor = dynamicColor,
                dynamicBannerSeed = if (dynamicBanner && bannerColor != 0) bannerColor else null,
                customSeedColor = if (useCustomColor && customColor != 0) customColor else null,
                trueBlack = trueBlack,
                typography = typography,
                content = content,
            )
        }
    }
}
