/*
 * sing-box JSON outbound parsing adapted from Exclave's GPL-3.0-or-later
 * parser design. This implementation accepts fields only when they can be
 * mapped safely to MikuRay's Xray/v2fly-based profile model.
 *
 * References:
 * - https://github.com/ExclaveNetwork/Exclave/blob/dev/app/src/main/java/io/nekohasekai/sagernet/group/SingBoxJSONParser.kt
 * - https://github.com/chika0801/sing-box-examples
 */
package com.miku.ray.fmt

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.miku.ray.AppConfig
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.enums.NetworkType
import java.util.Base64

/**
 * Converts compatible sing-box `outbounds` into MikuRay profiles.
 *
 * Inbounds, route rules, DNS configuration, selectors, URLTest groups and
 * protocol-specific functionality outside the Xray/v2fly core are intentionally
 * not imported. This avoids creating a profile whose important settings would
 * be silently lost before MikuRay starts the core.
 */
object SingBoxFmt {
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

    private val supportedVlessFlows = setOf("xtls-rprx-vision", "xtls-rprx-vision-udp443")
    private val supportedTrojanFlows = setOf("xtls-rprx-origin", "xtls-rprx-direct")

    fun parse(content: String?): List<ProfileItem> {
        if (content.isNullOrBlank()) return emptyList()

        val root = try {
            JsonParser.parseString(content).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        val outbounds = root.array("outbounds") ?: JsonArray().apply { add(root) }
        return outbounds.mapNotNull { element ->
            element.takeIf { it.isJsonObject }?.asJsonObject?.toProfile()
        }
    }

    private fun JsonObject.toProfile(): ProfileItem? {
        if (hasUnsupportedCommonOptions()) return null

        return when (string("type")?.trim()?.lowercase()) {
            "shadowsocks" -> parseShadowsocks()
            "socks" -> parseSocks()
            "http" -> parseHttp()
            "vmess" -> parseVmess()
            "vless" -> parseVless()
            "trojan" -> parseTrojan()
            "hysteria2" -> parseHysteria2()
            "wireguard" -> parseWireguard()
            else -> null
        }
    }

    private fun JsonObject.parseShadowsocks(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        val method = normalizeShadowsocksMethod(string("method")) ?: return null
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

    private fun JsonObject.parseSocks(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        val version = string("version")
        if (version != null && version != "" && version != "5") return null
        if (hasTlsOnlyOptions()) return null

        return ProfileItem.create(EConfigType.SOCKS).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            username = string("username")
            password = string("password")
        }
    }

    private fun JsonObject.parseHttp(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        val path = string("path")
        if (path != null && path != "" && path != "/") return null
        if (hasTlsOnlyOptions()) return null

        return ProfileItem.create(EConfigType.HTTP).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            username = string("username")
            password = string("password")
        }
    }

    private fun JsonObject.parseVmess(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        val uuid = string("uuid") ?: return null
        val alterId = int("alter_id") ?: 0
        // AlterID is not stored by MikuRay and is obsolete for current Xray.
        if (alterId != 0) return null
        val method = string("security")?.trim()?.lowercase()?.ifEmpty { "auto" } ?: "auto"
        if (method !in supportedVmessMethods) return null

        return ProfileItem.create(EConfigType.VMESS).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            password = uuid
            this.method = method
            if (!applyTransportSettings(this@parseVmess)) return null
            if (!applyTlsSettings(this@parseVmess, trojan = false)) return null
            if (security == AppConfig.REALITY && network !in realityCompatibleNetworks) return null
        }
    }

    private fun JsonObject.parseVless(): ProfileItem? {
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
            if (!applyTransportSettings(this@parseVless)) return null
            if (!applyTlsSettings(this@parseVless, trojan = false)) return null
            if (security == AppConfig.REALITY && network !in realityCompatibleNetworks) return null
            if (flow == "xtls-rprx-vision" && security != null && network != NetworkType.TCP.type) return null
        }
    }

    private fun JsonObject.parseTrojan(): ProfileItem? {
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
            if (!applyTransportSettings(this@parseTrojan)) return null
            if (!applyTlsSettings(this@parseTrojan, trojan = true)) return null
            if (security == AppConfig.REALITY && network !in realityCompatibleNetworks) return null
        }
    }

    private fun JsonObject.parseHysteria2(): ProfileItem? {
        val endpoint = endpoint() ?: return null
        val tls = obj("tls") ?: return null
        if (tls.bool("enabled") != true || tls.obj("reality")?.bool("enabled") == true || tls.hasUnsupportedTlsOptions()) return null

        val obfs = obj("obfs")
        val obfsType = obfs?.string("type")?.trim().orEmpty()
        if (obfsType.isNotEmpty() && obfsType != "salamander") return null

        return ProfileItem.create(EConfigType.HYSTERIA2).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            password = string("password")
            network = NetworkType.HYSTERIA.type
            security = AppConfig.TLS
            sni = tls.string("server_name")
            alpn = tls.stringList("alpn").ifEmpty { listOf("h3") }.joinToString(",")
            fingerPrint = tls.utlsFingerprint()
            insecure = tls.bool("insecure") ?: false
            portHopping = portHoppingValue()
            portHoppingInterval = string("hop_interval")?.takeIf { it.isNotBlank() }
            bandwidthUp = mbpsValue("up_mbps")
            bandwidthDown = mbpsValue("down_mbps")
            obfsPassword = obfs?.string("password")?.takeIf { obfsType == "salamander" }
        }
    }

    private fun JsonObject.parseWireguard(): ProfileItem? {
        if (has("peers") || has("amnezia_wg_option")) return null
        val endpoint = endpoint() ?: return null
        val privateKey = string("private_key") ?: return null
        val publicKey = string("peer_public_key") ?: return null
        val localAddress = stringList("local_address").joinToString(",")
        if (localAddress.isEmpty()) return null

        return ProfileItem.create(EConfigType.WIREGUARD).apply {
            remarks = remark(endpoint)
            server = endpoint.host
            serverPort = endpoint.port.toString()
            secretKey = privateKey
            this.publicKey = publicKey
            preSharedKey = string("pre_shared_key")
            this.localAddress = localAddress
            mtu = int("mtu")?.takeIf { it > 0 } ?: AppConfig.WIREGUARD_LOCAL_MTU.toInt()
            reserved = reservedValue() ?: "0,0,0"
        }
    }

    private fun ProfileItem.applyCompatibleShadowsocksPlugin(outbound: JsonObject): Boolean {
        val plugin = outbound.string("plugin")?.trim()?.lowercase().orEmpty()
        if (plugin.isEmpty()) return true
        // Simple-obfs HTTP is the only plugin shape represented by an existing
        // MikuRay profile: Xray TCP transport with an HTTP header.
        if (plugin !in supportedSimpleObfsPlugins) return false

        val options = outbound.string("plugin_opts")
            ?.split(';')
            ?.mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) null else {
                    part.substring(0, separator).trim() to part.substring(separator + 1).trim()
                }
            }
            ?.toMap()
            .orEmpty()
        if (options["obfs"] != "http") return false

        network = NetworkType.TCP.type
        headerType = AppConfig.HEADER_TYPE_HTTP
        host = options["obfs-host"]?.takeIf { it.isNotBlank() }
        path = options["path"]?.takeIf { it.isNotBlank() }
        return true
    }

    private fun ProfileItem.applyTransportSettings(outbound: JsonObject): Boolean {
        val transport = outbound.obj("transport")
        return when (transport?.string("type")?.trim()?.lowercase()) {
            null, "", "tcp" -> {
                network = NetworkType.TCP.type
                true
            }

            "ws" -> {
                network = NetworkType.WS.type
                path = transport.string("path")
                host = transport.obj("headers")?.stringList("host")?.firstOrNull()
                wsEarlyData = transport.int("max_early_data")?.takeIf { it >= 0 }
                wsEarlyDataHeaderName = transport.string("early_data_header_name")?.takeIf { it.isNotBlank() }
                true
            }

            "http" -> {
                val tlsEnabled = outbound.obj("tls")?.bool("enabled") == true
                network = if (tlsEnabled) NetworkType.HTTP.type else NetworkType.TCP.type
                if (!tlsEnabled) headerType = AppConfig.HEADER_TYPE_HTTP
                path = transport.stringList("path").joinToString(",").takeIf { it.isNotEmpty() }
                host = transport.stringList("host").joinToString(",").takeIf { it.isNotEmpty() }
                true
            }

            "grpc" -> {
                network = NetworkType.GRPC.type
                serviceName = transport.string("service_name")
                authority = transport.string("authority")
                mode = transport.string("mode")?.takeIf { it == "multi" }
                true
            }

            "httpupgrade" -> {
                network = NetworkType.HTTP_UPGRADE.type
                path = transport.string("path")
                host = transport.string("host")
                true
            }

            "xhttp" -> {
                network = NetworkType.XHTTP.type
                path = transport.string("path")
                host = transport.string("host")
                xhttpMode = transport.string("mode")?.takeIf { it in supportedXhttpModes } ?: "auto"
                true
            }

            else -> false
        }
    }

    private fun ProfileItem.applyTlsSettings(outbound: JsonObject, trojan: Boolean): Boolean {
        val tls = outbound.obj("tls")
        if (tls?.hasUnsupportedTlsOptions() == true) return false
        val reality = tls?.obj("reality")
        if (reality?.bool("enabled") == true) {
            val key = reality.string("public_key")?.takeIf { it.isNotBlank() } ?: return false
            security = AppConfig.REALITY
            publicKey = key
            shortId = reality.string("short_id")
        } else if (trojan || tls?.bool("enabled") == true) {
            security = AppConfig.TLS
        } else {
            security = null
        }

        sni = tls?.string("server_name")
        alpn = tls?.stringList("alpn")?.joinToString(",")?.takeIf { it.isNotEmpty() }
        fingerPrint = tls?.utlsFingerprint()
        insecure = tls?.bool("insecure") ?: false
        return true
    }

    private fun JsonObject.utlsFingerprint(): String? {
        val utls = obj("utls") ?: return null
        return when (utls.bool("enabled")) {
            false -> "unsafe"
            else -> utls.string("fingerprint")?.takeIf { it.isNotBlank() }
        }
    }

    private fun JsonObject.hasUnsupportedCommonOptions(): Boolean =
        obj("multiplex")?.bool("enabled") == true || has("detour") || has("domain_resolver")

    private fun JsonObject.hasTlsOnlyOptions(): Boolean {
        val tls = obj("tls") ?: return false
        return tls.bool("enabled") == true || tls.hasUnsupportedTlsOptions()
            || tls.string("server_name")?.isNotBlank() == true || tls.bool("insecure") != null
    }

    private fun JsonObject.hasUnsupportedTlsOptions(): Boolean {
        return has("client_certificate") || has("client_key") || has("certificate_public_key_sha256")
            || has("ech") || bool("disable_sni") == true
    }

    private fun JsonObject.portHoppingValue(): String? {
        val values = stringList("server_ports")
        if (values.isNotEmpty()) return values.joinToString(",")
        return string("server_port_range")?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.mbpsValue(name: String): String? {
        val value = string(name)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return "$value".takeIf { value.toDoubleOrNull()?.let { it >= 0 } == true }?.plus("m")
    }

    private fun JsonObject.endpoint(): Endpoint? {
        val host = string("server")?.trim()?.removeSurrounding("[", "]") ?: return null
        val port = int("server_port") ?: return null
        if (host.isEmpty() || port !in 1..65535) return null
        return Endpoint(host, port)
    }

    private fun JsonObject.remark(endpoint: Endpoint): String =
        string("tag")?.trim()?.takeIf { it.isNotEmpty() } ?: "${endpoint.host}:${endpoint.port}"

    private fun JsonObject.value(name: String): JsonElement? =
        get(name) ?: entrySet().firstOrNull { entry -> entry.key.equals(name, ignoreCase = true) }?.value

    private fun JsonObject.string(name: String): String? {
        val value = value(name) ?: return null
        return value.takeIf { it.isJsonPrimitive }
            ?.asJsonPrimitive
            ?.takeIf { it.isString || it.isNumber || it.isBoolean }
            ?.asString
    }

    private fun JsonObject.int(name: String): Int? {
        val value = value(name) ?: return null
        return try {
            value.takeIf { it.isJsonPrimitive }?.asInt
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.bool(name: String): Boolean? {
        val value = value(name) ?: return null
        return try {
            value.takeIf { it.isJsonPrimitive }?.asBoolean
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.obj(name: String): JsonObject? =
        value(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.array(name: String): JsonArray? =
        value(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.stringList(name: String): List<String> {
        val value = value(name) ?: return emptyList()
        return when {
            value.isJsonArray -> value.asJsonArray.mapNotNull { item ->
                item.takeIf { it.isJsonPrimitive }
                    ?.asJsonPrimitive
                    ?.takeIf { it.isString || it.isNumber }
                    ?.asString
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
            value.isJsonPrimitive -> value.asJsonPrimitive
                .takeIf { it.isString || it.isNumber }
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(::listOf)
                .orEmpty()
            else -> emptyList()
        }
    }

    private fun JsonObject.reservedValue(): String? {
        val reserved = value("reserved") ?: return null
        return when {
            reserved.isJsonArray -> reserved.asJsonArray
                .mapNotNull { item ->
                    try {
                        item.takeIf { it.isJsonPrimitive }?.asInt
                    } catch (_: Exception) {
                        null
                    }
                }
                .takeIf { it.size == 3 && it.all { byte -> byte in 0..255 } }
                ?.joinToString(",")
            reserved.isJsonPrimitive -> decodeReserved(reserved.asString)
            else -> null
        }
    }

    private fun decodeReserved(value: String): String? {
        val text = value.trim()
        val decimal = text.split(',').mapNotNull { it.trim().toIntOrNull() }
        if (decimal.size == 3 && decimal.all { it in 0..255 }) return decimal.joinToString(",")

        val padded = text + "=".repeat((4 - text.length % 4) % 4)
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

    private val supportedSimpleObfsPlugins = setOf("obfs-local", "simple-obfs")
    private val supportedXhttpModes = setOf("auto", "packet-up", "stream-up", "stream-one")
}
