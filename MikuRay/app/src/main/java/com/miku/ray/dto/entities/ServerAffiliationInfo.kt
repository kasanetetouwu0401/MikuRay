package com.miku.ray.dto.entities

data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    var uplinkTotal: Long = 0L,
    var downlinkTotal: Long = 0L,
    var countryCode: String? = null,
    /** Download speed in bytes per second; 0 = unset, negative = failed */
    var downloadSpeedBps: Long = 0L,
    /** Upload speed in bytes per second; 0 = unset, negative = failed */
    var uploadSpeedBps: Long = 0L,
) {
    fun getTestDelayString(): String {
        if (testDelayMillis <= 0L) {
            return ""
        }
        return testDelayMillis.toString() + "ms"
    }

    fun getSpeedString(): String {
        if (downloadSpeedBps == 0L && uploadSpeedBps == 0L) return ""
        if (downloadSpeedBps < 0L && uploadSpeedBps < 0L) return "fail"
        val down = if (downloadSpeedBps > 0L) formatSpeed(downloadSpeedBps) else "-"
        val up = if (uploadSpeedBps > 0L) formatSpeed(uploadSpeedBps) else "-"
        // Match traffic chip style: ↑ upload  ↓ download
        return "↑ $up  ↓ $down"
    }

    companion object {
        fun formatSpeed(bps: Long): String {
            if (bps <= 0L) return "-"
            val mbps = bps * 8.0 / 1_000_000.0
            return when {
                mbps >= 100 -> "%.0fMbps".format(mbps)
                mbps >= 10 -> "%.1fMbps".format(mbps)
                mbps >= 1 -> "%.2fMbps".format(mbps)
                else -> {
                    val kbps = bps * 8.0 / 1_000.0
                    if (kbps >= 1) "%.0fKbps".format(kbps) else "%.0fbps".format(bps * 8.0)
                }
            }
        }
    }
}
