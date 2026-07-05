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
                setOverlayColor(Color.argb(120, 0, 0, 0))
                
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

        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)

        val radius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_RADIUS, AppConfig.DEFAULT_BLUR_RADIUS).toFloat()
        setNativeBlurRadius(window, radius)

        // Dim it a touch on top of the blur so foreground content stays readable,
        // mirroring what the system does for its own blurred surfaces.
        window.attributes?.dimAmount = 0.1f
    }

    private fun setNativeBlurRadius(window: Window, radius: Float) {
        if (!isNativeBlurSupported()) return
        val density = window.context.resources.displayMetrics.density
        val radiusPx = (radius * density).toInt().coerceIn(1, 250)
        window.setBackgroundBlurRadius(radiusPx)
    }

    private fun clearNativeBlur(window: Window) {
        if (!isNativeBlurSupported()) return
        try {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.setBackgroundBlurRadius(0)
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
