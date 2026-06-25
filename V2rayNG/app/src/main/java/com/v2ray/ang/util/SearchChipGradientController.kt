package com.v2ray.ang.util

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.handler.MmkvManager

object SearchChipGradientController {

    fun isEnabled(): Boolean {
        val gradientSwitchOn = MmkvManager.decodeSettingsBool(AppConfig.PREF_SEARCH_CHIP_GRADIENT, false)
        val weatherEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)
        val totalTrafficEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_TOTAL_TRAFFIC_CHIP, false)
        return gradientSwitchOn && (weatherEnabled || totalTrafficEnabled)
    }

    fun applyState(activity: AppCompatActivity, binding: ActivityMainBinding) {
        if (isEnabled()) applyGradientOn(activity, binding)
        else applyGradientOff(activity, binding)
    }

    private fun applyGradientOn(activity: AppCompatActivity, binding: ActivityMainBinding) {
        val colorStart = activity.getColorAttr("colorPrimary")
        val colorEnd = activity.getColorAttr("colorTertiary")
        val cornerRadiusPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 16f, activity.resources.displayMetrics
        )
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(colorStart, colorEnd)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
        }
        binding.layoutWeatherChip.background = RippleDrawable(
            ColorStateList.valueOf(activity.getColorAttr("android:colorControlHighlight")),
            gradient,
            null
        )

        val tintList = ColorStateList.valueOf(activity.getColorAttr("colorOnPrimary"))
        ImageViewCompat.setImageTintList(binding.ivWeatherIcon, tintList)
        ImageViewCompat.setImageTintList(binding.ivTotalTrafficIcon, tintList)
        binding.tvWeatherTemp.setTextColor(tintList.defaultColor)
        binding.tvTotalTraffic.setTextColor(tintList.defaultColor)
    }

    private fun applyGradientOff(activity: AppCompatActivity, binding: ActivityMainBinding) {
        binding.layoutWeatherChip.setBackgroundResource(R.drawable.bg_weather_chip)

        val tintList = ColorStateList.valueOf(activity.getColorAttr("colorOnSurfaceVariant"))
        ImageViewCompat.setImageTintList(binding.ivWeatherIcon, tintList)
        ImageViewCompat.setImageTintList(binding.ivTotalTrafficIcon, tintList)
        binding.tvWeatherTemp.setTextColor(tintList.defaultColor)
        binding.tvTotalTraffic.setTextColor(tintList.defaultColor)
    }
}
