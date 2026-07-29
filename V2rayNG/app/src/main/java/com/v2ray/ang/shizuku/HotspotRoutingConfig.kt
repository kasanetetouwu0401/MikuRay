package com.v2ray.ang.shizuku

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.HotspotRoutingSnapshot
import com.v2ray.ang.service.HevTunnelConfig

/**
 * One validated engine payload for the tethering datapath: the rendered hev-socks5-tunnel
 * yaml plus the test-network addressing it was built against.
 *
 * The Shizuku tethering engine always forwards tethered-client traffic into the running
 * core's local SOCKS inbound — the same "socks" inbound normal per-app proxying and
 * browser-dialer traffic already goes through. Because of that, it works identically
 * whether the main VPN itself is using HEV tunnel or native Xray TUN internally; the
 * only real requirement is that the local SOCKS inbound exists at all (see
 * [com.v2ray.ang.handler.SettingsManager.isLocalSocksProxyEnabled]). There is no separate
 * native-Xray-TUN tethering engine here, unlike upstream's dual-engine design — one HEV
 * instance running in the Shizuku shell process covers both main-VPN modes.
 */
internal data class HotspotRoutingConfig(
    val hevYaml: String,
    val ipv4Address: String,
    val ipv6Address: String?,
    val profileName: String,
) {
    companion object {
        fun fromSnapshot(snapshot: HotspotRoutingSnapshot): HotspotRoutingConfig? {
            if (!snapshot.running || !snapshot.vpnMode || !snapshot.localProxyEnabled || snapshot.socksPort <= 0) return null
            val ipv6 = if (snapshot.ipv6Enabled) AppConfig.SHIZUKU_TUN_ADDR_V6 else null
            return HotspotRoutingConfig(
                hevYaml = HevTunnelConfig.buildFromSnapshot(snapshot),
                ipv4Address = AppConfig.SHIZUKU_TUN_ADDR_V4,
                ipv6Address = ipv6,
                profileName = snapshot.profileName,
            )
        }
    }
}
