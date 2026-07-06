package com.v2ray.ang.util

import android.content.Context
import android.content.res.Configuration

object DPIController {

    // Cara modern Android: Gunakan createConfigurationContext (API 17+) murni tanpa manipulasi metrics
    fun wrapWithDpi(base: Context, dpiValue: Int): Context {
        if (dpiValue <= 0) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.densityDpi = dpiValue
        return base.createConfigurationContext(configuration)
    }

    fun applyDpi(context: Context, dpiValue: Int) {
        // No-op (Dikosongkan). 
        // Method updateConfiguration() sudah deprecated. 
        // Secara arsitektur AndroidX, perubahan DPI cukup mengandalkan attachBaseContext 
        // dan applyOverrideConfiguration di Activity, lalu memanggil recreate() seperti di DpiSliderDialog.
    }
}


