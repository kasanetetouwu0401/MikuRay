package com.v2ray.ang.util

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.telephony.TelephonyManager
import com.v2ray.ang.dto.AppTrafficInfo
import java.util.Locale

/**
 * Reads device-wide, per-app network traffic via [NetworkStatsManager].
 *
 * This reflects total traffic for each app's UID across all network interfaces
 * (mobile + Wi-Fi), as reported by the system. It does not (and cannot) isolate
 * traffic that specifically passed through MikuRay's proxy core, since once the
 * VPN tunnel is active, the TUN interface only sees this app's own UID.
 */
object AppTrafficUtil {

    /**
     * Whether the "Usage access" special permission has been granted to this app.
     * Required by [NetworkStatsManager] for querying stats of apps other than the caller.
     */
    fun hasUsageAccessPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Opens the system "Usage access" settings screen so the user can grant the permission. */
    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Queries total rx/tx bytes per installed app within [sinceMillis, untilMillis], merging
     * mobile and Wi-Fi networks.
     *
     * @return map of uid -> Pair(rxBytes, txBytes)
     */
    @SuppressLint("MissingPermission")
    fun queryTrafficByUid(
        context: Context,
        sinceMillis: Long,
        untilMillis: Long = System.currentTimeMillis()
    ): Map<Int, Pair<Long, Long>> {
        val result = HashMap<Int, Pair<Long, Long>>()
        if (!hasUsageAccessPermission(context)) return result

        val statsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return result

        fun accumulate(networkType: Int, subscriberId: String?) {
            try {
                val bucket = NetworkStats.Bucket()
                val stats = statsManager.querySummary(networkType, subscriberId, sinceMillis, untilMillis)
                stats.use {
                    while (it.hasNextBucket()) {
                        it.getNextBucket(bucket)
                        val uid = bucket.uid
                        val existing = result[uid] ?: (0L to 0L)
                        result[uid] = (existing.first + bucket.rxBytes) to (existing.second + bucket.txBytes)
                    }
                }
            } catch (e: Exception) {
                LogUtil.e("AppTrafficUtil", "querySummary failed for networkType=$networkType", e)
            }
        }

        accumulate(ConnectivityManager.TYPE_WIFI, null)

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val subscriberId = try {
            @Suppress("DEPRECATION")
            telephonyManager?.subscriberId
        } catch (e: SecurityException) {
            null
        }
        accumulate(ConnectivityManager.TYPE_MOBILE, subscriberId)

        return result
    }

    /**
     * Loads installed apps that are relevant for traffic display: apps requesting INTERNET
     * permission, plus this app itself.
     */
    fun loadCandidateApps(context: Context): List<AppTrafficInfo> {
        val packageManager = context.packageManager
        val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val list = ArrayList<AppTrafficInfo>()

        for (pkg in packages) {
            val applicationInfo = pkg.applicationInfo ?: continue
            val requestedPermissions = pkg.requestedPermissions
            val hasInternet = requestedPermissions?.contains(android.Manifest.permission.INTERNET) == true
            if (!hasInternet && applicationInfo.uid != Process.myUid()) continue

            val appName = applicationInfo.loadLabel(packageManager).toString()
            val appIcon = applicationInfo.loadIcon(packageManager) ?: continue
            val isSystemApp = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM > 0

            list.add(
                AppTrafficInfo(
                    uid = applicationInfo.uid,
                    appName = appName,
                    packageName = pkg.packageName,
                    appIcon = appIcon,
                    isSystemApp = isSystemApp
                )
            )
        }
        return list
    }

    /** Formats a byte count into a human-readable string, e.g. "12.34 MB". */
    fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var i = 0
        while (size >= 1024 && i < units.size - 1) {
            size /= 1024
            i++
        }
        return String.format(Locale.getDefault(), "%.2f %s", size, units[i])
    }

    /**
     * Snapshot of current per-UID rx+tx totals (lifetime device counters since boot), used to
     * compute deltas across a short polling interval for "currently active" detection.
     */
    fun snapshotUidTraffic(uids: Set<Int>): Map<Int, Long> {
        val result = HashMap<Int, Long>(uids.size)
        val unsupported = TrafficStats.UNSUPPORTED.toLong()
        for (uid in uids) {
            val rx = TrafficStats.getUidRxBytes(uid)
            val tx = TrafficStats.getUidTxBytes(uid)
            if (rx == unsupported && tx == unsupported) continue
            val rxSafe = if (rx == unsupported) 0L else rx
            val txSafe = if (tx == unsupported) 0L else tx
            result[uid] = rxSafe + txSafe
        }
        return result
    }

    /** Whether the device currently has any active network connection at all. */
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
