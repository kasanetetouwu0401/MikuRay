package com.v2ray.ang.core.ssh

import com.jcraft.jsch.Session
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal local SOCKS5 server that tunnels each accepted connection through the SSH
 * session using a JSch "direct-tcpip" channel.
 *
 * JSch does not implement OpenSSH's `-D` dynamic/SOCKS forwarding natively (it only has
 * setPortForwardingL / setPortForwardingR), so this class provides the equivalent by hand:
 * it speaks just enough of the SOCKS5 protocol (RFC 1928) to read the requested
 * destination host/port, then opens a `direct-tcpip` channel to that destination over the
 * existing SSH session and pipes bytes in both directions.
 *
 * Only the CONNECT command and "no authentication" method are supported, which is all
 * Xray's socks outbound needs.
 */
class Socks5OverSsh(private val session: Session) {

    private val executor = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)

    @Volatile
    var localPort: Int = 0
        private set

    @Throws(IOException::class)
    fun start(bindAddress: String, port: Int): Int {
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(bindAddress, port))
        serverSocket = ss
        localPort = ss.localPort
        running.set(true)

        executor.execute { acceptLoop(ss) }
        return localPort
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null
        executor.shutdownNow()
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (running.get()) {
            val client = try {
                ss.accept()
            } catch (_: IOException) {
                if (!running.get()) return else continue
            }
            executor.execute { handleClient(client) }
        }
    }

    private fun handleClient(client: Socket) {
        try {
            client.tcpNoDelay = true
            val input = client.getInputStream()
            val output = client.getOutputStream()

            if (!performGreeting(input, output)) {
                client.close()
                return
            }

            val target = readConnectRequest(input, output) ?: run {
                client.close()
                return
            }

            relayThroughSsh(client, input, output, target.first, target.second)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Socks5OverSsh: client handling failed", e)
            try {
                client.close()
            } catch (_: IOException) {
            }
        }
    }

    /** Reads the SOCKS5 method-selection message and replies with "no authentication". */
    private fun performGreeting(input: InputStream, output: OutputStream): Boolean {
        val version = input.read()
        if (version != 0x05) return false
        val nMethods = input.read()
        if (nMethods < 0) return false
        val methods = ByteArray(nMethods)
        readFully(input, methods)
        // We only support "no authentication required" (0x00), which is all Xray requests.
        output.write(byteArrayOf(0x05, 0x00))
        output.flush()
        return true
    }

    /**
     * Reads the SOCKS5 request and returns (host, port) for CONNECT requests, replying
     * with a success/failure status as appropriate. Returns null on error or if the
     * command isn't CONNECT.
     */
    private fun readConnectRequest(input: InputStream, output: OutputStream): Pair<String, Int>? {
        val header = ByteArray(4)
        readFully(input, header)
        val version = header[0].toInt() and 0xFF
        val command = header[1].toInt() and 0xFF
        val addressType = header[3].toInt() and 0xFF

        if (version != 0x05 || command != 0x01 /* CONNECT */) {
            replyStatus(output, 0x07) // command not supported
            return null
        }

        val host: String
        when (addressType) {
            0x01 -> { // IPv4
                val addr = ByteArray(4)
                readFully(input, addr)
                host = "${addr[0].toInt() and 0xFF}.${addr[1].toInt() and 0xFF}." +
                    "${addr[2].toInt() and 0xFF}.${addr[3].toInt() and 0xFF}"
            }

            0x03 -> { // domain name
                val len = input.read()
                if (len < 0) {
                    replyStatus(output, 0x01)
                    return null
                }
                val nameBytes = ByteArray(len)
                readFully(input, nameBytes)
                host = String(nameBytes, Charsets.US_ASCII)
            }

            0x04 -> { // IPv6
                val addr = ByteArray(16)
                readFully(input, addr)
                host = java.net.InetAddress.getByAddress(addr).hostAddress ?: run {
                    replyStatus(output, 0x01)
                    return null
                }
            }

            else -> {
                replyStatus(output, 0x08) // address type not supported
                return null
            }
        }

        val portBytes = ByteArray(2)
        readFully(input, portBytes)
        val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

        return host to port
    }

    private fun replyStatus(output: OutputStream, status: Int) {
        // A minimal, mostly-ignored bind address/port (0.0.0.0:0) is fine here: Xray/SOCKS
        // clients only care about the status byte for CONNECT replies.
        output.write(byteArrayOf(0x05, status.toByte(), 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun relayThroughSsh(
        client: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        host: String,
        port: Int,
    ) {
        val channel = try {
            session.openChannel("direct-tcpip") as com.jcraft.jsch.ChannelDirectTCPIP
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Socks5OverSsh: failed to open direct-tcpip channel", e)
            replyStatus(clientOut, 0x01)
            client.close()
            return
        }

        channel.setHost(host)
        channel.setPort(port)
        // Origin address doesn't matter to most servers; loopback is a safe default.
        channel.setOrgIPAddress("127.0.0.1")
        channel.setOrgPort(0)

        // JSch requires the channel's streams to be obtained before connect() is called.
        val channelIn: InputStream
        val channelOut: OutputStream
        try {
            channelIn = channel.inputStream
            channelOut = channel.outputStream
            channel.connect(15000)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Socks5OverSsh: direct-tcpip connect failed for $host:$port", e)
            replyStatus(clientOut, 0x05) // connection refused
            client.close()
            return
        }

        replyStatus(clientOut, 0x00) // succeeded

        val clientToChannel = executor.submit {
            pump(clientIn, channelOut)
            try {
                channel.disconnect()
            } catch (_: Exception) {
            }
        }
        val channelToClient = executor.submit {
            pump(channelIn, clientOut)
            try {
                client.close()
            } catch (_: IOException) {
            }
        }

        try {
            clientToChannel.get()
            channelToClient.get()
        } catch (_: Exception) {
        }
    }

    private fun pump(from: InputStream, to: OutputStream) {
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val read = from.read(buffer)
                if (read == -1) break
                to.write(buffer, 0, read)
                to.flush()
            }
        } catch (_: IOException) {
            // Peer closed or channel torn down; nothing more to do.
        }
    }

    @Throws(IOException::class)
    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read == -1) throw IOException("Unexpected end of stream")
            offset += read
        }
    }
}
