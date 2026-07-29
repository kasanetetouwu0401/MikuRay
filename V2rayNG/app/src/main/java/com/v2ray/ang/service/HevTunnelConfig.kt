package com.v2ray.ang.service

import com.v2ray.ang.AppConfig

/**
 * Parameters needed to render a hev-socks5-tunnel yaml config for the Shizuku tethering
 * engine. This mirrors [TProxyService]'s own config builder but targets the test-network
 * TUN address/MTU instead of the regular VPN interface, and is built from a
 * [com.v2ray.ang.dto.HotspotRoutingSnapshot] captured by the daemon process rather than
 * from live settings (which may have changed since that snapshot was taken).
 */
internal data class HevTunnelParameters(
    val mtu: Int,
    val ipv4: String,
    val ipv6: String? = null,
    val socksAddress: String,
    val socksPort: Int,
    val socksUsername: String? = null,
    val socksPassword: String? = null,
    val tcpTimeoutSeconds: Int,
    val udpTimeoutSeconds: Int,
    val logLevel: String,
)

internal object HevTunnelConfig {
    fun build(parameters: HevTunnelParameters): String = buildString {
        appendLine("tunnel:")
        appendLine("  mtu: ${parameters.mtu}")
        appendLine("  ipv4: '${parameters.ipv4.yamlSingleQuoted()}'")
        parameters.ipv6?.let { appendLine("  ipv6: '${it.yamlSingleQuoted()}'") }
        appendLine("socks5:")
        appendLine("  port: ${parameters.socksPort}")
        appendLine("  address: '${parameters.socksAddress.yamlSingleQuoted()}'")
        appendLine("  udp: 'udp'")
        if (parameters.socksUsername != null && parameters.socksPassword != null) {
            appendLine("  username: '${parameters.socksUsername.yamlSingleQuoted()}'")
            appendLine("  password: '${parameters.socksPassword.yamlSingleQuoted()}'")
        }
        appendLine("misc:")
        appendLine("  task-stack-size: 20480")
        appendLine("  connect-timeout: 5000")
        appendLine("  read-write-timeout: ${parameters.tcpTimeoutSeconds * 1000}")
        appendLine("  log-level: '${parameters.logLevel.yamlSingleQuoted()}'")
        appendLine("  limit-nofile: 65535")
    }

    /** Builds directly from a captured [com.v2ray.ang.dto.HotspotRoutingSnapshot]. */
    fun buildFromSnapshot(
        snapshot: com.v2ray.ang.dto.HotspotRoutingSnapshot,
        ipv4: String = AppConfig.SHIZUKU_TUN_ADDR_V4.substringBefore('/'),
        ipv6: String? = if (snapshot.ipv6Enabled) AppConfig.SHIZUKU_TUN_ADDR_V6.substringBefore('/') else null,
    ): String = build(
        HevTunnelParameters(
            mtu = snapshot.mtu,
            ipv4 = ipv4,
            ipv6 = ipv6,
            socksAddress = AppConfig.LOOPBACK,
            socksPort = snapshot.socksPort,
            socksUsername = snapshot.socksUsername,
            socksPassword = snapshot.socksPassword,
            tcpTimeoutSeconds = snapshot.hevTcpTimeoutSeconds,
            udpTimeoutSeconds = snapshot.hevUdpTimeoutSeconds,
            logLevel = snapshot.hevLogLevel,
        )
    )

    private fun String.yamlSingleQuoted(): String = replace("'", "''")
}
