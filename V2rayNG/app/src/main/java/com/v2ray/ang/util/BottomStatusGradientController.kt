package com.v2ray.ang.util

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import com.v2ray.ang.AppConfig
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.handler.MmkvManager

object BottomStatusGradientController {

    fun isEffectivelyEnabled(): Boolean {
        val gradientOn = MmkvManager.decodeSettingsBool(AppConfig.PREF_BOTTOM_STATUS_GRADIENT, false)
        val blurOn = MmkvManager.decodeSettingsBool(AppConfig.PREF_BLUR_BOTTOM_STATUS, false)
        return gradientOn && !blurOn
    }

    fun applyState(activity: AppCompatActivity, binding: ActivityMainBinding) {
        if (isEffectivelyEnabled()) applyGradientOn(activity, binding)
        else applyGradientOff(activity, binding)
    }

    private fun applyGradientOn(activity: AppCompatActivity, binding: ActivityMainBinding) {
        val colorStart = activity.getColorAttr("colorPrimary")
        val colorEnd   = activity.getColorAttr("colorTertiary")

        val cornerRadiusPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 28f, activity.resources.displayMetrics
        )
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(colorStart, colorEnd)
        ).apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
        }
        binding.cardBottomStatus.background = RippleDrawable(
            ColorStateList.valueOf(activity.getColorAttr("android:colorControlHighlight")),
            gradient,
            null
        )

        val onColor = activity.getColorAttr("colorOnPrimary")
        binding.tvIpState.setTextColor(onColor)
        binding.tvIpState.alpha = 0.85f
        binding.tvTestState.setTextColor(onColor)
        binding.fabNoBlur.backgroundTintList = ColorStateList.valueOf(
            activity.getColorAttr("colorOnPrimary")
        )
        binding.fabNoBlur.imageTintList = ColorStateList.valueOf(
            activity.getColorAttr("colorPrimary")
        )
    }

    private fun applyGradientOff(activity: AppCompatActivity, binding: ActivityMainBinding) {
        binding.cardBottomStatus.setCardBackgroundColor(
            activity.getColorAttr("colorPrimary")
        )
        val onColor = activity.getColorAttr("colorOnPrimary")
        binding.tvIpState.setTextColor(onColor)
        binding.tvIpState.alpha = 0.8f
        binding.tvTestState.setTextColor(onColor)
        binding.fabNoBlur.backgroundTintList = ColorStateList.valueOf(
            activity.getColorAttr("colorOnPrimary")
        )
        binding.fabNoBlur.imageTintList = ColorStateList.valueOf(
            activity.getColorAttr("colorPrimary")
        )
    }
}
