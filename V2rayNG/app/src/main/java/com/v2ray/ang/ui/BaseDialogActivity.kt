package com.v2ray.ang.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.DPIController
import com.v2ray.ang.util.MyContextWrapper
import com.v2ray.ang.util.ThemeManager
import com.v2ray.ang.util.ThemeStateManager
import com.v2ray.ang.util.WindowBlurUtils

abstract class BaseDialogActivity : AppCompatActivity() {
    private lateinit var themeStateManager: ThemeStateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        themeStateManager = ThemeStateManager(this)

        // 1. Terapkan Tema Utama (Dynamic Color / Material You / Custom Color)[span_2](start_span)[span_2](end_span)
        ThemeManager.applyTheme(this) 
        
        // 2. Suntikkan kembali atribut Dialog agar tidak hilang ditimpa tema utama[span_3](start_span)[span_3](end_span)
        theme.applyStyle(R.style.Theme_AppDialog, true)

        // 3. Terapkan custom font & True Black[span_4](start_span)[span_4](end_span)
        val fontOverlayId = getFontStyleResId(MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT))
        if (fontOverlayId != 0) {
            theme.applyStyle(fontOverlayId, true)
            
            val isTrueBlack = ThemeManager.isDarkMode(this) && MmkvManager.decodeSettingsBool(AppConfig.PREF_TRUE_BLACK, false)
            if (isTrueBlack) {
                theme.applyStyle(R.style.ThemeOverlay_App_TrueBlack_DialogFix, true)
            }
        }

        super.onCreate(savedInstanceState)

        // 4. Atur Window agar bertindak sebagai popup dialog[span_5](start_span)[span_5](end_span)
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(), 
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(android.view.Gravity.CENTER)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.5f)

        // 5. Live Blur menggunakan library eksternal agar kompatibel di semua versi Android
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_BLUR, false)) {
            WindowBlurUtils.applyWindowBlur(window)
        }
    }

    override fun onResume() {
        super.onResume()
        themeStateManager.checkThemeChangedAndRecreate() //[span_6](start_span)[span_6](end_span)
    }

    // --- Pertahankan sinkronisasi DPI dan Bahasa dari BaseActivity --- //[span_7](start_span)[span_7](end_span)
    override fun attachBaseContext(newBase: Context?) {
        val base = newBase ?: return
        val dpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
        val localeWrapped = MyContextWrapper.wrap(base, SettingsManager.getLocale())
        val finalContext = if (dpi > 0) DPIController.wrapWithDpi(localeWrapped, dpi) else localeWrapped
        super.attachBaseContext(finalContext)
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode

            val dpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
            if (dpi > 0) {
                overrideConfiguration.densityDpi = dpi
            }
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    private fun getFontStyleResId(fontName: String?): Int { //[span_8](start_span)[span_8](end_span)
        return when (fontName) {
            "google"       -> R.style.StyleFontGoogle
            "roboto"       -> R.style.StyleFontRoboto
            "poppins"      -> R.style.StyleFontPoppins
            "chococooky"   -> R.style.StyleFontChocoCooky
            "simpleday"    -> R.style.StyleFontSimpleDay
            "fucek"        -> R.style.StyleFontFucek
            "sfprodisplay" -> R.style.StyleFontSFProDisplay
            "dancingscript"-> R.style.StyleFontDancingScript
            "cream"        -> R.style.StyleFontCream
            "oneui"        -> R.style.StyleFontOneUI
            "inconsolata"  -> R.style.StyleFontInconsolata
            "emilyscandy"  -> R.style.StyleFontEmilysCandy
            "summerdream"  -> R.style.StyleFontSummerDream
            "rine"         -> R.style.StyleFontRine
            "evolve"       -> R.style.StyleFontEvolve
            else           -> 0
        }
    }
}
