package com.miku.ray.core

import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager

object CoreConnectionTracker {

    fun markConnectStarted() {
        MmkvManager.encodeSettings(AppConfig.PREF_VPN_CONNECT_START_TIME, System.currentTimeMillis())
    }

    fun markConnectStopped() {
        MmkvManager.encodeSettings(AppConfig.PREF_VPN_CONNECT_START_TIME, 0L)
    }

    fun getConnectStartTime(): Long {
        val startTime = MmkvManager.decodeSettingsLong(AppConfig.PREF_VPN_CONNECT_START_TIME, 0L)
        if (startTime > 0L && !CoreServiceManager.isRunning()) {
            markConnectStopped()
            return 0L
        }
        return startTime
    }

    fun isConnected(): Boolean = getConnectStartTime() > 0L
}
