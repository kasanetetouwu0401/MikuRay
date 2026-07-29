package com.v2ray.ang.shizuku

import android.content.Context
import android.os.Build

/**
 * Small helpers for code that runs inside the Shizuku UserService process (shell UID).
 *
 * The UserService is created from the app's own APK/classloader via
 * `Shizuku.bindUserService`, so `Context.getSystemService` works normally for public
 * services. Hidden framework classes (`TestNetworkManager`, the pre-36 `TetheringManager`
 * surface) are reached through reflection, isolated here and in [TetheringPlatformCompat]
 * so the rest of the tethering code stays free of `@SuppressLint("PrivateApi")` noise.
 */
internal object ShellContextCompat {

    /** `Context.TEST_NETWORK_SERVICE` isn't a public constant pre-API 31 on all sources. */
    const val TEST_NETWORK_SERVICE = "test_network"

    fun getTestNetworkManager(context: Context): Any? {
        return try {
            context.getSystemService(TEST_NETWORK_SERVICE)
        } catch (_: Throwable) {
            null
        }
    }

    fun getTetheringManager(context: Context): Any? {
        return try {
            context.getSystemService("tethering")
        } catch (_: Throwable) {
            null
        }
    }

    fun getConnectivityManager(context: Context): android.net.ConnectivityManager? {
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
    }

    /**
     * `WifiManager.getWifiApState()` is hidden but stable across AOSP versions.
     * `WIFI_AP_STATE_ENABLED == 13`. Used to detect a hotspot that was already running
     * *before* protected routing was armed, since Android does not retroactively
     * reconsider tethering's upstream once it has already picked one.
     */
    fun isWifiApEnabled(context: Context): Boolean {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) ?: return false
            val method = wm.javaClass.getMethod("getWifiApState")
            (method.invoke(wm) as? Int) == 13
        } catch (_: Throwable) {
            false
        }
    }

    val isApi36OrNewer: Boolean get() = Build.VERSION.SDK_INT >= 36
    val isApi33OrNewer: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
