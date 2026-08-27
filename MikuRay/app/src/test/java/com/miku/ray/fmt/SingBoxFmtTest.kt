package com.miku.ray.fmt

import com.miku.ray.AppConfig
import com.miku.ray.enums.EConfigType
import com.miku.ray.enums.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxFmtTest {

    @Test
    fun parse_mapsReferenceStyleOutbounds() {
        val content = """
            {
              "outbounds": [
                {
                  "type": "vmess",
                  "tag": "VMess WS TLS",
                  "server": "vmess.example.com",
                  "server_port": 443,
                  "uuid": "123e4567-e89b-12d3-a456-426614174000",
                  "security": "aes-128-gcm",
                  "alter_id": 0,
                  "tls": {
                    "enabled": true,
                    "server_name": "edge.example.com",
                    "utls": { "enabled": true, "fingerprint": "chrome" }
                  },
                  "packet_encoding": "packetaddr",
                  "transport": {
                    "type": "ws",
                    "path": "/ws",
                    "headers": { "Host": "cdn.example.com" },
                    "max_early_data": 2048,
                    "early_data_header_name": "Sec-WebSocket-Protocol"
                  }
                },
                {
                  "type": "vless",
                  "tag": "VLESS Reality gRPC",
                  "server": "vless.example.com",
                  "server_port": 443,
                  "uuid": "123e4567-e89b-12d3-a456-426614174001",
                  "tls": {
                    "enabled": true,
                    "server_name": "reality.example.com",
                    "utls": { "enabled": true, "fingerprint": "firefox" },
                    "reality": {
                      "enabled": true,
                      "public_key": "public-key-value",
                      "short_id": "8ba85179e30d4fc2"
                    }
                  },
                  "transport": {
                    "type": "grpc",
                    "service_name": "grpc-vless",
                    "authority": "edge.example.com"
                  }
                },
                {
                  "type": "hysteria2",
                  "tag": "Hysteria Two",
                  "server": "hy2.example.com",
                  "server_port": 443,
                  "server_ports": ["443", "1000-2000"],
                  "hop_interval": "15-30",
                  "up_mbps": 20,
                  "down_mbps": 100,
                  "password": "hy2-secret",
                  "tls": {
                    "enabled": true,
                    "server_name": "hy2.example.com",
                    "alpn": ["h3", "h2"],
                    "utls": { "enabled": false },
                    "insecure": true
                  },
                  "obfs": { "type": "salamander", "password": "obfs-secret" }
                },
                {
                  "type": "wireguard",
                  "tag": "WireGuard",
                  "server": "wg.example.com",
                  "server_port": 51820,
                  "private_key": "private-key-value",
                  "peer_public_key": "public-key-value",
                  "pre_shared_key": "preshared-key-value",
                  "local_address": ["10.0.0.2/32", "fd00::2/128"],
                  "mtu": 1280,
                  "reserved": "U4An"
                }
              ]
            }
        """.trimIndent()

        val profiles = SingBoxFmt.parse(content)

        assertEquals(4, profiles.size)

        val vmess = profiles[0]
        assertEquals(EConfigType.VMESS, vmess.configType)
        assertEquals("VMess WS TLS", vmess.remarks)
        assertEquals("aes-128-gcm", vmess.method)
        assertEquals(NetworkType.WS.type, vmess.network)
        assertEquals("/ws", vmess.path)
        assertEquals("cdn.example.com", vmess.host)
        assertEquals(2048, vmess.wsEarlyData)
        assertEquals("Sec-WebSocket-Protocol", vmess.wsEarlyDataHeaderName)
        assertEquals(AppConfig.TLS, vmess.security)
        assertEquals("edge.example.com", vmess.sni)
        assertEquals("chrome", vmess.fingerPrint)

        val vless = profiles[1]
        assertEquals(EConfigType.VLESS, vless.configType)
        assertEquals(NetworkType.GRPC.type, vless.network)
        assertEquals("grpc-vless", vless.serviceName)
        assertEquals("edge.example.com", vless.authority)
        assertEquals(AppConfig.REALITY, vless.security)
        assertEquals("reality.example.com", vless.sni)
        assertEquals("firefox", vless.fingerPrint)
        assertEquals("public-key-value", vless.publicKey)
        assertEquals("8ba85179e30d4fc2", vless.shortId)

        val hysteria2 = profiles[2]
        assertEquals(EConfigType.HYSTERIA2, hysteria2.configType)
        assertEquals(NetworkType.HYSTERIA.type, hysteria2.network)
        assertEquals("443,1000-2000", hysteria2.portHopping)
        assertEquals("15-30", hysteria2.portHoppingInterval)
        assertEquals("20m", hysteria2.bandwidthUp)
        assertEquals("100m", hysteria2.bandwidthDown)
        assertEquals("h3,h2", hysteria2.alpn)
        assertEquals("unsafe", hysteria2.fingerPrint)
        assertTrue(hysteria2.insecure == true)
        assertEquals("obfs-secret", hysteria2.obfsPassword)

        val wireguard = profiles[3]
        assertEquals(EConfigType.WIREGUARD, wireguard.configType)
        assertEquals("10.0.0.2/32,fd00::2/128", wireguard.localAddress)
        assertEquals("83,128,39", wireguard.reserved)
        assertEquals(1280, wireguard.mtu)
    }

    @Test
    fun parse_mapsHttpUpgradeAndSingleOutboundDocument() {
        val content = """
            {
              "type": "trojan",
              "tag": "Single Trojan",
              "server": "trojan.example.com",
              "server_port": 443,
              "password": "secret",
              "tls": {
                "enabled": true,
                "server_name": "edge.example.com",
                "alpn": ["h2"]
              },
              "transport": {
                "type": "httpupgrade",
                "host": "upgrade.example.com",
                "path": "/upgrade"
              }
            }
        """.trimIndent()

        val profile = SingBoxFmt.parse(content).single()

        assertEquals(EConfigType.TROJAN, profile.configType)
        assertEquals(NetworkType.HTTP_UPGRADE.type, profile.network)
        assertEquals("/upgrade", profile.path)
        assertEquals("upgrade.example.com", profile.host)
        assertEquals(AppConfig.TLS, profile.security)
        assertEquals("edge.example.com", profile.sni)
        assertEquals("h2", profile.alpn)
    }

    @Test
    fun parse_skipsUnsupportedAndUnsafeOutbounds() {
        val content = """
            {
              "outbounds": [
                {
                  "type": "tuic",
                  "server": "tuic.example.com",
                  "server_port": 443,
                  "uuid": "ignored",
                  "password": "ignored"
                },
                {
                  "type": "socks",
                  "server": "socks.example.com",
                  "server_port": 1080,
                  "version": "4"
                },
                {
                  "type": "hysteria2",
                  "server": "hy2.example.com",
                  "server_port": 443,
                  "tls": { "enabled": false }
                },
                {
                  "type": "vless",
                  "server": "unsupported-transport.example.com",
                  "server_port": 443,
                  "uuid": "123e4567-e89b-12d3-a456-426614174002",
                  "transport": { "type": "quic" }
                },
                {
                  "type": "trojan",
                  "server": "multiplex.example.com",
                  "server_port": 443,
                  "password": "secret",
                  "multiplex": { "enabled": true }
                },
                {
                  "type": "wireguard",
                  "private_key": "private-key-value",
                  "local_address": ["10.0.0.2/32"],
                  "peers": []
                }
              ]
            }
        """.trimIndent()

        assertTrue(SingBoxFmt.parse(content).isEmpty())
        assertTrue(SingBoxFmt.parse("not-json").isEmpty())
        assertTrue(SingBoxFmt.parse("{\"outbounds\": []}").isEmpty())
    }
}
