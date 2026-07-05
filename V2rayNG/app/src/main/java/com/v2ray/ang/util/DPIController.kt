package com.v2ray.ang.util

import android.content.Context
import android.content.res.Configuration

object DPIController {

    fun wrapWithDpi(base: Context, dpiValue: Int): Context {
        if (dpiValue <= 0) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.densityDpi = dpiValue
        configuration.fontScale = base.resources.configuration.fontScale
        return base.createConfigurationContext(configuration)
    }

    fun applyDpi(context: Context, dpiValue: Int) {
        if (dpiValue <= 0) return
        
        val res = context.resources
        val configuration = Configuration(res.configuration)
        configuration.densityDpi = dpiValue

        val metrics = res.displayMetrics
        metrics.densityDpi = dpiValue
        metrics.density = dpiValue / 160f
        metrics.scaledDensity = metrics.density * configuration.fontScale

        @Suppress("DEPRECATION")
        res.updateConfiguration(configuration, metrics)
    }
}
