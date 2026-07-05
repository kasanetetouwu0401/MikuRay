package com.v2ray.ang.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.qmdeve.blurview.widget.BlurView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

object WindowBlurUtils {

    private const val BLUR_OVERLAY_ID = 2100000000

    // Alpha (0-255) of the black overlay the custom BlurView draws on top of its
    // blur (see setOverlayColor below). Native blur has no such overlay of its
    // own, so we fake the same darkness using the window's dimAmount instead —
    // keeping both engines visually consistent.
    private const val CUSTOM_BLUR_OVERLAY_ALPHA = 120

    /**
     * Whether the device supports Android's own window-blur-behind API
     * (Window#setBackgroundBlurRadius, added in Android 12 / API 31).
     */
    fun isNativeBlurSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Whether the user has opted into using Android's native blur instead of
     * the bundled BlurView implementation. Falls back to the custom
     * implementation transparently if the device doesn't support it.
     */
    private fun useNativeBlur(): Boolean =
        isNativeBlurSupported() && MmkvManager.decodeSettingsBool(AppConfig.PREF_BLUR_USE_NATIVE, false)

    fun applyWindowBlur(window: Window?) {
        if (window == null) return

        val isBlurEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_BLUR, false)
        if (!isBlurEnabled) {
            clearNativeBlur(window)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes?.dimAmount = 0.6f
            return
        }

        if (useNativeBlur()) {
            try {
                applyNativeBlur(window)
                return
            } catch (e: Exception) {
                e.printStackTrace()
                // Fall through to the custom implementation if the native path fails.
            }
        }

        try {
            val context = window.context
            val activity = context.getActivity() ?: return
            val decorView = activity.window?.decorView as? ViewGroup ?: return

            clearNativeBlur(window)

            decorView.findViewById<View>(BLUR_OVERLAY_ID)?.let {
                decorView.removeView(it)
            }

            val blurView = BlurView(context, null).apply {
                id = BLUR_OVERLAY_ID
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                val blurRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_RADIUS, AppConfig.DEFAULT_BLUR_RADIUS).toFloat()
                val blurRounds = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_ROUNDS, AppConfig.DEFAULT_BLUR_ROUNDS)
                setBlurRadius(blurRadius)
                setBlurRounds(blurRounds)
                setOverlayColor(Color.argb(CUSTOM_BLUR_OVERLAY_ALPHA, 0, 0, 0))
                
                isClickable = false
                isFocusable = false
                elevation = 0f
                outlineProvider = null
            }

            decorView.addView(blurView)          
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

            window.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    decorView.removeView(blurView)
                    window.decorView.removeOnAttachStateChangeListener(this)
                }
            })
            
        } catch (e: Exception) {
            e.printStackTrace()
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes?.dimAmount = 0.6f
        }
    }

    fun updateWindowBlur(window: Window?, radius: Float, rounds: Int) {
        if (window == null) return

        if (useNativeBlur()) {
            try {
                setNativeBlurRadius(window, radius)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            val activity = window.context.getActivity() ?: return
            val decorView = activity.window?.decorView as? ViewGroup ?: return
            val blurView = decorView.findViewById<BlurView>(BLUR_OVERLAY_ID) ?: return
            
            blurView.setBlurRadius(radius)
            blurView.setBlurRounds(rounds)
            
            blurView.invalidate()
            decorView.invalidate()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Applies Android's own blur-behind effect to [window] using
     * Window#setBackgroundBlurRadius (API 31+). This replaces the custom
     * BlurView overlay entirely, so make sure any existing overlay is removed.
     */
    private fun applyNativeBlur(window: Window) {
        if (!isNativeBlurSupported()) return

        // Get rid of the custom overlay if it was previously attached (e.g. user
        // just switched the setting from custom -> native without recreating the dialog).
        val activity = window.context.getActivity()
        val decorView = activity?.window?.decorView as? ViewGroup
        decorView?.findViewById<View>(BLUR_OVERLAY_ID)?.let {
            decorView.removeView(it)
        }

        // dimAmount only actually renders when FLAG_DIM_BEHIND is set, so both
        // flags need to be on together to get "blurred + dimmed" like the custom engine.
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)

        val radius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_RADIUS, AppConfig.DEFAULT_BLUR_RADIUS).toFloat()
        val density = window.context.resources.displayMetrics.density
        val radiusPx = (radius * density).toInt().coerceIn(1, 250)

        // Match the darkness of the custom BlurView's black overlay so both
        // engines look the same. Written together with blurBehindRadius so
        // both take effect in a single relayout.
        val params = window.attributes
        params.blurBehindRadius = radiusPx
        params.dimAmount = CUSTOM_BLUR_OVERLAY_ALPHA / 255f
        window.attributes = params
    }

    private fun setNativeBlurRadius(window: Window, radius: Float) {
        if (!isNativeBlurSupported()) return
        val density = window.context.resources.displayMetrics.density
        val radiusPx = (radius * density).toInt().coerceIn(1, 250)

        // Window#setBackgroundBlurRadius() only takes effect once the window's
        // ViewRootImpl is already attached, so it silently no-ops when called
        // before the dialog is shown. Writing the field on the LayoutParams
        // directly (like dimAmount above) and re-assigning it is what actually
        // gets picked up both before *and* after the window is shown.
        val params = window.attributes
        params.blurBehindRadius = radiusPx
        window.attributes = params
    }

    private fun clearNativeBlur(window: Window) {
        if (!isNativeBlurSupported()) return
        try {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val params = window.attributes
            params.blurBehindRadius = 0
            params.dimAmount = 0f
            window.attributes = params
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

tailrec fun Context.getActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

fun MaterialAlertDialogBuilder.showBlur(): androidx.appcompat.app.AlertDialog {
    val dialog = this.create()
    WindowBlurUtils.applyWindowBlur(dialog.window)
    dialog.show()
    return dialog
}

fun AlertDialog.Builder.showBlur(): AlertDialog {
    val dialog = this.create()
    WindowBlurUtils.applyWindowBlur(dialog.window)
    dialog.show()
    return dialog
}
