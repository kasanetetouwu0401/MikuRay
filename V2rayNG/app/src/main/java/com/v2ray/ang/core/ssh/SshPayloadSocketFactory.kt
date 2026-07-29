package com.v2ray.ang.core.ssh

import com.jcraft.jsch.SocketFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.SSLParameters
import javax.net.ssl.SNIHostName

/**
 * Opens the raw TCP socket to the SSH server ourselves (instead of letting JSch do it),
 * optionally through a SOCKS5 proxy and/or wrapped in TLS with a custom SNI, writes an
 * optional custom payload on it, and only then hands the socket back to JSch to continue
 * the normal SSH banner/handshake on top of it.
 *
 * This mirrors HTTP Injector's "SSH + custom payload / proxy / SNI" feature, used to slip
 * past DPI/quota systems that only inspect the first bytes of a connection, or to route
 * through an SNI-based reverse proxy in front of the real SSH server.
 */
class SshPayloadSocketFactory(
    private val targetHost: String,
    private val targetPort: Int,
    private val payloadTemplate: String?,
    private val proxyAddress: String? = null, // SOCKS5 proxy "ip:port" dialed before reaching targetHost:targetPort
    private val sni: String? = null, // when non-blank, wraps the socket in TLS with this SNI before the payload/handshake
    private val connectTimeoutMs: Int = 15000,
) : SocketFactory {

    override fun createSocket(host: String?, port: Int): Socket {
        val resolvedHost = host?.takeIf { it.isNotBlank() } ?: targetHost
        val resolvedPort = if (port > 0) port else targetPort

        var socket = openSocket(resolvedHost, resolvedPort)

        val sniHost = sni?.trim()
        if (!sniHost.isNullOrEmpty()) {
            socket = wrapTls(socket, resolvedHost, sniHost)
        }

        val payload = resolvePayload(payloadTemplate, resolvedHost, resolvedPort)
        if (payload.isNotEmpty()) {
            val out = socket.getOutputStream()
            out.write(payload.toByteArray(Charsets.ISO_8859_1))
            out.flush()
        }

        return socket
    }

    /** Opens the transport-level socket, either directly or through a SOCKS5 proxy. */
    private fun openSocket(resolvedHost: String, resolvedPort: Int): Socket {
        val proxy = proxyAddress?.trim()
        if (proxy.isNullOrEmpty()) {
            val socket = Socket()
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(resolvedHost, resolvedPort), connectTimeoutMs)
            return socket
        }

        val sepIdx = proxy.lastIndexOf(':')
        if (sepIdx <= 0) error("SSH: invalid proxy address \"$proxy\", expected ip:port")
        val proxyHost = proxy.substring(0, sepIdx)
        val proxyPort = proxy.substring(sepIdx + 1).toIntOrNull() ?: error("SSH: invalid proxy port in \"$proxy\"")

        val socket = Socket()
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(proxyHost, proxyPort), connectTimeoutMs)
        socket.soTimeout = connectTimeoutMs
        socks5Connect(socket, resolvedHost, resolvedPort)
        socket.soTimeout = 0
        return socket
    }

    /** Minimal SOCKS5 client handshake (no-auth) issuing a CONNECT to host:port. */
    private fun socks5Connect(socket: Socket, host: String, port: Int) {
        val out = DataOutputStream(socket.getOutputStream())
        val inp = DataInputStream(socket.getInputStream())

        // Greeting: SOCKS5, 1 method, no-auth
        out.write(byteArrayOf(0x05, 0x01, 0x00))
        out.flush()
        val greetReply = ByteArray(2)
        inp.readFully(greetReply)
        if (greetReply[0] != 0x05.toByte() || greetReply[1] != 0x00.toByte()) {
            error("SSH: SOCKS5 proxy did not accept no-auth (reply=${greetReply.joinToString()})")
        }

        // CONNECT request, address as domain name (works for both hostnames and IPs)
        val hostBytes = host.toByteArray(Charsets.US_ASCII)
        val req = mutableListOf<Byte>(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte())
        req.addAll(hostBytes.toList())
        req.add((port shr 8 and 0xFF).toByte())
        req.add((port and 0xFF).toByte())
        out.write(req.toByteArray())
        out.flush()

        val replyHeader = ByteArray(4)
        inp.readFully(replyHeader)
        if (replyHeader[1] != 0x00.toByte()) {
            error("SSH: SOCKS5 proxy CONNECT failed (code=${replyHeader[1]})")
        }
        // Skip the bound address in the reply (varies by address type)
        val skip = when (replyHeader[3].toInt()) {
            0x01 -> 4 // IPv4
            0x03 -> inp.readUnsignedByte() // domain: length-prefixed
            0x04 -> 16 // IPv6
            else -> error("SSH: SOCKS5 proxy returned unknown address type")
        }
        inp.skipBytes(skip)
        inp.skipBytes(2) // bound port
    }

    /** Wraps an already-connected plain socket in TLS, sending [sniHost] as the SNI. */
    private fun wrapTls(plain: Socket, connectHost: String, sniHost: String): Socket {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val ssl = factory.createSocket(plain, connectHost, plain.port, true)
        if (ssl is javax.net.ssl.SSLSocket) {
            val params = ssl.sslParameters ?: SSLParameters()
            params.serverNames = listOf(SNIHostName(sniHost))
            ssl.sslParameters = params
            ssl.startHandshake()
        }
        return ssl
    }

    override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()

    override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()

    companion object {
        /**
         * Resolves HTTP-Injector-style placeholders inside a custom payload template.
         * Supported tokens (case-insensitive): [host], [port], [crlf], [cr], [lf].
         *
         * Example template:
         *   GET / HTTP/1.1[crlf]Host: [host][crlf]Connection: Upgrade[crlf][crlf]
         */
        fun resolvePayload(template: String?, host: String, port: Int): String {
            if (template.isNullOrEmpty()) return ""
            return template
                .replace("[host]", host, ignoreCase = true)
                .replace("[port]", port.toString(), ignoreCase = true)
                .replace("[crlf]", "\r\n", ignoreCase = true)
                .replace("[cr]", "\r", ignoreCase = true)
                .replace("[lf]", "\n", ignoreCase = true)
        }
    }
}
