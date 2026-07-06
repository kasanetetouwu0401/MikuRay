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

    // Gunakan TAG alih-alih hardcoded ID untuk menghindari bentrok / crash UI
    private const val BLUR_OVERLAY_TAG = "window_blur_overlay_tag"

    fun applyWindowBlur(window: Window?) {
        if (window == null) return
        
        val isBlurEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_BLUR, false)
        if (!isBlurEnabled) {
            applyDefaultDim(window)
            return
        }

        try {
            val context = window.context
            val activity = context.getActivity() ?: return
            val activityDecorView = activity.window?.decorView as? ViewGroup ?: return
            
            // Hapus blur lama jika ada (mencegah duplikasi view jika dialog bertumpuk)
            removeBlurOverlay(activityDecorView)

            val blurView = BlurView(context, null).apply {
                tag = BLUR_OVERLAY_TAG // Set tag
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

            activityDecorView.addView(blurView)          
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

            // Listener ini aman karena dipanggil SETELAH dialog.show()
            window.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    removeBlurOverlay(activityDecorView)
                    v.removeOnAttachStateChangeListener(this)
                }
            })
            
        } catch (e: Exception) {
            e.printStackTrace()
            applyDefaultDim(window)
        }
    }

    private fun applyDefaultDim(window: Window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes?.apply {
            dimAmount = 0.6f
        }
    }

    private fun removeBlurOverlay(decorView: ViewGroup) {
        decorView.findViewWithTag<View>(BLUR_OVERLAY_TAG)?.let {
            // Pastikan view masih berada di dalam parent sebelum dihapus
            if (it.parent === decorView) {
                decorView.removeView(it)
            }
        }
    }

    fun updateWindowBlur(window: Window?, radius: Float, rounds: Int) {
        if (window == null) return
        try {
            val activity = window.context.getActivity() ?: return
            val decorView = activity.window?.decorView as? ViewGroup ?: return
            val blurView = decorView.findViewWithTag<BlurView>(BLUR_OVERLAY_TAG) ?: return
            
            blurView.setBlurRadius(radius)
            blurView.setBlurRounds(rounds)
            
            blurView.invalidate() // Cukup invalidate BlurView, tidak perlu seluruh decorView
            
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
    dialog.show() // PENTING: Tampilkan dialog dulu
    WindowBlurUtils.applyWindowBlur(dialog.window) // Baru apply blur setelah window decorView terbentuk
    return dialog
}

fun AlertDialog.Builder.showBlur(): AlertDialog {
    val dialog = this.create()
    dialog.show() // PENTING: Tampilkan dialog dulu
    WindowBlurUtils.applyWindowBlur(dialog.window) // Baru apply blur setelah window decorView terbentuk
    return dialog
}

