package com.v2ray.ang.util

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.blur.LiveBlurView

object BlurBottomStatusController {

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_BLUR_BOTTOM_STATUS, false)

    fun applyState(activity: AppCompatActivity, binding: ActivityMainBinding) {
        if (isEnabled()) applyBlurOn(activity, binding)
        else applyBlurOff(activity, binding)
    }

    private fun applyBlurOn(activity: AppCompatActivity, binding: ActivityMainBinding) {
        val radius = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_BLUR_BOTTOM_RADIUS,
            AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS
        ).toFloat()

        val blurView = binding.blurBottomStatus as LiveBlurView
        blurView.attachTo(binding.root, exclude = binding.cardBottomStatus)
        blurView.overlayColor = activity.getColorAttr(R.attr.colorSurface).withAlpha(60)
        blurView.liquidGlassEnabled = true
        blurView.liquidCornerRadiusPx = activity.resources.getDimension(R.dimen.blur_bottom_status_corner_radius)
        blurView.blurRadiusPx = radius
        blurView.invalidate()

        binding.blurBottomStatus.visibility = View.VISIBLE
        binding.cardBottomStatus.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.tvIpState.setTextColor(
            activity.getColorAttr(R.attr.colorOnSurfaceVariant)
        )
        binding.tvTestState.setTextColor(
            activity.getColorAttr(R.attr.colorOnSurface)
        )
        binding.fab.visibility = View.VISIBLE
        binding.fabNoBlur.visibility = View.GONE
    }

    private fun applyBlurOff(activity: AppCompatActivity, binding: ActivityMainBinding) {
        binding.blurBottomStatus.visibility = View.GONE
        binding.cardBottomStatus.setCardBackgroundColor(
            activity.getColorAttr(R.attr.colorPrimary)
        )
        val textColorOnPrimary = activity.getColorAttr(R.attr.colorOnPrimary)
        binding.tvIpState.setTextColor(textColorOnPrimary)
        binding.tvIpState.alpha = 0.8f
        binding.tvTestState.setTextColor(textColorOnPrimary)
        binding.fab.visibility = View.GONE
        binding.fabNoBlur.visibility = View.VISIBLE
    }

    private fun Int.withAlpha(alpha: Int): Int =
        (this and 0x00FFFFFF) or (alpha shl 24)
}
