package com.v2ray.ang.dto

import android.graphics.drawable.Drawable

/**
 * Represents aggregated network traffic usage for a single installed app.
 *
 * [rxBytes]/[txBytes] are device-wide totals reported by [android.app.usage.NetworkStatsManager]
 * for this app's UID (mobile + Wi-Fi combined), not traffic routed through MikuRay's proxy core.
 */
data class AppTrafficInfo(
    val uid: Int,
    val appName: String,
    val packageName: String,
    val appIcon: Drawable,
    val isSystemApp: Boolean,
    var rxBytes: Long = 0L,
    var txBytes: Long = 0L,
    var isActiveNow: Boolean = false
) {
    val totalBytes: Long get() = rxBytes + txBytes
}
