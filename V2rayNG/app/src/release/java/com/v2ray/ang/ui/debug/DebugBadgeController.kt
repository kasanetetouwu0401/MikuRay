package com.v2ray.ang.ui.debug

import android.app.Activity
import android.view.ViewGroup

/**
 * No-op release counterpart of the debug-only DebugBadgeController.
 * Keeps the call site in MainActivity build-type-agnostic without
 * pulling the SlantedTextView dependency into release builds.
 */
object DebugBadgeController {

    fun attach(activity: Activity, root: ViewGroup) {
        // Intentionally empty: no debug ribbon in release builds.
    }
}
