/*
 * Clash/Mihomo YAML subscription parsing adapted from Exclave's GPL-3.0-or-later
 * parser design. This implementation accepts only fields that MikuRay can
 * represent with its Xray/v2fly-based profile model.
 *
 * References:
 * - https://github.com/ExclaveNetwork/Exclave/blob/dev/app/src/main/java/io/nekohasekai/sagernet/group/ClashYAMLParser.kt
 * - https://github.com/MetaCubeX/mihomo/blob/Alpha/docs/config.yaml
 */
package com.miku.ray.fmt

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.miku.ray.AppConfig
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.enums.NetworkType
import java.util.Base64
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Parses only the `proxies` section of a Clash/Mihomo YAML document.
 *
 * Rules, proxy groups, rule providers, DNS settings, and options without an
 * equivalent MikuRay profile field are rejected or omitted deliberately. This
 * prevents subscriptions from creating profiles that look imported but cannot
 * run correctly with MikuRay's existing Xray/v2fly core.
 */
object ClashYamlFmt {
    private const val MAX_YAML_CODE_POINTS = 2_000_000
    private const val MAX_YAML_ALIASES = 50
    private const val MAX_YAML_NESTING_DEPTH = 50

    private val gson = Gson()

    private val supportedShadowsocksMethods = setOf(
        "aes-128-gcm",
        "aes-192-gcm",
        "aes-256-gcm",
        "chacha20-ietf-poly1305",
        "xchacha20-ietf-poly1305",
        "aes-128-cfb",
        "aes-192-cfb",
        "aes-256-cfb",
        "aes-128-ctr",
        "aes-192-ctr",
        "aes-256-ctr",
        "rc4-md5",
        "chacha20-ietf",
        "xchacha20",
        "2022-blake3-aes-128-gcm",
        "2022-blake3-aes-256-gcm",
        "2022-blake3-chacha20-poly1305",
    )

    private val supportedVmessMethods = setOf(
        "auto",
        "aes-128-gcm",
        "chacha20-poly1305",
        "none",
    )

    private val realityCompatibleNetworks = setOf(
        NetworkType.TCP.type,
        NetworkType.HTTP.type,
        NetworkType.H2.type,
        NetworkType.GRPC.type,
        NetworkType.XHTTP.type,
    )

    private val supportedXhttpModes = setOf("auto", "packet-up", "stream-up", "stream-one")
    private val supportedKcpHeaders = setOf("none", "srtp", "utp", "wechat-video", "dtls", "wireguard", "dns")

    fun parse(content: String?): List<ProfileItem> {
        if (content.isNullOrBlank()) return emptyList()

        val root = try {
            val options = LoaderOptions().apply {
                maxAliasesForCollections = MAX_YAML_ALIASES
                codePointLimit = MAX_YAML_CODE_POINTS
                nestingDepthLimit = MAX_YAML_NESTING_DEPTH
            }
            // `!<str>` is common in Clash subscriptions. Removing only this
            // presentation tag retains the scalar while SafeConstructor rejects
            // all other untrusted object tags.
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
        if (hasUnsupportedCommonOptions()) return null

        return when (string("type")?.trim()?.lowercase()) {
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
        // MikuRay's SOCKS outbound does not expose StreamSettings, so SOCKS-over-
        // TLS cannot be faithfully represented by this profile type.
        if (boolean("tls") == true || hasTlsOnlyOptions()) return null

        return ProfileItem.create(EConfigType.SOCKS).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            username = string("username")
            password = string("password")
        }
    }

    private fun Map<String, Any?>.parseHttp(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        // MikuRay's HTTP outbound likewise has no stream/TLS settings.
        if (boolean("tls") == true || hasTlsOnlyOptions()) return null

        return ProfileItem.create(EConfigType.HTTP).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            username = string("username")
            password = string("password")
        }
    }

    private fun Map<String, Any?>.parseVmess(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        val uuid = string("uuid") ?: return null
        val alterId = int("alterId") ?: 0
        // AlterID is absent from MikuRay's profile model and no longer supported
        // by modern Xray transports. Do not import it as a different node.
        if (alterId != 0) return null
        val method = string("cipher")?.trim()?.lowercase()?.ifEmpty { "auto" } ?: "auto"
        if (method !in supportedVmessMethods) return null

        return ProfileItem.create(EConfigType.VMESS).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            password = uuid
            this.method = method
            if (!applyTransportSettings(this@parseVmess, isVmess = true)) return null
            if (!applyTlsSettings(this@parseVmess, trojan = false)) return null
            if (security == AppConfig.REALITY && network !in realityCompatibleNetworks) return null
        }
    }

    private fun Map<String, Any?>.parseVless(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        val uuid = string("uuid") ?: return null
        val sourceFlow = string("flow")?.trim().orEmpty()
        if (sourceFlow.isNotEmpty() && sourceFlow !in supportedVlessFlows) return null

        return ProfileItem.create(EConfigType.VLESS).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            password = uuid
            method = string("encryption")?.takeIf { it.isNotBlank() } ?: "none"
            flow = sourceFlow.ifEmpty { null }
            if (!applyTransportSettings(this@parseVless, isVmess = false)) return null
            if (!applyTlsSettings(this@parseVless, trojan = false)) return null
            if (security == AppConfig.REALITY && network !in realityCompatibleNetworks) return null
            if (flow == "xtls-rprx-vision" && security != null && network != NetworkType.TCP.type) return null
        }
    }

    private fun Map<String, Any?>.parseTrojan(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        val password = string("password") ?: return null
        val sourceFlow = string("flow")?.trim().orEmpty()
        if (sourceFlow.isNotEmpty() && sourceFlow !in supportedTrojanFlows) return null

        return ProfileItem.create(EConfigType.TROJAN).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            this.password = password
            flow = sourceFlow.ifEmpty { null }
            if (!applyTransportSettings(this@parseTrojan, isVmess = false)) return null
            if (!applyTlsSettings(this@parseTrojan, trojan = true)) return null
            if (security == AppConfig.REALITY && network !in realityCompatibleNetworks) return null
        }
    }

    private fun Map<String, Any?>.parseHysteria2(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        if (map("realm-opts")?.boolean("enable") == true || hasUnsupportedTlsMaterial()) return null
        val obfs = string("obfs")?.trim().orEmpty()
        if (obfs.isNotEmpty() && obfs != "salamander") return null

        return ProfileItem.create(EConfigType.HYSTERIA2).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            password = string("password")
            network = NetworkType.HYSTERIA.type
            security = AppConfig.TLS
            portHopping = string("ports")?.takeIf { it.isNotBlank() }
            portHoppingInterval = string("hop-interval")?.takeIf { it.isNotBlank() }
            bandwidthUp = normalizeBandwidth(string("up"))
            bandwidthDown = normalizeBandwidth(string("down"))
            obfsPassword = string("obfs-password")?.takeIf { obfs == "salamander" }
            if (!applyTlsOptions(this@parseHysteria2, serverNameFirst = false)) return null
            alpn = alpn ?: "h3"
        }
    }

    private fun Map<String, Any?>.parseWireguard(): ProfileItem? {
        if (map("amnezia-wg-option") != null || value("peers") != null) return null
        val endpoint = endpoint() ?: return null
        val privateKey = string("private-key") ?: return null
        val publicKey = string("public-key") ?: return null
        val addresses = listOfNotNull(
            string("ip")?.takeIf { it.isNotBlank() },
            string("ipv6")?.takeIf { it.isNotBlank() },
        ).joinToString(",")
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
        val plugin = proxy.string("plugin")?.trim()?.lowercase().orEmpty()
        if (plugin.isEmpty()) return true
        // The profile model maps only simple-obfs HTTP to Xray TCP header HTTP.
        if (plugin !in supportedSimpleObfsPlugins) return false
        val options = proxy.map("plugin-opts") ?: return false
        if (options.string("mode")?.trim()?.lowercase() != "http") return false

        network = NetworkType.TCP.type
        headerType = AppConfig.HEADER_TYPE_HTTP
        host = options.string("host")?.takeIf { it.isNotBlank() }
        path = options.string("path")?.takeIf { it.isNotBlank() }
        return true
    }

    private fun ProfileItem.applyTransportSettings(proxy: Map<String, Any?>, isVmess: Boolean): Boolean {
        return when (proxy.string("network")?.trim()?.lowercase()) {
            null, "", "tcp" -> {
                network = NetworkType.TCP.type
                true
            }

            "ws" -> {
                network = NetworkType.WS.type
                val options = proxy.map("ws-opts")
                path = options?.string("path")
                host = options?.map("headers")?.stringList("host")?.firstOrNull()
                if (options?.boolean("v2ray-http-upgrade") == true) {
                    network = NetworkType.HTTP_UPGRADE.type
                }
                true
            }

            "grpc" -> {
                network = NetworkType.GRPC.type
                val options = proxy.map("grpc-opts")
                serviceName = options?.string("grpc-service-name")
                authority = options?.string("authority")
                mode = options?.string("mode")?.takeIf { it == "multi" }
                true
            }

            "xhttp" -> {
                if (isVmess) return false
                network = NetworkType.XHTTP.type
                val options = proxy.map("xhttp-opts")
                if (options?.map("download-settings") != null) return false
                path = options?.string("path")
                host = options?.string("host")
                xhttpMode = options?.string("mode")?.takeIf { it in supportedXhttpModes } ?: "auto"
                xhttpExtra = options?.toXhttpExtra()
                true
            }

            "h2" -> {
                network = NetworkType.H2.type
                val options = proxy.map("h2-opts")
                path = options?.string("path")
                host = options?.stringList("host")?.joinToString(",")
                true
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
                true
            }

            "kcp", "mkcp" -> {
                if (!isVmess) return false
                network = NetworkType.KCP.type
                val options = proxy.map("mkcp-opts")
                seed = options?.string("seed")
                kcpMtu = options?.int("mtu")?.takeIf { it > 0 }
                kcpTti = options?.int("tti")?.takeIf { it > 0 }
                headerType = options?.string("header")?.normalizeKcpHeader() ?: "none"
                true
            }

            else -> false
        }
    }

    private fun ProfileItem.applyTlsSettings(proxy: Map<String, Any?>, trojan: Boolean): Boolean {
        if (proxy.hasUnsupportedTlsMaterial()) return false
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

        return applyTlsOptions(proxy, serverNameFirst = true)
    }

    private fun ProfileItem.applyTlsOptions(proxy: Map<String, Any?>, serverNameFirst: Boolean): Boolean {
        sni = if (serverNameFirst) {
            proxy.string("servername") ?: proxy.string("sni")
        } else {
            proxy.string("sni") ?: proxy.string("servername")
        }
        alpn = proxy.stringList("alpn").joinToString(",").takeIf { it.isNotEmpty() }
        fingerPrint = proxy.string("client-fingerprint")?.takeIf { it.isNotBlank() }
        insecure = proxy.boolean("skip-cert-verify") ?: false
        verifyPeerCertByName = proxy.string("name-cert-verify")?.takeIf { it.isNotBlank() }

        val pinConfigured = proxy.string("fingerprint")?.isNotBlank() == true
        val pin = proxy.certificateFingerprint()
        if (pinConfigured && pin == null) return false
        pinnedCA256 = pin

        val ech = proxy.map("ech-opts")
        if (ech?.boolean("enable") == true) {
            if (security != AppConfig.TLS && security != AppConfig.REALITY) return false
            echConfigList = ech.string("config")?.takeIf { it.isNotBlank() } ?: return false
        }
        return true
    }

    private fun Map<String, Any?>.toXhttpExtra(): String? {
        val extra = JsonObject()
        fun copyString(source: String, target: String) {
            string(source)?.takeIf { it.isNotBlank() }?.let { extra.addProperty(target, it) }
        }
        fun copyBoolean(source: String, target: String) {
            boolean(source)?.let { extra.addProperty(target, it) }
        }

        copyBoolean("no-grpc-header", "noGRPCHeader")
        copyString("x-padding-bytes", "xPaddingBytes")
        copyBoolean("x-padding-obfs-mode", "xPaddingObfsMode")
        copyString("x-padding-key", "xPaddingKey")
        copyString("x-padding-header", "xPaddingHeader")
        copyString("x-padding-placement", "xPaddingPlacement")
        copyString("x-padding-method", "xPaddingMethod")
        copyString("uplink-http-method", "uplinkHTTPMethod")
        copyString("session-placement", "sessionIDPlacement")
        copyString("session-key", "sessionIDKey")
        copyString("session-table", "sessionIDTable")
        copyString("session-length", "sessionIDLength")
        copyString("seq-placement", "seqPlacement")
        copyString("seq-key", "seqKey")
        copyString("uplink-data-placement", "uplinkDataPlacement")
        copyString("uplink-data-key", "uplinkDataKey")
        copyString("uplink-chunk-size", "uplinkChunkSize")
        copyString("sc-max-each-post-bytes", "scMaxEachPostBytes")
        copyString("sc-min-posts-interval-ms", "scMinPostsIntervalMs")

        map("reuse-settings")?.let { reuse ->
            val xmux = JsonObject()
            fun copyReuse(source: String, target: String) {
                reuse.string(source)?.takeIf { it.isNotBlank() }?.let { xmux.addProperty(target, it) }
            }
            copyReuse("max-connections", "maxConnections")
            copyReuse("max-concurrency", "maxConcurrency")
            copyReuse("c-max-reuse-times", "cMaxReuseTimes")
            copyReuse("h-max-request-times", "hMaxRequestTimes")
            copyReuse("h-max-reusable-secs", "hMaxReusableSecs")
            copyReuse("h-keep-alive-period", "hKeepAlivePeriod")
            if (xmux.entrySet().isNotEmpty()) extra.add("xmux", xmux)
        }

        return gson.toJson(extra).takeIf { extra.entrySet().isNotEmpty() }
    }

    private fun Map<String, Any?>.hasUnsupportedCommonOptions(): Boolean {
        if (string("dialer-proxy")?.isNotBlank() == true) return true
        if (boolean("udp") == false || map("smux")?.boolean("enabled") == true) return true
        return listOf("tlsmirror-opts", "shadow-tls-opts", "restls-opts", "jls-opts")
            .any { option -> map(option) != null }
    }

    private fun Map<String, Any?>.hasTlsOnlyOptions(): Boolean =
        boolean("tls") == true || map("reality-opts") != null || map("ech-opts") != null
            || string("servername")?.isNotBlank() == true || string("sni")?.isNotBlank() == true
            || string("client-fingerprint")?.isNotBlank() == true || string("fingerprint")?.isNotBlank() == true
            || boolean("skip-cert-verify") != null || string("name-cert-verify")?.isNotBlank() == true
            || hasUnsupportedTlsMaterial()

    private fun Map<String, Any?>.hasUnsupportedTlsMaterial(): Boolean =
        value("certificate") != null || value("private-key") != null

    private fun Map<String, Any?>.certificateFingerprint(): String? {
        val raw = string("fingerprint")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val values = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (values.isEmpty() || values.any { value ->
                value.replace(":", "").matches(Regex("[0-9a-fA-F]{64}")).not()
            }) return null
        return values.joinToString(",")
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
            .takeIf { it.size == 3 && it.all { byte -> byte in 0..255 } }
            ?.joinToString(",")
        is String -> {
            val trimmed = value.trim()
            val decimalValues = trimmed.split(',').mapNotNull { it.trim().toIntOrNull() }
            when {
                decimalValues.size == 3 && decimalValues.all { it in 0..255 } -> decimalValues.joinToString(",")
                else -> decodeReservedBase64(trimmed)
            }
        }
        else -> null
    }

    private fun decodeReservedBase64(value: String): String? {
        if (value.isBlank()) return null
        val padded = value + "=".repeat((4 - value.length % 4) % 4)
        val bytes = try {
            Base64.getDecoder().decode(padded)
        } catch (_: IllegalArgumentException) {
            try {
                Base64.getUrlDecoder().decode(padded)
            } catch (_: IllegalArgumentException) {
                return null
            }
        }
        return bytes.takeIf { it.size == 3 }?.joinToString(",") { byte -> (byte.toInt() and 0xff).toString() }
    }

    private fun String?.normalizeKcpHeader(): String {
        val normalized = this?.trim()?.lowercase().orEmpty()
        return when (normalized) {
            "", "noop" -> "none"
            "wechat" -> "wechat-video"
            in supportedKcpHeaders -> normalized
            else -> "none"
        }
    }

    private fun normalizeBandwidth(value: String?): String? {
        val text = value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val match = Regex("^(\\d+(?:\\.\\d+)?)\\s*(?:([kmgt])(?:bps|bit/s|b/s)?|mbps)?$").matchEntire(text)
            ?: return text
        val number = match.groupValues[1]
        val unit = match.groupValues[2].ifEmpty { "m" }
        return "$number$unit"
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

    private val supportedSimpleObfsPlugins = setOf("obfs", "simple-obfs", "obfs-local")
    private val supportedVlessFlows = setOf("xtls-rprx-vision", "xtls-rprx-vision-udp443")
    private val supportedTrojanFlows = setOf("xtls-rprx-origin", "xtls-rprx-direct")
}
