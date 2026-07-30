package com.v2ray.ang.core.ssh

import android.util.Base64
import com.v2ray.ang.enums.ESshMode
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.SocketFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Builds the raw socket that sshj then speaks the SSH protocol over.
 *
 * Depending on [mode], the socket is layered as:
 * 1. TCP connect — straight to the SSH host, or to [proxyHost]/[proxyPort] first.
 * 2. HTTP CONNECT through the proxy (SSH_SSL_PAYLOAD_PROXY only).
 * 3. TLS wrap (SSH_SSL_PAYLOAD / SSH_SSL_PAYLOAD_PROXY).
 * 4. "Payload" injection — an HTTP-request-shaped string sent before the SSH handshake,
 *    the classic bug-host / DPI-evasion trick used by SSH-injector apps.
 *
 * Note: the TLS wrap here intentionally trusts any certificate. Apps that use this
 * "SSH + SSL" trick generally point at bug hosts / CDNs with unrelated or self-signed
 * certs — the TLS layer here is for shape/obfuscation, not for authenticating the peer.
 * Real transport security is provided by the SSH layer on top (host key + auth).
 */
class PayloadSocketFactory(
    private val mode: ESshMode,
    private val targetHost: String,
    private val targetPort: Int,
    private val sni: String,
    private val payloadTemplate: String,
    private val proxyHost: String,
    private val proxyPort: Int,
    private val proxyUsername: String?,
    private val proxyPassword: String?,
    private val connectTimeoutMs: Int,
) : SocketFactory() {

    override fun createSocket(): Socket = ChainedSocket()

    override fun createSocket(host: String?, port: Int): Socket =
        ChainedSocket().apply { connect(InetSocketAddress(host, port), connectTimeoutMs) }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        createSocket(host, port)

    override fun createSocket(host: InetAddress?, port: Int): Socket =
        createSocket(host?.hostAddress, port)

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        createSocket(address, port)

    /**
     * A [Socket] whose real connection is only established (and layered) once [connect] runs;
     * every other call is delegated to whichever inner socket is currently active. sshj only
     * ever uses the input/output streams and a handful of state getters after connecting, so a
     * full override isn't needed.
     */
    private inner class ChainedSocket : Socket() {
        private lateinit var active: Socket

        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            val effectiveTimeout = if (timeout > 0) timeout else connectTimeoutMs

            val firstHopHost = if (mode.usesProxy) proxyHost else targetHost
            val firstHopPort = if (mode.usesProxy) proxyPort else targetPort

            var sock: Socket = Socket()
            sock.connect(InetSocketAddress(firstHopHost, firstHopPort), effectiveTimeout)
            sock.soTimeout = effectiveTimeout

            // When going through a proxy fronted by a CDN edge (e.g. a Cloudflare IP on
            // port 443), that edge expects TLS immediately — sending a plaintext CONNECT
            // before the TLS handshake gets rejected (Cloudflare replies with a "plain HTTP
            // request sent to HTTPS port" 400). So TLS wraps the hop to the proxy first, and
            // the CONNECT request travels *inside* that TLS tunnel, exactly like a normal
            // HTTPS forward proxy.
            if (mode.usesSsl) {
                sock = wrapTls(sock)
            }
            if (mode.usesProxy) {
                sock = httpConnect(sock)
            }
            if (mode.usesPayload) {
                sendPayload(sock)
            }

            sock.soTimeout = 0
            active = sock
        }

        override fun getInputStream(): InputStream = active.getInputStream()
        override fun getOutputStream() = active.getOutputStream()
        override fun isConnected(): Boolean = ::active.isInitialized && active.isConnected
        override fun isClosed(): Boolean = !::active.isInitialized || active.isClosed
        override fun close() {
            if (::active.isInitialized) active.close()
        }

        override fun setSoTimeout(timeout: Int) {
            if (::active.isInitialized) active.soTimeout = timeout
        }

        override fun getSoTimeout(): Int = if (::active.isInitialized) active.soTimeout else 0
        override fun setTcpNoDelay(on: Boolean) {
            if (::active.isInitialized) active.tcpNoDelay = on
        }

        override fun setKeepAlive(on: Boolean) {
            if (::active.isInitialized) active.keepAlive = on
        }

        override fun shutdownOutput() {
            if (::active.isInitialized) active.shutdownOutput()
        }

        override fun shutdownInput() {
            if (::active.isInitialized) active.shutdownInput()
        }
    }

    private fun httpConnect(raw: Socket): Socket {
        val authHeader = if (!proxyUsername.isNullOrEmpty()) {
            val token = Base64.encodeToString("$proxyUsername:${proxyPassword.orEmpty()}".toByteArray(), Base64.NO_WRAP)
            "Proxy-Authorization: Basic $token\r\n"
        } else {
            ""
        }
        val request = "CONNECT $targetHost:$targetPort HTTP/1.1\r\n" +
            "Host: $targetHost:$targetPort\r\n" +
            authHeader +
            "Connection: Keep-Alive\r\n\r\n"
        raw.getOutputStream().write(request.toByteArray())
        raw.getOutputStream().flush()

        val statusLine = readLine(raw.getInputStream())
        if (!statusLine.contains(" 200 ")) {
            raw.close()
            error("Proxy CONNECT failed: $statusLine")
        }
        while (readLine(raw.getInputStream()).isNotEmpty()) {
            // drain remaining proxy response headers
        }
        return raw
    }

    private fun wrapTls(raw: Socket): Socket {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(TrustAllManager()), SecureRandom())
        val sslSocket = sslContext.socketFactory.createSocket(raw, sni, raw.port, true) as SSLSocket
        val params = sslSocket.sslParameters
        params.serverNames = listOf(SNIHostName(sni))
        sslSocket.sslParameters = params
        sslSocket.startHandshake()
        return sslSocket
    }

    private fun sendPayload(sock: Socket) {
        if (payloadTemplate.isBlank()) return
        val rendered = payloadTemplate
            .replace("[host_port]", "$targetHost:$targetPort")
            .replace("[host]", targetHost)
            .replace("[port]", targetPort.toString())
            .replace("[crlf]", "\r\n")
        sock.getOutputStream().write(rendered.toByteArray())
        sock.getOutputStream().flush()

        // Best-effort drain of an HTTP-style response, if the bug host sends one. Some
        // bug hosts reply nothing at all before handing off to SSH, hence the short timeout.
        val originalTimeout = sock.soTimeout
        try {
            sock.soTimeout = PAYLOAD_RESPONSE_TIMEOUT_MS
            val head = readLine(sock.getInputStream())
            if (head.isNotEmpty()) {
                while (readLine(sock.getInputStream()).isNotEmpty()) {
                    // drain remaining headers
                }
            }
        } catch (_: SocketTimeoutException) {
            // No HTTP-style response — proceed straight to the SSH handshake.
        } finally {
            sock.soTimeout = originalTimeout
        }
    }

    private fun readLine(input: InputStream): String {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1 || b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    private class TrustAllManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    companion object {
        private const val PAYLOAD_RESPONSE_TIMEOUT_MS = 5000
    }
}
