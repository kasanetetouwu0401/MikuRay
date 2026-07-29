package com.v2ray.ang.dto

import java.io.Serializable

/**
 * Exact launch parameters captured by the running core process (`:RunSoLibV2RayDaemon`)
 * for Shizuku tethering.
 *
 * This is intentionally different from rebuilding a configuration from current settings:
 * the selected profile or preferences may have changed after the running core was started.
 * Sending this snapshot over the existing broadcast channel lets the main-process tethering
 * controller mirror exactly what the daemon process is doing right now.
 */
data class HotspotRoutingSnapshot(
    val running: Boolean = false,
    /** True whenever VPN mode is active — independent of whether it uses HEV or native Xray TUN. */
    val vpnMode: Boolean = false,
    /** True if the running core's local SOCKS inbound exists (see [com.v2ray.ang.handler.SettingsManager.isLocalSocksProxyEnabled]). Tethering needs this regardless of TUN mechanism, since it always forwards into that inbound. */
    val localProxyEnabled: Boolean = false,
    val profileName: String = "",
    val ipv6Enabled: Boolean = false,
    val vpnDnsServers: List<String> = emptyList(),
    val socksPort: Int = 0,
    val socksUsername: String? = null,
    val socksPassword: String? = null,
    val mtu: Int = 0,
    val hevTcpTimeoutSeconds: Int = 0,
    val hevUdpTimeoutSeconds: Int = 0,
    val hevLogLevel: String = "warn",
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
