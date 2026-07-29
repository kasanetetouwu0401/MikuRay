package com.v2ray.ang.core.ssh

import com.jcraft.jsch.Session
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
 * The CONNECT command always works (all Xray's socks outbound needs for TCP). UDP ASSOCIATE
 * is also supported, but only when [udpgwAddress] is set: SSH itself carries no UDP, so UDP
 * datagrams are relayed through a Neko Injector/HTTP Injector-style udpgw server reachable
 * over the SSH tunnel, via [UdpgwClient].
 */
class Socks5OverSsh(
    private val session: Session,
    private val udpgwAddress: String? = null,
) {

    private val executor = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)

    private val nextConId = AtomicInteger(1)
    private val udpgwClient: UdpgwClient? by lazy {
        val addr = udpgwAddress ?: return@lazy null
        val sepIdx = addr.lastIndexOf(':')
        if (sepIdx <= 0) return@lazy null
        val host = addr.substring(0, sepIdx)
        val port = addr.substring(sepIdx + 1).toIntOrNull() ?: return@lazy null
        UdpgwClient(session, host, port)
    }

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
        udpgwClient?.close()
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

            val request = readRequest(input, output) ?: run {
                client.close()
                return
            }

            if (request.command == 0x03) {
                handleUdpAssociate(client, output)
            } else {
                relayThroughSsh(client, input, output, request.host, request.port)
            }
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

    private data class Socks5Request(val command: Int, val host: String, val port: Int)

    /**
     * Reads a SOCKS5 request and returns (command, host, port). Supports CONNECT (0x01)
     * always, and UDP ASSOCIATE (0x03) only when a udpgw server is configured — replying
     * with a success/failure status as appropriate. Returns null on error or an
     * unsupported command.
     */
    private fun readRequest(input: InputStream, output: OutputStream): Socks5Request? {
        val header = ByteArray(4)
        readFully(input, header)
        val version = header[0].toInt() and 0xFF
        val command = header[1].toInt() and 0xFF
        val addressType = header[3].toInt() and 0xFF

        val commandSupported = command == 0x01 /* CONNECT */ || (command == 0x03 /* UDP ASSOCIATE */ && udpgwClient != null)
        if (version != 0x05 || !commandSupported) {
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

        return Socks5Request(command, host, port)
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

    /**
     * Implements SOCKS5 UDP ASSOCIATE (RFC 1928 §4, command 0x03): opens a local UDP relay
     * socket, tells the client (Xray) to send its datagrams there wrapped in a SOCKS5 UDP
     * request header, and forwards each one's payload to [UdpgwClient] keyed by a conId
     * per distinct (destHost, destPort) seen on this association. Replies from the udpgw
     * server are wrapped back into a SOCKS5 UDP response header and sent to whichever local
     * address last used that conId.
     *
     * The TCP control connection ([client]) must stay open for the life of the association
     * per the SOCKS5 spec; a background thread watches it to know when to tear the relay down.
     */
    private fun handleUdpAssociate(client: Socket, clientOut: OutputStream) {
        val gw = udpgwClient
        if (gw == null) {
            replyStatus(clientOut, 0x07)
            client.close()
            return
        }

        val relay = try {
            DatagramSocket(InetSocketAddress("127.0.0.1", 0))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Socks5OverSsh: failed to open UDP relay socket", e)
            replyStatus(clientOut, 0x01)
            client.close()
            return
        }

        // conId -> which local address to deliver the reply to (the sender of the last
        // datagram for that destination on this association).
        val conIdToReplyTarget = java.util.concurrent.ConcurrentHashMap<Int, InetSocketAddress>()
        // "destHost:destPort" -> conId, so repeated packets to the same destination reuse
        // the same udpgw conId instead of leaking a new one per packet.
        val destToConId = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val relayOpen = AtomicBoolean(true)

        // Reply BND.ADDR/BND.PORT = our relay socket; the client sends its UDP datagrams there.
        val relayPort = relay.localPort
        clientOut.write(
            byteArrayOf(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, (relayPort shr 8 and 0xFF).toByte(), (relayPort and 0xFF).toByte())
        )
        clientOut.flush()

        fun teardown() {
            if (!relayOpen.compareAndSet(true, false)) return
            destToConId.values.forEach { gw.unregisterListener(it) }
            try {
                relay.close()
            } catch (_: Exception) {
            }
            try {
                client.close()
            } catch (_: Exception) {
            }
        }

        // Watch the TCP control connection; its closure ends the association (RFC 1928 §7).
        executor.execute {
            try {
                val buf = ByteArray(256)
                while (relayOpen.get()) {
                    if (client.getInputStream().read(buf) == -1) break
                }
            } catch (_: Exception) {
            } finally {
                teardown()
            }
        }

        executor.execute {
            val buf = ByteArray(64 * 1024)
            try {
                while (relayOpen.get()) {
                    val packet = DatagramPacket(buf, buf.size)
                    relay.receive(packet)

                    val parsed = parseUdpRequest(packet) ?: continue
                    val (destHost, destPort, data) = parsed
                    val destKey = "$destHost:$destPort"
                    val conId = destToConId.getOrPut(destKey) {
                        val id = nextConId.getAndUpdate { (it + 1) and 0xFFFF }
                        gw.registerListener(id) { replyData ->
                            val target = conIdToReplyTarget[id] ?: return@registerListener
                            try {
                                val wrapped = wrapUdpResponse(destHost, destPort, replyData)
                                relay.send(DatagramPacket(wrapped, wrapped.size, target))
                            } catch (e: Exception) {
                                LogUtil.e(AppConfig.TAG, "Socks5OverSsh: failed to deliver udpgw reply", e)
                            }
                        }
                        id
                    }
                    conIdToReplyTarget[conId] = InetSocketAddress(packet.address, packet.port)

                    try {
                        gw.send(conId, destHost, destPort, data)
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Socks5OverSsh: udpgw send failed for $destKey", e)
                    }
                }
            } catch (_: Exception) {
            } finally {
                teardown()
            }
        }
    }

    /** Parses a client->relay SOCKS5 UDP request datagram (RFC 1928 §7). Fragmentation (FRAG != 0) is not supported. */
    private fun parseUdpRequest(packet: DatagramPacket): Triple<String, Int, ByteArray>? {
        val data = packet.data
        val len = packet.length
        if (len < 4) return null
        val frag = data[2].toInt() and 0xFF
        if (frag != 0) return null // fragmented UDP not supported
        val addressType = data[3].toInt() and 0xFF

        var offset = 4
        val host: String
        when (addressType) {
            0x01 -> { // IPv4
                if (len < offset + 4) return null
                host = "${data[offset].toInt() and 0xFF}.${data[offset + 1].toInt() and 0xFF}." +
                    "${data[offset + 2].toInt() and 0xFF}.${data[offset + 3].toInt() and 0xFF}"
                offset += 4
            }

            0x03 -> { // domain name
                if (len < offset + 1) return null
                val nameLen = data[offset].toInt() and 0xFF
                offset += 1
                if (len < offset + nameLen) return null
                host = String(data, offset, nameLen, Charsets.US_ASCII)
                offset += nameLen
            }

            else -> return null // IPv6 destinations aren't supported by the udpgw wire format used here
        }

        if (len < offset + 2) return null
        val port = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        offset += 2

        val payload = data.copyOfRange(offset, len)
        return Triple(host, port, payload)
    }

    /** Wraps a udpgw reply payload back into a SOCKS5 UDP response datagram (RFC 1928 §7), IPv4 only. */
    private fun wrapUdpResponse(destHost: String, destPort: Int, data: ByteArray): ByteArray {
        val addr = try {
            InetAddress.getByName(destHost).address
        } catch (_: Exception) {
            byteArrayOf(0, 0, 0, 0)
        }
        val header = byteArrayOf(
            0, 0, 0, // RSV, RSV, FRAG
            0x01, // ATYP = IPv4
            addr.getOrElse(0) { 0 }, addr.getOrElse(1) { 0 }, addr.getOrElse(2) { 0 }, addr.getOrElse(3) { 0 },
            (destPort shr 8 and 0xFF).toByte(), (destPort and 0xFF).toByte(),
        )
        return header + data
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
