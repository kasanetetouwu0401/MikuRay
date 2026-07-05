package com.v2ray.ang.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources

object DPIController {

    fun wrapWithDpi(base: Context, dpiValue: Int): Context {
        if (dpiValue <= 0) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.densityDpi = dpiValue
        
        val context = base.createConfigurationContext(configuration)
        
        // Memaksa update DisplayMetrics pada context yang baru dibuat
        // Ini penting agar layout yang bergantung pada density (dp/sp) merender ukuran yang benar
        updateMetrics(context.resources, dpiValue, configuration)
        
        return context
    }

    fun applyDpi(context: Context, dpiValue: Int) {
        if (dpiValue <= 0) return
        val resources = context.resources
        val configuration = Configuration(resources.configuration)
        configuration.densityDpi = dpiValue
        
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
        
        // Memaksa update DisplayMetrics pada Application/Activity level
        updateMetrics(resources, dpiValue, configuration)
    }

    private fun updateMetrics(resources: Resources, dpiValue: Int, config: Configuration) {
        val metrics = resources.displayMetrics
        metrics.densityDpi = dpiValue
        metrics.density = dpiValue / 160f
        // Mempertahankan proporsi ukuran font (sp) dengan mengalikan fontScale
        metrics.scaledDensity = metrics.density * config.fontScale 
    }
}

