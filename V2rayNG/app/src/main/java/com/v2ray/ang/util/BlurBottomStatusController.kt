package com.v2ray.ang.util

import android.os.Build
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.handler.MmkvManager

object BlurBottomStatusController {

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_BLUR_BOTTOM_STATUS, false)

    fun applyState(activity: AppCompatActivity, binding: ActivityMainBinding) {
        if (isEnabled()) applyBlurOn(activity, binding)
        else applyBlurOff(activity, binding)
    }

    private fun dpToPx(activity: AppCompatActivity, dp: Float): Float =
        dp * activity.resources.displayMetrics.density

    private fun applyBlurOn(activity: AppCompatActivity, binding: ActivityMainBinding) {
        val radius = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_BLUR_BOTTOM_RADIUS,
            AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS
        ).toFloat()
        val rounds = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_BLUR_BOTTOM_ROUNDS,
            AppConfig.DEFAULT_BLUR_BOTTOM_ROUNDS
        )
        val glassEdgeDp = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_BLUR_BOTTOM_GLASS_EDGE_AMOUNT,
            AppConfig.DEFAULT_BLUR_BOTTOM_GLASS_EDGE_AMOUNT
        ).toFloat()

        binding.blurBottomStatus.setBlurRadius(radius)
        binding.blurBottomStatus.setBlurRounds(rounds)

        // The full InstallerX "glass" recipe: layered theme-adaptive tint everywhere, plus
        // (where the API allows it) vibrancy and the rounded-rect lens refraction. This is
        // the one bounded, pill-shaped glass surface in the app, so it's the natural place
        // to show the "glass edge" — a full-screen backdrop blur has no edge to bend.
        val cornerRadiusPx = dpToPx(activity, AppConfig.DEFAULT_BLUR_BOTTOM_CORNER_RADIUS_DP.toFloat())
        binding.blurBottomStatus.setGlassCornerRadiusPx(cornerRadiusPx)
        binding.blurBottomStatus.setGlassTint(GlassTintDefaults.forCurrentTheme(activity))
        binding.blurBottomStatus.setVibrancy(1.2f)
        binding.blurBottomStatus.setLensRefraction(
            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            refractionHeightPx = dpToPx(activity, glassEdgeDp),
            refractionAmountPx = dpToPx(activity, glassEdgeDp),
        )
        binding.blurBottomStatus.invalidate()

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
}
