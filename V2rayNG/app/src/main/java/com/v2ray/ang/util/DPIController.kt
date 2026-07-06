package com.v2ray.ang.util

import android.content.Context
import android.content.res.Configuration

object DPIController {

    fun wrapWithDpi(base: Context, dpiValue: Int): Context {
        if (dpiValue <= 0) return base
        
        val resources = base.resources
        val configuration = Configuration(resources.configuration)
        configuration.densityDpi = dpiValue
        
        // FIX BLUR NGEZOOM & ROUNDED CORNER HILANG:
        // Meskipun deprecated, kita tetap harus mengupdate DisplayMetrics di sini.
        // Tujuannya agar library/fungsi Blur dan Window Dialog membaca skala yang sama dengan Activity,
        // bukan menggunakan DPI bawaan HP yang menyebabkan ukurannya mismatch (ngezoom).
        val metrics = resources.displayMetrics
        metrics.densityDpi = dpiValue
        metrics.density = dpiValue / 160f
        metrics.scaledDensity = metrics.density * configuration.fontScale
        
        return base.createConfigurationContext(configuration)
    }

    fun applyDpi(context: Context, dpiValue: Int) {
        // Dibiarkan kosong karena perubahan DPI saat runtime sudah di-handle 
        // oleh activity.recreate() di DpiSliderDialog
        // dan dibaca ulang di attachBaseContext.
    }
}


