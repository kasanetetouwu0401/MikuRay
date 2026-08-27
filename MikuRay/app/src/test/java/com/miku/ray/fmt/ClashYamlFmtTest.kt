package com.miku.ray.fmt

import com.miku.ray.AppConfig
import com.miku.ray.enums.EConfigType
import com.miku.ray.enums.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClashYamlFmtTest {

    @Test
    fun parse_mapsCompatibleProfilesAndTransports() {
        val content = """
            proxies:
              - name: !<str> Singapore SS
                type: ss
                server: ss.example.com
                port: 8388
                cipher: aead_chacha20_poly1305
                password: super-secret
                plugin: obfs
                plugin-opts:
                  mode: http
                  host: cdn.example.com
                  path: /health
              - name: VLESS Reality
                type: vless
                server: vless.example.com
                port: 443
                uuid: 123e4567-e89b-12d3-a456-426614174000
                network: grpc
                grpc-opts:
                  grpc-service-name: grpc-vless
                  authority: edge.example.com
                servername: reality.example.com
                client-fingerprint: chrome
                reality-opts:
                  public-key: public-key-value
                  short-id: 8ba85179e30d4fc2
              - name: Hysteria Two
                type: hysteria2
                server: hy2.example.com
                port: 443
                password: hy2-secret
                sni: hy2.example.com
                alpn: [h3, h2]
                obfs: salamander
                obfs-password: obfs-secret
              - name: WireGuard
                type: wireguard
                server: wg.example.com
                port: 51820
                private-key: private-key-value
                public-key: public-key-value
                pre-shared-key: preshared-key-value
                ip: 10.0.0.2/32
                ipv6: fd00::2/128
                mtu: 1280
                reserved: [1, 2, 3]
        """.trimIndent()

        val profiles = ClashYamlFmt.parse(content)

        assertEquals(4, profiles.size)

        val shadowsocks = profiles[0]
        assertEquals(EConfigType.SHADOWSOCKS, shadowsocks.configType)
        assertEquals("Singapore SS", shadowsocks.remarks)
        assertEquals("chacha20-ietf-poly1305", shadowsocks.method)
        assertEquals(NetworkType.TCP.type, shadowsocks.network)
        assertEquals(AppConfig.HEADER_TYPE_HTTP, shadowsocks.headerType)
        assertEquals("cdn.example.com", shadowsocks.host)
        assertEquals("/health", shadowsocks.path)

        val vless = profiles[1]
        assertEquals(EConfigType.VLESS, vless.configType)
        assertEquals(NetworkType.GRPC.type, vless.network)
        assertEquals("grpc-vless", vless.serviceName)
        assertEquals("edge.example.com", vless.authority)
        assertEquals(AppConfig.REALITY, vless.security)
        assertEquals("reality.example.com", vless.sni)
        assertEquals("public-key-value", vless.publicKey)
        assertEquals("8ba85179e30d4fc2", vless.shortId)

        val hysteria2 = profiles[2]
        assertEquals(EConfigType.HYSTERIA2, hysteria2.configType)
        assertEquals(NetworkType.HYSTERIA.type, hysteria2.network)
        assertEquals("h3,h2", hysteria2.alpn)
        assertEquals("obfs-secret", hysteria2.obfsPassword)

        val wireguard = profiles[3]
        assertEquals(EConfigType.WIREGUARD, wireguard.configType)
        assertEquals("10.0.0.2/32\nfd00::2/128", wireguard.localAddress)
        assertEquals("1,2,3", wireguard.reserved)
        assertEquals(1280, wireguard.mtu)
    }

    @Test
    fun parse_skipsUnsupportedAndInvalidProfiles() {
        val content = """
            proxies:
              - name: Unsupported TUIC
                type: tuic
                server: tuic.example.com
                port: 443
                uuid: ignored
                password: ignored
              - name: Unsupported SS plugin
                type: ss
                server: plugin.example.com
                port: 8388
                cipher: aes-256-gcm
                password: secret
                plugin: v2ray-plugin
              - name: Invalid port
                type: trojan
                server: invalid.example.com
                port: 0
                password: secret
              - name: Incomplete reality
                type: vless
                server: reality.example.com
                port: 443
                uuid: 123e4567-e89b-12d3-a456-426614174000
                reality-opts:
                  short-id: abcdef
        """.trimIndent()

        assertTrue(ClashYamlFmt.parse(content).isEmpty())
    }

    @Test
    fun parse_rejectsNonClashDocumentsAndMalformedYaml() {
        assertTrue(ClashYamlFmt.parse("proxies: []").isEmpty())
        assertTrue(ClashYamlFmt.parse("outbounds: []").isEmpty())
        assertTrue(ClashYamlFmt.parse("proxies: [").isEmpty())
    }
}
