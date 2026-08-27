package com.miku.ray.fmt

import com.google.gson.JsonParser
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
                ports: 1000,2000-3000
                hop-interval: 15-30
                up: 30 Mbps
                down: 200 Mbps
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
                reserved: U4An
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
        assertEquals("1000,2000-3000", hysteria2.portHopping)
        assertEquals("15-30", hysteria2.portHoppingInterval)
        assertEquals("30m", hysteria2.bandwidthUp)
        assertEquals("200m", hysteria2.bandwidthDown)

        val wireguard = profiles[3]
        assertEquals(EConfigType.WIREGUARD, wireguard.configType)
        assertEquals("10.0.0.2/32,fd00::2/128", wireguard.localAddress)
        assertEquals("83,128,39", wireguard.reserved)
        assertEquals(1280, wireguard.mtu)
    }

    @Test
    fun parse_mapsAdvancedMihomoOptionsThatMikuRaySupports() {
        val fingerprint = "e8:e2:d3:87:fd:bf:fe:b3:8e:9c:90:65:cf:30:a9:7e:e2:3c:0e:3d:32:ee:6f:78:ff:ae:40:96:6b:ef:cc:c9"
        val content = """
            proxies:
              - name: VMess mKCP
                type: vmess
                server: vmess.example.com
                port: 443
                uuid: 123e4567-e89b-12d3-a456-426614174001
                alterId: 0
                cipher: auto
                tls: true
                servername: tls.example.com
                client-fingerprint: firefox
                fingerprint: $fingerprint
                skip-cert-verify: true
                name-cert-verify: cert.example.com
                ech-opts:
                  enable: true
                  config: AEnExampleConfig
                network: mkcp
                mkcp-opts:
                  mtu: 1350
                  tti: 50
                  seed: seeded
                  header: wechat
              - name: VLESS xHTTP
                type: vless
                server: xhttp.example.com
                port: 443
                uuid: 123e4567-e89b-12d3-a456-426614174002
                tls: true
                network: xhttp
                xhttp-opts:
                  path: /xhttp
                  host: edge.example.com
                  mode: stream-one
                  no-grpc-header: false
                  x-padding-bytes: 100-1000
                  uplink-http-method: POST
                  session-placement: path
                  session-key: sid
                  reuse-settings:
                    max-concurrency: 16-32
                    h-max-request-times: 600-900
        """.trimIndent()

        val profiles = ClashYamlFmt.parse(content)

        assertEquals(2, profiles.size)

        val vmess = profiles[0]
        assertEquals(EConfigType.VMESS, vmess.configType)
        assertEquals(NetworkType.KCP.type, vmess.network)
        assertEquals(1350, vmess.kcpMtu)
        assertEquals(50, vmess.kcpTti)
        assertEquals("seeded", vmess.seed)
        assertEquals("wechat-video", vmess.headerType)
        assertEquals(AppConfig.TLS, vmess.security)
        assertEquals("tls.example.com", vmess.sni)
        assertEquals("firefox", vmess.fingerPrint)
        assertEquals(fingerprint, vmess.pinnedCA256)
        assertEquals("AEnExampleConfig", vmess.echConfigList)
        assertTrue(vmess.insecure == true)
        assertEquals("cert.example.com", vmess.verifyPeerCertByName)

        val vless = profiles[1]
        assertEquals(EConfigType.VLESS, vless.configType)
        assertEquals(NetworkType.XHTTP.type, vless.network)
        assertEquals("/xhttp", vless.path)
        assertEquals("edge.example.com", vless.host)
        assertEquals("stream-one", vless.xhttpMode)
        val xhttpExtra = JsonParser.parseString(vless.xhttpExtra).asJsonObject
        assertEquals(false, xhttpExtra["noGRPCHeader"].asBoolean)
        assertEquals("100-1000", xhttpExtra["xPaddingBytes"].asString)
        assertEquals("POST", xhttpExtra["uplinkHTTPMethod"].asString)
        assertEquals("path", xhttpExtra["sessionIDPlacement"].asString)
        assertEquals("sid", xhttpExtra["sessionIDKey"].asString)
        assertEquals("16-32", xhttpExtra["xmux"].asJsonObject["maxConcurrency"].asString)
        assertEquals("600-900", xhttpExtra["xmux"].asJsonObject["hMaxRequestTimes"].asString)
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
              - name: Unsupported SS over TLS
                type: ss
                server: tls-plugin.example.com
                port: 8388
                cipher: aes-256-gcm
                password: secret
                plugin: obfs
                plugin-opts:
                  mode: tls
              - name: Unsupported TLSMirror
                type: vmess
                server: vmess.example.com
                port: 443
                uuid: 123e4567-e89b-12d3-a456-426614174003
                tlsmirror-opts:
                  primary-key: test
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
              - name: Multi-peer WireGuard
                type: wireguard
                private-key: private-key-value
                ip: 10.0.0.2/32
                peers:
                  - server: wg.example.com
                    port: 51820
                    public-key: public-key-value
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
