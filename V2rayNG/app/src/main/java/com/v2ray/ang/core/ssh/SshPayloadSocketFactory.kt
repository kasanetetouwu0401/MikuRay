package com.v2ray.ang.core.ssh

import com.jcraft.jsch.SocketFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Opens the raw TCP socket to the SSH server ourselves (instead of letting JSch do it),
 * writes an optional custom payload on it, and only then hands the socket back to JSch
 * to continue the normal SSH banner/handshake on top of it.
 *
 * This mirrors HTTP Injector's "SSH + custom payload" feature: the payload is a raw
 * HTTP-looking request sent before SSH even starts, used to slip past DPI/quota systems
 * that only inspect the first bytes of a connection.
 */
class SshPayloadSocketFactory(
    private val targetHost: String,
    private val targetPort: Int,
    private val payloadTemplate: String?,
    private val connectTimeoutMs: Int = 15000,
) : SocketFactory {

    override fun createSocket(host: String?, port: Int): Socket {
        val resolvedHost = host?.takeIf { it.isNotBlank() } ?: targetHost
        val resolvedPort = if (port > 0) port else targetPort

        val socket = Socket()
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(resolvedHost, resolvedPort), connectTimeoutMs)

        val payload = resolvePayload(payloadTemplate, resolvedHost, resolvedPort)
        if (payload.isNotEmpty()) {
            val out = socket.getOutputStream()
            out.write(payload.toByteArray(Charsets.ISO_8859_1))
            out.flush()
        }

        return socket
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
