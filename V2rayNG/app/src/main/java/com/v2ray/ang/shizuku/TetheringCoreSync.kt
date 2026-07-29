package com.v2ray.ang.shizuku

import android.content.Context
import android.content.Intent
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.HotspotRoutingSnapshot
import com.v2ray.ang.dto.HotspotRoutingSync
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import java.util.UUID

/**
 * Lives in the `:RunSoLibV2RayDaemon` process alongside [com.v2ray.ang.core.CoreServiceManager].
 *
 * Captures an exact snapshot of the running core's launch parameters and broadcasts
 * authenticated lifecycle events to [ShizukuRoutingSyncReceiver] in the main process,
 * which forwards them to the bound Shizuku UserService. Only takes effect when Shizuku
 * tethering has been armed by the user in the Tethering screen (a stored non-blank sync
 * token); otherwise every call here is a cheap no-op so normal core start/stop is
 * unaffected when the feature isn't in use.
 */
object TetheringCoreSync {

    /**
     * Represents this daemon process (`:RunSoLibV2RayDaemon`) to the shell UserService.
     * Recreated each time the core starts so the previous session's death (if any) can't
     * be confused with the current one; the shell service `linkToDeath`s this Binder and
     * fails closed if the daemon process disappears without a clean stop.
     */
    private var lease: ICoreTetheringLease.Stub? = null

    /** Call before attempting to start the core, so a fresh token exists for this session. */
    fun onStarting() {
        if (!isArmed()) return
        MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, UUID.randomUUID().toString())
        lease = object : ICoreTetheringLease.Stub() {
            override fun ping() {}
        }
    }

    fun onStarted(context: Context, profileName: String, usesHevTun: Boolean) {
        if (!isArmed()) return
        val snapshot = buildSnapshot(profileName, usesHevTun)
        broadcast(context, HotspotRoutingSync(token(), HotspotRoutingSync.EVENT_CORE_STARTED, snapshot))
    }

    fun onStartFailed(context: Context, detail: String) {
        if (!isArmed()) return
        broadcast(context, HotspotRoutingSync(token(), HotspotRoutingSync.EVENT_CORE_START_FAILED, detail = detail))
    }

    fun onStopping(context: Context) {
        if (!isArmed()) return
        broadcast(context, HotspotRoutingSync(token(), HotspotRoutingSync.EVENT_CORE_STOPPING))
    }

    /** Called on MSG_QUERY_HOTSPOT_CONFIG, e.g. when the Tethering screen is (re)opened. */
    fun sendCurrentSnapshot(context: Context?, isRunning: Boolean) {
        if (context == null || !isArmed()) return
        if (!isRunning) {
            broadcast(context, HotspotRoutingSync(token(), HotspotRoutingSync.EVENT_CORE_STOPPING))
            return
        }
        val profileName = MmkvManager.decodeServerConfig(MmkvManager.getSelectServer() ?: "")?.remarks.orEmpty()
        val snapshot = buildSnapshot(profileName, SettingsManager.isUsingHevTun())
        broadcast(context, HotspotRoutingSync(token(), HotspotRoutingSync.EVENT_CORE_STARTED, snapshot))
    }

    /** Nudges the main-process controller to rebind a fresh Shizuku Binder after it returns to foreground. */
    fun onAppForegrounded(context: Context?) {
        if (context == null || !isArmed()) return
        val intent = Intent(ShizukuRoutingSyncReceiver.ACTION_SYNC).apply {
            setPackage(context.packageName)
            putExtra(ShizukuRoutingSyncReceiver.EXTRA_FOREGROUND_NUDGE, true)
        }
        context.sendBroadcast(intent)
    }

    fun clear() {
        MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, "")
    }

    private fun isArmed(): Boolean = token().isNotBlank()

    private fun token(): String = MmkvManager.decodeSettingsString(AppConfig.PREF_SHIZUKU_SYNC_TOKEN).orEmpty()

    private fun buildSnapshot(profileName: String, usesHevTun: Boolean): HotspotRoutingSnapshot {
        val timeouts = MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT)
            ?.split(',')
            ?.map { it.trim().toIntOrNull() }
            ?: emptyList()
        return HotspotRoutingSnapshot(
            running = true,
            vpnMode = SettingsManager.isVpnMode() && usesHevTun,
            profileName = profileName,
            ipv6Enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED, false),
            vpnDnsServers = SettingsManager.getVpnDnsServers(),
            socksPort = SettingsManager.getSocksPort(),
            socksUsername = SettingsManager.getSocksUsername(),
            socksPassword = SettingsManager.getSocksPassword(),
            mtu = SettingsManager.getVpnMtu(),
            hevTcpTimeoutSeconds = timeouts.getOrNull(0) ?: 300,
            hevUdpTimeoutSeconds = timeouts.getOrNull(1) ?: 60,
            hevLogLevel = MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_LOGLEVEL) ?: "warn",
        )
    }

    private fun broadcast(context: Context, sync: HotspotRoutingSync) {
        try {
            val activeLease = lease ?: object : ICoreTetheringLease.Stub() {
                override fun ping() {}
            }.also { lease = it }
            val leaseBundle = android.os.Bundle().apply {
                putBinder(ShizukuRoutingSyncReceiver.EXTRA_LEASE, activeLease.asBinder())
            }
            val intent = Intent(ShizukuRoutingSyncReceiver.ACTION_SYNC).apply {
                setPackage(context.packageName)
                putExtra(ShizukuRoutingSyncReceiver.EXTRA_SYNC, sync)
                putExtra(ShizukuRoutingSyncReceiver.EXTRA_LEASE_BUNDLE, leaseBundle)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "TetheringCoreSync: failed to broadcast sync event", e)
        }
    }
}
