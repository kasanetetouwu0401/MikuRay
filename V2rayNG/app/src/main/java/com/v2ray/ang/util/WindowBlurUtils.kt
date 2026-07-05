package com.v2ray.ang.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
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

    fun applyWindowBlur(window: Window?, targetId: Int = android.R.id.content) {
        if (window == null) return
        
        val isBlurEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_BLUR, false)
        if (!isBlurEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes?.dimAmount = 0.6f
            return
        }

        try {
            val dialogContext = window.context
            val activity = dialogContext.getActivity() ?: return
            val targetView = activity.findViewById<ViewGroup>(targetId) ?: return
            
            targetView.findViewById<View>(BLUR_OVERLAY_ID)?.let {
                targetView.removeView(it)
            }

            val blurView = BlurView(activity, null).apply {
                id = BLUR_OVERLAY_ID
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                val blurRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_RADIUS, AppConfig.DEFAULT_BLUR_RADIUS).toFloat()
                val blurRounds = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_ROUNDS, AppConfig.DEFAULT_BLUR_ROUNDS)
                
                setBlurRadius(if (blurRadius > 0f) blurRadius else 10f)
                setBlurRounds(if (blurRounds > 0) blurRounds else 1)
                setOverlayColor(Color.argb(120, 0, 0, 0))
                
                isClickable = false
                isFocusable = false
                elevation = 0f
                outlineProvider = null
            }

            targetView.addView(blurView)          
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

            window.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                
                override fun onViewDetachedFromWindow(v: View) {
                    targetView.removeView(blurView)
                    window.decorView.removeOnAttachStateChangeListener(this)
                }
            })
            
        } catch (e: Exception) {
            e.printStackTrace()
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes?.dimAmount = 0.6f
        }
    }

    fun updateWindowBlur(window: Window?, targetId: Int = android.R.id.content, radius: Float, rounds: Int) {
        if (window == null) return
        
        try {
            val activity = window.context.getActivity() ?: return
            val targetView = activity.findViewById<ViewGroup>(targetId) ?: return
            val blurView = targetView.findViewById<BlurView>(BLUR_OVERLAY_ID) ?: return
            
            blurView.setBlurRadius(if (radius > 0f) radius else 10f)
            blurView.setBlurRounds(if (rounds > 0) rounds else 1)
            
            blurView.invalidate()
            targetView.invalidate()
            
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

fun MaterialAlertDialogBuilder.showBlur(targetId: Int = android.R.id.content): AlertDialog {
    val dialog = this.create()
    WindowBlurUtils.applyWindowBlur(dialog.window, targetId)
    dialog.show()
    return dialog
}

fun AlertDialog.Builder.showBlur(targetId: Int = android.R.id.content): AlertDialog {
    val dialog = this.create()
    WindowBlurUtils.applyWindowBlur(dialog.window, targetId)
    dialog.show()
    return dialog
}
