package com.v2ray.ang.util

import android.content.Context
import android.content.res.Configuration

object DPIController {

    fun wrapWithDpi(base: Context, dpiValue: Int): Context {
        if (dpiValue <= 0) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.densityDpi = dpiValue
        return base.createConfigurationContext(configuration)
    }
}
