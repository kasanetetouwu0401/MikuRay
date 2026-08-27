/*
 * Clash/Mihomo YAML subscription parsing adapted from Exclave's GPL-3.0-or-later
 * parser design. This implementation intentionally accepts a conservative subset
 * that MikuRay can represent with its Xray/v2fly-based profile model.
 *
 * Reference: https://github.com/ExclaveNetwork/Exclave/blob/dev/app/src/main/java/io/nekohasekai/sagernet/group/ClashYAMLParser.kt
 */
package com.miku.ray.fmt

import com.miku.ray.AppConfig
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.enums.NetworkType
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Parses only the `proxies` section of a Clash/Mihomo YAML document.
 *
 * Rules, proxy groups, rule providers, DNS settings, and unsupported protocol
 * options are intentionally ignored. This keeps imported subscriptions portable
 * to MikuRay's existing profile format instead of creating profiles that would
 * fail when the Xray/v2fly core starts.
 */
object ClashYamlFmt {
    private const val MAX_YAML_CODE_POINTS = 2_000_000
    private const val MAX_YAML_ALIASES = 50
    private const val MAX_YAML_NESTING_DEPTH = 50

    private val supportedShadowsocksMethods = setOf(
        "aes-128-gcm",
        "aes-192-gcm",
        "aes-256-gcm",
        "chacha20-ietf-poly1305",
    )

    fun parse(content: String?): List<ProfileItem> {
        if (content.isNullOrBlank()) return emptyList()

        val root = try {
            val options = LoaderOptions().apply {
                maxAliasesForCollections = MAX_YAML_ALIASES
                codePointLimit = MAX_YAML_CODE_POINTS
                nestingDepthLimit = MAX_YAML_NESTING_DEPTH
            }
            // `!<str>` is widely used by Clash subscriptions to preserve scalar
            // values as strings. Removing this specific presentation tag retains
            // the scalar while keeping SafeConstructor for all other YAML tags.
            val normalizedContent = content.replace("!<str>", "")
            Yaml(SafeConstructor(options)).load<Any?>(normalizedContent)
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        val proxies = (root as? Map<*, *>)
            ?.stringKeyMap()
            ?.value("proxies") as? List<*>
            ?: return emptyList()

        return proxies.mapNotNull { entry ->
            (entry as? Map<*, *>)?.stringKeyMap()?.toProfile()
        }
    }

    private fun Map<String, Any?>.toProfile(): ProfileItem? {
        val type = string("type")?.lowercase() ?: return null
        return when (type) {
            "ss", "shadowsocks" -> parseShadowsocks()
            "socks5" -> parseSocks()
            "http" -> parseHttp()
            "vmess" -> parseVmess()
            "vless" -> parseVless()
            "trojan" -> parseTrojan()
            "hysteria2" -> parseHysteria2()
            "wireguard" -> parseWireguard()
            else -> null
        }
    }

    private fun Map<String, Any?>.parseShadowsocks(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        val method = normalizeShadowsocksMethod(string("cipher")) ?: return null
        val password = string("password") ?: return null

        return ProfileItem.create(EConfigType.SHADOWSOCKS).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            this.method = method
            this.password = password
            if (!applyCompatibleShadowsocksPlugin(this@parseShadowsocks)) return null
        }
    }

    private fun Map<String, Any?>.parseSocks(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        return ProfileItem.create(EConfigType.SOCKS).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            username = string("username")
            password = string("password")
            if (!applyTlsSettings(this@parseSocks, trojan = false)) return null
        }
    }

    private fun Map<String, Any?>.parseHttp(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        return ProfileItem.create(EConfigType.HTTP).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            username = string("username")
            password = string("password")
            if (!applyTlsSettings(this@parseHttp, trojan = false)) return null
        }
    }

    private fun Map<String, Any?>.parseVmess(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        val uuid = string("uuid") ?: return null
        val alterId = int("alterId") ?: 0
        if (alterId != 0) return null

        return ProfileItem.create(EConfigType.VMESS).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            password = uuid
            method = string("cipher")?.takeIf { it.isNotBlank() } ?: AppConfig.DEFAULT_SECURITY
            applyTransportSettings(this@parseVmess, isVmess = true)
            if (!applyTlsSettings(this@parseVmess, trojan = false)) return null
        }
    }

    private fun Map<String, Any?>.parseVless(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        val uuid = string("uuid") ?: return null

        return ProfileItem.create(EConfigType.VLESS).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            password = uuid
            method = string("encryption")?.takeIf { it.isNotBlank() } ?: "none"
            flow = string("flow")?.takeIf { it.startsWith("xtls-rprx-vision") }
            applyTransportSettings(this@parseVless, isVmess = false)
            if (!applyTlsSettings(this@parseVless, trojan = false)) return null
            if (security == AppConfig.REALITY && network !in realityCompatibleNetworks) return null
        }
    }

    private fun Map<String, Any?>.parseTrojan(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        val password = string("password") ?: return null

        return ProfileItem.create(EConfigType.TROJAN).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            this.password = password
            applyTransportSettings(this@parseTrojan, isVmess = false)
            if (!applyTlsSettings(this@parseTrojan, trojan = true)) return null
            if (security == AppConfig.REALITY && network !in realityCompatibleNetworks) return null
        }
    }

    private fun Map<String, Any?>.parseHysteria2(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        return ProfileItem.create(EConfigType.HYSTERIA2).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            password = string("password")
            network = NetworkType.HYSTERIA.type
            security = AppConfig.TLS
            sni = string("sni") ?: string("servername")
            alpn = stringList("alpn").ifEmpty { listOf("h3") }.joinToString(",")
            insecure = boolean("skip-cert-verify") ?: false
            val obfs = string("obfs")
            if (obfs.isNullOrEmpty() || obfs == "salamander") {
                obfsPassword = string("obfs-password")
            } else {
                return null
            }
        }
    }

    private fun Map<String, Any?>.parseWireguard(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        val privateKey = string("private-key") ?: return null
        val publicKey = string("public-key") ?: return null
        val addresses = listOfNotNull(
            string("ip")?.takeIf { it.isNotBlank() },
            string("ipv6")?.takeIf { it.isNotBlank() },
        ).joinToString("\n")
        if (addresses.isEmpty()) return null

        return ProfileItem.create(EConfigType.WIREGUARD).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            secretKey = privateKey
            this.publicKey = publicKey
            preSharedKey = string("pre-shared-key") ?: string("preshared-key")
            localAddress = addresses
            mtu = int("mtu")?.takeIf { it > 0 } ?: AppConfig.WIREGUARD_LOCAL_MTU.toInt()
            reserved = reservedValue() ?: "0,0,0"
        }
    }

    private fun ProfileItem.applyCompatibleShadowsocksPlugin(proxy: Map<String, Any?>): Boolean {
        val plugin = proxy.string("plugin")?.lowercase().orEmpty()
        if (plugin.isEmpty()) return true
        if (plugin != "obfs" && plugin != "simple-obfs" && plugin != "obfs-local") return false

        val options = proxy.map("plugin-opts") ?: return false
        if (options.string("mode")?.lowercase() != "http") return false

        network = NetworkType.TCP.type
        headerType = AppConfig.HEADER_TYPE_HTTP
        host = options.string("host")?.takeIf { it.isNotBlank() }
        path = options.string("path")?.takeIf { it.isNotBlank() }
        return true
    }

    private fun ProfileItem.applyTransportSettings(proxy: Map<String, Any?>, isVmess: Boolean) {
        when (proxy.string("network")?.lowercase()) {
            "ws" -> {
                network = NetworkType.WS.type
                val options = proxy.map("ws-opts")
                path = options?.string("path")
                host = options?.map("headers")?.string("host")
                if (options?.boolean("v2ray-http-upgrade") == true) {
                    network = NetworkType.HTTP_UPGRADE.type
                }
            }

            "grpc" -> {
                network = NetworkType.GRPC.type
                val options = proxy.map("grpc-opts")
                serviceName = options?.string("grpc-service-name")
                authority = options?.string("authority")
                mode = options?.string("mode")
            }

            "xhttp" -> {
                network = NetworkType.XHTTP.type
                val options = proxy.map("xhttp-opts")
                path = options?.string("path")
                host = options?.string("host")
                xhttpMode = options?.string("mode")?.takeIf { it in supportedXhttpModes } ?: "auto"
            }

            "h2" -> {
                network = NetworkType.H2.type
                val options = proxy.map("h2-opts")
                path = options?.string("path")
                host = options?.stringList("host")?.joinToString(",")
            }

            "http" -> {
                if (isVmess) {
                    network = NetworkType.TCP.type
                    headerType = AppConfig.HEADER_TYPE_HTTP
                    val options = proxy.map("http-opts")
                    path = options?.stringList("path")?.joinToString(",")
                    host = options?.map("headers")?.stringList("host")?.joinToString(",")
                } else {
                    network = NetworkType.HTTP.type
                    val options = proxy.map("h2-opts")
                    path = options?.string("path")
                    host = options?.stringList("host")?.joinToString(",")
                }
            }

            "kcp", "mkcp" -> {
                network = NetworkType.KCP.type
                val options = proxy.map("mkcp-opts")
                seed = options?.string("seed")
                headerType = options?.string("type")?.let {
                    if (it == "wechat") "wechat-video" else it
                }
            }

            else -> network = NetworkType.TCP.type
        }
    }

    private fun ProfileItem.applyTlsSettings(proxy: Map<String, Any?>, trojan: Boolean): Boolean {
        val reality = proxy.map("reality-opts")
        if (reality != null) {
            val key = reality.string("public-key")?.takeIf { it.isNotBlank() } ?: return false
            security = AppConfig.REALITY
            publicKey = key
            shortId = reality.string("short-id")
        } else if (trojan || proxy.boolean("tls") == true) {
            security = AppConfig.TLS
        } else {
            security = null
        }

        sni = proxy.string("servername") ?: proxy.string("sni")
        alpn = proxy.stringList("alpn").joinToString(",").takeIf { it.isNotEmpty() }
        fingerPrint = proxy.string("client-fingerprint")
        insecure = proxy.boolean("skip-cert-verify") ?: false
        verifyPeerCertByName = proxy.string("name-cert-verify")
        return true
    }

    private fun Map<String, Any?>.endpoint(): Endpoint? {
        val host = string("server")?.trim()?.removeSurrounding("[", "]") ?: return null
        val port = int("port") ?: return null
        if (host.isEmpty() || port !in 1..65535) return null
        return Endpoint(host, port)
    }

    private fun Map<String, Any?>.remark(endpoint: Endpoint): String =
        string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: "${endpoint.host}:${endpoint.port}"

    private fun Map<String, Any?>.value(name: String): Any? = this[name.lowercase()]

    private fun Map<String, Any?>.string(name: String): String? = when (val value = value(name)) {
        null -> null
        is String -> value
        is Number, is Boolean -> value.toString()
        else -> null
    }

    private fun Map<String, Any?>.int(name: String): Int? = when (val value = value(name)) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }

    private fun Map<String, Any?>.boolean(name: String): Boolean? = when (val value = value(name)) {
        is Boolean -> value
        is String -> when (value.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
        else -> null
    }

    private fun Map<String, Any?>.map(name: String): Map<String, Any?>? =
        (value(name) as? Map<*, *>)?.stringKeyMap()

    private fun Map<String, Any?>.stringList(name: String): List<String> = when (val value = value(name)) {
        is List<*> -> value.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
        is String -> value.trim().takeIf { it.isNotEmpty() }?.let(::listOf).orEmpty()
        else -> emptyList()
    }

    private fun Map<String, Any?>.reservedValue(): String? = when (val value = value("reserved")) {
        is List<*> -> value.mapNotNull { (it as? Number)?.toInt() }
            .takeIf { it.size == 3 }
            ?.joinToString(",")
        is String -> value.takeIf { it.split(',').size == 3 }
        else -> null
    }

    private fun Map<*, *>.stringKeyMap(): Map<String, Any?> = buildMap {
        for ((key, value) in this@stringKeyMap) {
            val normalizedKey = key?.toString()?.trim()?.lowercase() ?: continue
            put(normalizedKey, value)
        }
    }

    private fun normalizeShadowsocksMethod(method: String?): String? {
        val normalized = method?.trim()?.lowercase()?.replace('_', '-') ?: return null
        return when (normalized) {
            "aead-chacha20-poly1305", "aead-chacha20-ietf-poly1305", "chacha20-poly1305" ->
                "chacha20-ietf-poly1305"
            "aead-aes-128-gcm" -> "aes-128-gcm"
            "aead-aes-192-gcm" -> "aes-192-gcm"
            "aead-aes-256-gcm" -> "aes-256-gcm"
            in supportedShadowsocksMethods -> normalized
            else -> null
        }
    }

    private data class Endpoint(val host: String, val port: Int)

    private val realityCompatibleNetworks = setOf(
        NetworkType.TCP.type,
        NetworkType.HTTP.type,
        NetworkType.H2.type,
        NetworkType.GRPC.type,
        NetworkType.XHTTP.type,
    )

    private val supportedXhttpModes = setOf("auto", "packet-up", "stream-up", "stream-one")
}
