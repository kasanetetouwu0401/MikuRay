package com.miku.ray.fmt

import com.miku.ray.AppConfig
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Neko-compatible SSH profile format.
 *
 * Canonical form:
 * ssh://user:password@host:port?type=1&localPort=10809&defaultPayload=true#remark
 *
 * The legacy Neko compact form host:port@user:password is accepted as well.
 * Payload/SSL fields are persisted for compatibility; the current adapter uses
 * direct SSH and HTTP CONNECT proxy modes, while reporting unsupported custom
 * payload/SSL modes instead of silently changing their meaning.
 */
object SshFmt : FmtBase() {
    fun parse(value: String): ProfileItem? {
        val raw = value.trim()
        if (raw.isEmpty()) return null
        return if (raw.startsWith(AppConfig.SSH)) parseUri(raw) else parseLegacy(raw)
    }

    private fun parseUri(raw: String): ProfileItem? {
        return try {
            val uri = URI(raw)
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else 22
            val userInfo = uri.rawUserInfo.orEmpty()
            val userPass = userInfo.split(":", limit = 2)
            val query = parseQueryParam(uri)
            ProfileItem.create(EConfigType.SSH).apply {
                remarks = uri.fragment?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
                    .orEmpty().ifBlank { "$host:$port" }
                sshServer = host
                sshPort = port.toString()
                sshUser = userPass.firstOrNull()?.let(::decode) ?: ""
                sshPass = userPass.getOrNull(1)?.let(::decode) ?: ""
                sshPortaLocal = query["localPort"] ?: query["sshPortaLocal"] ?: "10809"
                sshTunnelType = query["type"] ?: query["tunnelType"] ?: "1"
                sshPayload = query["payload"] ?: query["proxyPayload"]
                sshWsPayload = query["wsPayload"]
                sshRemoteProxy = query["proxy"] ?: query["proxyRemoto"]
                sshRemoteProxyPort = query["proxyPort"] ?: query["proxyRemotoPorta"]
                sshUseDefaultPayload = (query["defaultPayload"] ?: query["usarDefaultPayload"] ?: "true").toBoolean()
                sshTlsServerName = query["sni"]
                sshTlsForcing = query["tlsForcing"] ?: query["tls"] ?: "tlsAuto"
                sshTrustAllCertificates = (query["trustAllCertificates"] ?: query["trustAll"] ?: "true").toBoolean()
                sshCompression = (query["compression"] ?: query["data_compression"] ?: "true").toBoolean()
                sshDnsResolver1 = query["dns1"]
                sshDnsResolver2 = query["dns2"]
                sshUdpResolver = query["udpResolver"] ?: query["udpgws"]
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLegacy(raw: String): ProfileItem? {
        val at = raw.indexOf('@')
        val hostPort = if (at >= 0) raw.substring(0, at) else raw
        val credentials = if (at >= 0) raw.substring(at + 1) else ""
        val colon = hostPort.lastIndexOf(':')
        if (colon <= 0) return null
        val host = hostPort.substring(0, colon)
        val port = hostPort.substring(colon + 1).toIntOrNull() ?: return null
        val userPass = credentials.split(":", limit = 2)
        return ProfileItem.create(EConfigType.SSH).apply {
            remarks = "$host:$port"
            sshServer = host
            sshPort = port.toString()
            sshUser = userPass.firstOrNull().orEmpty()
            sshPass = userPass.getOrNull(1).orEmpty()
            sshPortaLocal = "10809"
            sshTunnelType = "1"
            sshUseDefaultPayload = true
            sshCompression = true
        }
    }

    fun toUri(config: ProfileItem): String {
        val user = encode(config.sshUser.orEmpty())
        val pass = encode(config.sshPass.orEmpty())
        val host = config.sshServer.orEmpty()
        val port = config.sshPort ?: "22"
        val query = linkedMapOf(
            "type" to (config.sshTunnelType ?: "1"),
            "localPort" to (config.sshPortaLocal ?: "10809"),
            "defaultPayload" to (config.sshUseDefaultPayload ?: true).toString(),
            "tlsForcing" to (config.sshTlsForcing ?: "tlsAuto"),
            "trustAllCertificates" to (config.sshTrustAllCertificates ?: true).toString(),
            "compression" to (config.sshCompression ?: true).toString(),
        ).apply {
            config.sshPayload?.takeIf { it.isNotBlank() }?.let { put("payload", it) }
            config.sshWsPayload?.takeIf { it.isNotBlank() }?.let { put("wsPayload", it) }
            config.sshRemoteProxy?.takeIf { it.isNotBlank() }?.let { put("proxy", it) }
            config.sshRemoteProxyPort?.takeIf { it.isNotBlank() }?.let { put("proxyPort", it) }
            config.sshTlsServerName?.takeIf { it.isNotBlank() }?.let { put("sni", it) }
            config.sshDnsResolver1?.takeIf { it.isNotBlank() }?.let { put("dns1", it) }
            config.sshDnsResolver2?.takeIf { it.isNotBlank() }?.let { put("dns2", it) }
            config.sshUdpResolver?.takeIf { it.isNotBlank() }?.let { put("udpResolver", it) }
        }
        val queryString = query.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val fragment = config.remarks.takeIf { it.isNotBlank() }?.let { "#${encode(it)}" }.orEmpty()
        return "${AppConfig.SSH}$user:$pass@$host:$port?$queryString$fragment"
    }

    private fun parseQueryParam(uri: URI): Map<String, String> =
        uri.rawQuery.orEmpty().split('&')
            .filter { it.isNotBlank() }
            .associate {
                val parts = it.split('=', limit = 2)
                decode(parts[0]) to decode(parts.getOrElse(1) { "" })
            }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
