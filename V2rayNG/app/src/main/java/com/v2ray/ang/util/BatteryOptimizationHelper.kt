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
 * Helper to reduce the odds of CoreVpnService being killed in the background by:
 *  1. Requesting exemption from standard Android Doze/App Standby battery optimizations.
 *  2. Deep-linking the user to vendor-specific "autostart" / "background activity"
 *     screens (MIUI, ColorOS, FuntouchOS/OriginOS, EMUI/MagicOS, One UI, etc.) which
 *     are NOT covered by the standard Android battery optimization API.
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

    /**
     * Attempts to open the manufacturer-specific "autostart" / "background activity"
     * management screen. These are separate from the standard Android battery
     * optimization API and are the main reason VPN apps get killed on MIUI, ColorOS,
     * FuntouchOS/OriginOS, EMUI/MagicOS, etc. Falls back to the app's system settings
     * page if no known vendor screen can be opened.
     *
     * @return true if some settings screen was successfully launched.
     */
    fun openAutostartSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val pkg = context.packageName

        val candidates = mutableListOf<Intent>()

        when {
            manufacturer.contains("xiaomi") -> {
                candidates += componentIntent(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                candidates += componentIntent(
                    "com.miui.securitycenter",
                    "com.miui.securitycenter.permission.AppPermissionsEditorActivity"
                )
            }

            manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme") -> {
                candidates += componentIntent(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
                candidates += componentIntent(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                )
                candidates += componentIntent(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                )
            }

            manufacturer.contains("vivo") -> {
                candidates += componentIntent(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
                candidates += componentIntent(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                )
            }

            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                candidates += componentIntent(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                candidates += componentIntent(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            }

            manufacturer.contains("samsung") -> {
                candidates += componentIntent(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            }

            manufacturer.contains("asus") -> {
                candidates += componentIntent(
                    "com.asus.mobilemanager",
                    "com.asus.mobilemanager.autostart.AutoStartActivity"
                )
            }
        }

        // Generic candidate: request ignore battery optimizations as a decent fallback
        for (intent in candidates) {
            if (tryStartActivity(context, intent)) return true
        }

        // Nothing vendor-specific worked; fall back to the app details settings page.
        return openAppBatterySettingsFallback(context)
    }

    private fun componentIntent(pkg: String, cls: String): Intent {
        return Intent().apply {
            component = android.content.ComponentName(pkg, cls)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            if (intent.resolveActivity(context.packageManager) == null) return false
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** True if this device's manufacturer is known to aggressively kill background apps. */
    fun isAggressiveBatteryManufacturer(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return listOf(
            "xiaomi", "oppo", "oneplus", "realme", "vivo",
            "huawei", "honor", "samsung", "asus"
        ).any { manufacturer.contains(it) }
    }
}
