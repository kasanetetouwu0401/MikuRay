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
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

object WindowBlurUtils {

    private const val BLUR_OVERLAY_ID = 2100000000
    private const val BLUR_TARGET_ID = 2100000001

    /**
     * Ensures the activity's real content view is wrapped in a [BlurTarget], which the
     * BlurView library needs as a snapshot source. Safe to call multiple times; the wrap
     * only happens once per activity.
     */
    fun getOrCreateBlurTarget(activity: Activity): BlurTarget? {
        val contentFrame = activity.window?.decorView?.findViewById<ViewGroup>(android.R.id.content) ?: return null

        contentFrame.findViewById<BlurTarget>(BLUR_TARGET_ID)?.let { return it }

        if (contentFrame.childCount == 0) return null
        val existingChild = contentFrame.getChildAt(0)
        val childParams = existingChild.layoutParams
        contentFrame.removeViewAt(0)

        val blurTarget = BlurTarget(contentFrame.context).apply {
            id = BLUR_TARGET_ID
        }
        blurTarget.addView(existingChild, childParams)
        contentFrame.addView(
            blurTarget,
            0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        return blurTarget
    }

    /**
     * Adds a full-screen blur overlay on top of the activity's own content, sitting behind
     * whatever transparent-background dialog window is shown above it. This mirrors the
     * previous implementation: the overlay lives in the Activity's window, not the dialog's.
     */
    fun applyWindowBlur(window: Window?) {
        if (window == null) return

        val isBlurEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_BLUR, false)
        if (!isBlurEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes?.dimAmount = 0.6f
            return
        }

        try {
            val context = window.context
            val activity = context.getActivity() ?: return
            val activityDecorView = activity.window?.decorView as? ViewGroup ?: return
            val blurTarget = getOrCreateBlurTarget(activity) ?: return

            activityDecorView.findViewById<View>(BLUR_OVERLAY_ID)?.let {
                (it.parent as? ViewGroup)?.removeView(it)
            }

            val blurRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_RADIUS, AppConfig.DEFAULT_BLUR_RADIUS).toFloat()

            val blurView = BlurView(context).apply {
                id = BLUR_OVERLAY_ID
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setOverlayColor(Color.argb(120, 0, 0, 0))

                isClickable = false
                isFocusable = false
                elevation = 0f
                outlineProvider = null
            }

            activityDecorView.addView(blurView)
            blurView.setupWith(blurTarget)
                .setBlurRadius(blurRadius)

            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

            window.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    activityDecorView.removeView(blurView)
                    window.decorView.removeOnAttachStateChangeListener(this)
                }
            })

        } catch (e: Exception) {
            e.printStackTrace()
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes?.dimAmount = 0.6f
        }
    }

    fun updateWindowBlur(window: Window?, radius: Float) {
        if (window == null) return
        try {
            val activity = window.context.getActivity() ?: return
            val activityDecorView = activity.window?.decorView as? ViewGroup ?: return
            val blurView = activityDecorView.findViewById<BlurView>(BLUR_OVERLAY_ID) ?: return

            blurView.setBlurRadius(radius)
            blurView.invalidate()

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
