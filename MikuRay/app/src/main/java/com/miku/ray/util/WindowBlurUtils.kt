package com.miku.ray.util

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
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import java.lang.ref.WeakReference
import java.util.WeakHashMap

object WindowBlurUtils {

    private const val BLUR_OVERLAY_ID = 2100000000
    const val SYSTEM_BLUR_DIM_AMOUNT = 0.24f

    private val activeWindows = mutableListOf<WeakReference<Window>>()
    private val backgroundWindowOf = WeakHashMap<Window, WeakReference<Window>>()

    fun applyWindowBlur(window: Window?, backgroundWindow: Window? = null) {
        if (window == null) return

        val isBlurEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_BLUR, false)
        if (!isBlurEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes?.dimAmount = 0.6f
            return
        }

        try {
            val context = window.context
            val blurRadius = MmkvManager.decodeSettingsInt(
                AppConfig.PREF_BLUR_RADIUS,
                AppConfig.DEFAULT_BLUR_RADIUS
            ).toFloat()
            if (shouldUseSystemBlur() && tryApplyNativeWindowBlur(window, blurRadius)) {
                removeFallbackBlurOverlay(window)
                registerActiveWindow(window)
                return
            }

            val resolvedBackground = backgroundWindow
                ?: topActiveWindow(excluding = window)
                ?: context.getActivity()?.window

            val decorView = resolvedBackground?.decorView as? ViewGroup ?: return

            decorView.findViewById<View>(BLUR_OVERLAY_ID)?.let {
                decorView.removeView(it)
            }

            val blurView = BlurView(context, null).apply {
                id = BLUR_OVERLAY_ID
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

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

            backgroundWindowOf[window] = WeakReference(resolvedBackground)
            registerActiveWindow(window)

            window.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}
                    override fun onViewDetachedFromWindow(v: View) {
                        decorView.removeView(blurView)
                        unregisterActiveWindow(window)
                        backgroundWindowOf.remove(window)
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
        if (shouldUseSystemBlur() && tryApplyNativeWindowBlur(window, radius)) {
            removeFallbackBlurOverlay(window)
            return
        }
        try {
            val background = backgroundWindowOf[window]?.get() ?: window.context.getActivity()?.window
            val decorView = background?.decorView as? ViewGroup ?: return
            val blurView = decorView.findViewById<BlurView>(BLUR_OVERLAY_ID) ?: return

            blurView.setBlurRadius(radius)
            blurView.setBlurRounds(rounds)

            blurView.invalidate()
            decorView.invalidate()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isSystemBlurAvailable(context: Context): Boolean {
        if (!shouldUseSystemBlur() || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return false
            windowManager.isCrossWindowBlurEnabled
        } catch (_: Exception) {
            false
        }
    }

    private fun shouldUseSystemBlur(): Boolean =
    MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_SYSTEM_BLUR, false)

    private fun tryApplyNativeWindowBlur(window: Window, radius: Float): Boolean {
        if (!isSystemBlurAvailable(window.context)) return false
        return try {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = radius.toInt().coerceAtLeast(0)
                dimAmount = SYSTEM_BLUR_DIM_AMOUNT
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun removeFallbackBlurOverlay(window: Window) {
        try {
            val background = backgroundWindowOf[window]?.get() ?: window.context.getActivity()?.window
            val decorView = background?.decorView as? ViewGroup ?: return
            decorView.findViewById<View>(BLUR_OVERLAY_ID)?.let(decorView::removeView)
            backgroundWindowOf.remove(window)
        } catch (_: Exception) {
        }
    }

    private fun registerActiveWindow(window: Window) {
        activeWindows.removeAll { it.get() == null || it.get() === window }
        activeWindows.add(WeakReference(window))
    }

    private fun unregisterActiveWindow(window: Window) {
        activeWindows.removeAll { it.get() == null || it.get() === window }
    }

    private fun topActiveWindow(excluding: Window): Window? {
        for (i in activeWindows.indices.reversed()) {
            val candidate = activeWindows[i].get()
            if (candidate != null && candidate !== excluding) return candidate
        }
        return null
    }
}

tailrec fun Context.getActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

fun MaterialAlertDialogBuilder.showBlur(backgroundWindow: Window? = null): androidx.appcompat.app.AlertDialog {
    val dialog = this.create()
    WindowBlurUtils.applyWindowBlur(dialog.window, backgroundWindow)
    dialog.show()
    return dialog
}

fun AlertDialog.Builder.showBlur(backgroundWindow: Window? = null): AlertDialog {
    val dialog = this.create()
    WindowBlurUtils.applyWindowBlur(dialog.window, backgroundWindow)
    dialog.show()
    return dialog
}
