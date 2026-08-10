package com.v2ray.ang.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.v2ray.ang.AppConfig

/**
 * Helper to reduce the odds of CoreVpnService being killed in the background by
 * requesting exemption from standard Android Doze/App Standby battery optimizations.
 */
object BatteryOptimizationHelper {

    /**
     * @return true if the app is already exempt from battery optimizations
     * (or the API doesn't apply below M).
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Launches the system dialog asking the user to whitelist this app from
     * battery optimizations. Requires the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
     * permission to be declared in the manifest.
     *
     * @return true if an intent was launched, false if it couldn't be (e.g. already
     * exempt, unsupported API level, or no activity found to handle it).
     */
    /**
     * Builds the intent for the system "ignore battery optimizations" dialog,
     * or null if it can't be resolved (unsupported API level, already exempt,
     * or no activity to handle it — check openAppBatterySettingsFallback as a
     * manual fallback in that case).
     */
    fun buildIgnoreBatteryOptimizationsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        if (isIgnoringBatteryOptimizations(context)) return null

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        return if (intent.resolveActivity(context.packageManager) != null) intent else null
    }

    /**
     * Launches the system dialog asking the user to whitelist this app from
     * battery optimizations. Requires the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
     * permission to be declared in the manifest.
     *
     * @return true if an intent was launched, false if it couldn't be (e.g. already
     * exempt, unsupported API level, or no activity found to handle it).
     */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (isIgnoringBatteryOptimizations(context)) return false

        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            LogUtil.w(AppConfig.TAG, "requestIgnoreBatteryOptimizations: no activity found", e)
            openAppBatterySettingsFallback(context)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "requestIgnoreBatteryOptimizations failed", e)
            openAppBatterySettingsFallback(context)
        }
    }

    /**
     * Fallback: open the generic per-app "Battery" details screen when the direct
     * whitelist dialog isn't available on this device/ROM.
     */
    private fun openAppBatterySettingsFallback(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "openAppBatterySettingsFallback failed", e)
            false
        }
    }

}
