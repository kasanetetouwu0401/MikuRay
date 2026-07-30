package com.v2ray.ang.core.ssh

import com.v2ray.ang.util.LogUtil
import net.schmizz.sshj.SSHClient
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * A tiny SOCKS5 server (no-auth only, CONNECT command only — everything MikuRay's own Xray
 * outbound needs) that hands each accepted connection off to an SSH direct-tcpip channel.
 * This is the manual equivalent of `ssh -D <port>` since sshj has no built-in dynamic
 * forwarder.
 */
class MiniSocks5Server(private val sshClient: SSHClient) {

    private var serverSocket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    @Volatile
    private var running = false

    val isRunning: Boolean get() = running
    val localPort: Int get() = serverSocket?.localPort ?: 0

    fun start() {
        val socket = ServerSocket(0, 128, InetAddress.getByName(LOOPBACK))
        serverSocket = socket
        running = true
        pool.execute {
            while (running) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    if (running) LogUtil.e(TAG, "accept() failed: ${e.message}", e)
                    break
                }
                pool.execute { handleClient(client) }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        pool.shutdownNow()
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = HANDSHAKE_TIMEOUT_MS
            val input = client.getInputStream()
            val output = client.getOutputStream()

            if (!doGreeting(input, output)) {
                client.close()
                return
            }
            val target = readConnectRequest(input, output) ?: run {
                client.close()
                return
            }

            client.soTimeout = 0
            val tunnel = sshClient.newDirectConnection(target.first, target.second)
            pump(client, tunnel)
        } catch (e: Exception) {
            LogUtil.e(TAG, "SOCKS client handling failed: ${e.message}", e)
            runCatching { client.close() }
        }
    }

    /** SOCKS5 greeting: pick "no authentication required" unconditionally. */
    private fun doGreeting(input: InputStream, output: OutputStream): Boolean {
        val ver = input.read()
        if (ver != 0x05) return false
        val nMethods = input.read()
        if (nMethods < 0) return false
        val methods = ByteArray(nMethods)
        readFully(input, methods)
        output.write(byteArrayOf(0x05, 0x00))
        output.flush()
        return true
    }

    /** Reads a SOCKS5 CONNECT request and replies with success; returns the requested host:port. */
    private fun readConnectRequest(input: InputStream, output: OutputStream): Pair<String, Int>? {
        val ver = input.read()
        val cmd = input.read()
        input.read() // reserved
        val addrType = input.read()
        if (ver != 0x05 || cmd != 0x01) {
            replyError(output, 0x07) // command not supported
            return null
        }

        val host: String = when (addrType) {
            0x01 -> { // IPv4
                val addr = ByteArray(4)
                readFully(input, addr)
                InetAddress.getByAddress(addr).hostAddress ?: return null
            }

            0x03 -> { // domain name
                val len = input.read()
                if (len <= 0) return null
                val nameBytes = ByteArray(len)
                readFully(input, nameBytes)
                String(nameBytes, Charsets.US_ASCII)
            }

            0x04 -> { // IPv6
                val addr = ByteArray(16)
                readFully(input, addr)
                InetAddress.getByAddress(addr).hostAddress ?: return null
            }

            else -> {
                replyError(output, 0x08) // address type not supported
                return null
            }
        }

        val portHi = input.read()
        val portLo = input.read()
        if (portHi < 0 || portLo < 0) return null
        val port = (portHi shl 8) or portLo

        // Success reply; bound address is irrelevant to SOCKS clients that only use CONNECT.
        output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        output.flush()
        return host to port
    }

    private fun replyError(output: OutputStream, code: Int) {
        runCatching {
            output.write(byteArrayOf(0x05, code.toByte(), 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            output.flush()
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var off = 0
        while (off < buffer.size) {
            val n = input.read(buffer, off, buffer.size - off)
            if (n < 0) throw IOException("Unexpected EOF while reading SOCKS request")
            off += n
        }
    }

    /** Bidirectional byte pump between the local SOCKS client and the SSH direct-tcpip channel. */
    private fun pump(client: Socket, tunnel: net.schmizz.sshj.connection.channel.direct.DirectConnection) {
        val closeBoth = {
            runCatching { client.close() }
            runCatching { tunnel.close() }
        }

        pool.execute {
            try {
                copy(client.getInputStream(), tunnel.outputStream)
            } catch (_: IOException) {
            } finally {
                closeBoth()
            }
        }
        try {
            copy(tunnel.inputStream, client.getOutputStream())
        } catch (_: IOException) {
        } finally {
            closeBoth()
        }
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
            output.flush()
        }
    }

    companion object {
        private const val TAG = "MiniSocks5Server"
        private const val LOOPBACK = "127.0.0.1"
        private const val HANDSHAKE_TIMEOUT_MS = 10_000
        private const val COPY_BUFFER_SIZE = 8192
    }
}
