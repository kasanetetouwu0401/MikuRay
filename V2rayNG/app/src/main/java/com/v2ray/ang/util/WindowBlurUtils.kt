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
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.qmdeve.blurview.widget.BlurView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import java.util.WeakHashMap
import java.util.function.Consumer

object WindowBlurUtils {

    private const val LEGACY_BLUR_OVERLAY_ID = 2100000000
    private const val DIM_AMOUNT_WITH_BLUR = 0.10f
    private const val DIM_AMOUNT_WITHOUT_BLUR = 0.60f

    private val systemBlurRegistrations = WeakHashMap<Window, SystemBlurRegistration>()

    private data class SystemBlurRegistration(
        val listener: Consumer<Boolean>,
        val attachListener: View.OnAttachStateChangeListener
    )

    fun applyWindowBlur(window: Window?) {
        if (window == null) return

        clearLegacyOverlay(window)

        val enabled = isWindowBlurEnabled()
        if (!enabled) {
            clearSystemBlur(window)
            applyDimFallback(window)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useSystemWindowBlurEnabled()) {
            applySystemBlur(window)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                clearSystemBlur(window)
            }
            applyLegacyBlur(window)
        }
    }

    fun updateWindowBlur(window: Window?, radius: Float, rounds: Int) {
        if (window == null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isWindowBlurEnabled() && useSystemWindowBlurEnabled()) {
            val blurRadius = radius.coerceIn(0f, 150f).toInt()
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                setBlurBehindRadius(blurRadius)
            }
            updateSystemWindowAppearance(window, window.windowManager.isCrossWindowBlurEnabled)
            return
        }

        try {
            val activity = window.context.getActivity() ?: return
            val decorView = activity.window?.decorView as? ViewGroup ?: return
            val blurView = decorView.findViewById<BlurView>(LEGACY_BLUR_OVERLAY_ID) ?: return

            blurView.setBlurRadius(radius.coerceIn(0f, 150f))
            blurView.setBlurRounds(rounds.coerceIn(1, 15))
            blurView.invalidate()
            decorView.invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isWindowBlurEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_BLUR, false)

    private fun useSystemWindowBlurEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_SYSTEM_WINDOW_BLUR, false)

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applySystemBlur(window: Window) {
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                setBlurBehindRadius(readBlurRadius())
            }

            val decorView = window.decorView
            val existing = systemBlurRegistrations.remove(window)
            existing?.let {
                window.windowManager.removeCrossWindowBlurEnabledListener(it.listener)
                decorView.removeOnAttachStateChangeListener(it.attachListener)
            }

            val listener = Consumer<Boolean> { enabled ->
                updateSystemWindowAppearance(window, enabled)
            }
            val attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    registerSystemBlurListener(window, listener)
                    updateSystemWindowAppearance(window, window.windowManager.isCrossWindowBlurEnabled)
                }

                override fun onViewDetachedFromWindow(view: View) {
                    window.windowManager.removeCrossWindowBlurEnabledListener(listener)
                    systemBlurRegistrations.remove(window)
                    view.removeOnAttachStateChangeListener(this)
                }
            }

            systemBlurRegistrations[window] = SystemBlurRegistration(listener, attachListener)
            decorView.addOnAttachStateChangeListener(attachListener)
            if (decorView.isAttachedToWindow) {
                registerSystemBlurListener(window, listener)
                updateSystemWindowAppearance(window, window.windowManager.isCrossWindowBlurEnabled)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            clearSystemBlur(window)
            applyDimFallback(window)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerSystemBlurListener(window: Window, listener: Consumer<Boolean>) {
        window.windowManager.addCrossWindowBlurEnabledListener(listener)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun updateSystemWindowAppearance(window: Window, blursEnabled: Boolean) {
        val shouldUseBlur = isWindowBlurEnabled() && blursEnabled
        if (shouldUseBlur) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(DIM_AMOUNT_WITH_BLUR)
        } else {
            applyDimFallback(window)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun clearSystemBlur(window: Window) {
        systemBlurRegistrations.remove(window)?.let {
            window.windowManager.removeCrossWindowBlurEnabledListener(it.listener)
            window.decorView.removeOnAttachStateChangeListener(it.attachListener)
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.apply {
            setBlurBehindRadius(0)
        }
    }

    private fun applyDimFallback(window: Window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(DIM_AMOUNT_WITHOUT_BLUR)
    }

    private fun applyLegacyBlur(window: Window) {
        try {
            val context = window.context
            val activity = context.getActivity() ?: return
            val decorView = activity.window?.decorView as? ViewGroup ?: return
            val blurRadius = readBlurRadius().toFloat()
            val blurRounds = MmkvManager.decodeSettingsInt(
                AppConfig.PREF_BLUR_ROUNDS,
                AppConfig.DEFAULT_BLUR_ROUNDS
            ).coerceIn(1, 15)

            clearLegacyOverlay(window)

            val blurView = BlurView(context, null).apply {
                id = LEGACY_BLUR_OVERLAY_ID
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
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
                override fun onViewAttachedToWindow(view: View) = Unit

                override fun onViewDetachedFromWindow(view: View) {
                    if (blurView.parent === decorView) decorView.removeView(blurView)
                    view.removeOnAttachStateChangeListener(this)
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            applyDimFallback(window)
        }
    }

    private fun clearLegacyOverlay(window: Window) {
        val activity = window.context.getActivity() ?: return
        val decorView = activity.window?.decorView as? ViewGroup ?: return
        decorView.findViewById<View>(LEGACY_BLUR_OVERLAY_ID)?.let { decorView.removeView(it) }
    }

    private fun readBlurRadius(): Int = MmkvManager.decodeSettingsInt(
        AppConfig.PREF_BLUR_RADIUS,
        AppConfig.DEFAULT_BLUR_RADIUS
    ).coerceIn(0, 150)
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
