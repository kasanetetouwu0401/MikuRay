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

    fun getConnectStartTime(): Long =
        MmkvManager.decodeSettingsLong(AppConfig.PREF_VPN_CONNECT_START_TIME, 0L)

    fun isConnected(): Boolean = getConnectStartTime() > 0L

    fun reconcileWithRunningState() {
        val startTime = getConnectStartTime()
        if (startTime > 0L && !CoreServiceManager.isRunning()) {
            markConnectStopped()
        }
    }
}
