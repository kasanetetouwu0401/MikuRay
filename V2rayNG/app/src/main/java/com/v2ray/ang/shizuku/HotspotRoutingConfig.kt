package com.v2ray.ang.shizuku

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.HotspotRoutingSnapshot
import com.v2ray.ang.service.HevTunnelConfig

/**
 * One validated engine payload for the tethering datapath: the rendered hev-socks5-tunnel
 * yaml plus the test-network addressing it was built against.
 *
 * Only the HEV engine is supported for tethering in this build (MikuRay's own HEV path
 * already covers the common case of `pref_use_hev_tunnel_v2`); porting the alternative
 * native-Xray-TUN tethering engine from upstream is left for a follow-up.
 */
internal data class HotspotRoutingConfig(
    val hevYaml: String,
    val ipv4Address: String,
    val ipv6Address: String?,
    val profileName: String,
) {
    companion object {
        fun fromSnapshot(snapshot: HotspotRoutingSnapshot): HotspotRoutingConfig? {
            if (!snapshot.running || !snapshot.vpnMode || snapshot.socksPort <= 0) return null
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
