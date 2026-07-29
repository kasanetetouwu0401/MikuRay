package com.v2ray.ang.core.ssh

import com.jcraft.jsch.Session
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Client for the badvpn-udpgw wire protocol (the same "UDPGW" feature found in HTTP Injector /
 * Neko Injector-style SSH tools), used to relay UDP traffic (DNS, games, VoIP) through a udpgw
 * server reachable via the SSH tunnel, since plain SSH/SOCKS only carries TCP.
 *
 * One instance owns a single `direct-tcpip` channel to the udpgw server (opened lazily) and
 * multiplexes many logical UDP "connections" over it, each identified by a 16-bit conId chosen
 * by this client. [Socks5OverSsh] is the only caller: it maps each locally-received SOCKS5 UDP
 * datagram to a conId and registers a listener to receive the matching reply.
 *
 * Wire format per message (2-byte little-endian length prefix, then):
 *   uint8  flags   (KEEPALIVE=0x01, REBIND=0x02, DNS=0x04, IPV6=0x08)
 *   uint16 conId   (little-endian)
 *   if not KEEPALIVE:
 *     if DNS flag set:      (no address; server relays to its own configured resolver)
 *     else if IPV6 flag set: 16-byte address + 2-byte port (both big-endian/network order)
 *     else:                  4-byte IPv4 address + 2-byte port (both big-endian/network order)
 *     ...UDP payload bytes
 */
class UdpgwClient(
    private val session: Session,
    private val udpgwHost: String,
    private val udpgwPort: Int,
) {
    fun interface ReplyListener {
        fun onReply(data: ByteArray)
    }

    private val executor = Executors.newCachedThreadPool()
    private val listeners = ConcurrentHashMap<Int, ReplyListener>()
    private val running = AtomicBoolean(false)

    @Volatile
    private var channel: com.jcraft.jsch.ChannelDirectTCPIP? = null
    private var out: DataOutputStream? = null

    private val connectLock = Any()

    /** Lazily opens the direct-tcpip channel to the udpgw server and starts the reader loop. */
    @Throws(IOException::class)
    private fun ensureConnected() {
        if (channel?.isConnected == true) return
        synchronized(connectLock) {
            if (channel?.isConnected == true) return

            val ch = session.openChannel("direct-tcpip") as com.jcraft.jsch.ChannelDirectTCPIP
            ch.setHost(udpgwHost)
            ch.setPort(udpgwPort)
            ch.setOrgIPAddress("127.0.0.1")
            ch.setOrgPort(0)

            val input = ch.inputStream
            val output = ch.outputStream
            ch.connect(15000)

            channel = ch
            out = DataOutputStream(output)
            running.set(true)
            executor.execute { readLoop(DataInputStream(input)) }
            LogUtil.i(AppConfig.TAG, "UdpgwClient: connected to udpgw server $udpgwHost:$udpgwPort")
        }
    }

    /** Registers (or replaces) the listener that receives reply datagrams for [conId]. */
    fun registerListener(conId: Int, listener: ReplyListener) {
        listeners[conId] = listener
    }

    fun unregisterListener(conId: Int) {
        listeners.remove(conId)
    }

    /**
     * Sends one UDP datagram's payload to [destHost]:[destPort] over conId [conId]. If
     * [destPort] is 53 (DNS), the address is omitted and the udpgw server relays the query to
     * its own configured resolver instead, matching HTTP Injector/badvpn convention.
     */
    @Throws(IOException::class)
    fun send(conId: Int, destHost: String, destPort: Int, data: ByteArray) {
        ensureConnected()
        val isDns = destPort == 53

        var flags = 0
        if (isDns) flags = flags or FLAG_DNS

        val addrBytes = if (isDns) ByteArray(0) else encodeIpv4AddressPort(destHost, destPort)

        val bodyLen = 1 /* flags */ + 2 /* conId */ + addrBytes.size + data.size
        synchronized(connectLock) {
            val o = out ?: error("UDPGW: channel not connected")
            o.writeShort(bodyLen.toShort().reverseBytes().toInt()) // length, little-endian
            o.writeByte(flags)
            o.writeShort(conId.toShort().reverseBytes().toInt()) // conId, little-endian
            if (addrBytes.isNotEmpty()) o.write(addrBytes)
            o.write(data)
            o.flush()
        }
    }

    /** Sends a bare keepalive frame for [conId] so the server doesn't garbage-collect it. */
    fun sendKeepalive(conId: Int) {
        try {
            ensureConnected()
            synchronized(connectLock) {
                val o = out ?: return
                o.writeShort(3.toShort().reverseBytes().toInt()) // flags(1) + conId(2)
                o.writeByte(FLAG_KEEPALIVE)
                o.writeShort(conId.toShort().reverseBytes().toInt())
                o.flush()
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "UdpgwClient: keepalive failed for conId=$conId", e)
        }
    }

    fun close() {
        running.set(false)
        listeners.clear()
        try {
            channel?.disconnect()
        } catch (_: Exception) {
        }
        channel = null
        out = null
        executor.shutdownNow()
    }

    private fun readLoop(input: DataInputStream) {
        try {
            while (running.get()) {
                val lenLE = input.readUnsignedShort()
                val bodyLen = lenLE.toShort().reverseBytes().toInt() and 0xFFFF
                if (bodyLen < 3) { // at least flags(1) + conId(2)
                    skipFully(input, bodyLen)
                    continue
                }
                val flags = input.readUnsignedByte()
                val conIdLE = input.readUnsignedShort()
                val conId = conIdLE.toShort().reverseBytes().toInt() and 0xFFFF

                var remaining = bodyLen - 3
                if (flags and FLAG_KEEPALIVE == 0) {
                    // Skip the address portion the server may echo back; we don't need it since
                    // conId already identifies which local flow a reply belongs to.
                    val addrLen = when {
                        flags and FLAG_IPV6 != 0 -> 18
                        else -> 6
                    }
                    if (remaining >= addrLen) {
                        skipFully(input, addrLen)
                        remaining -= addrLen
                    }
                }

                val data = ByteArray(remaining)
                if (remaining > 0) readFully(input, data)

                if (flags and FLAG_KEEPALIVE == 0 && remaining >= 0) {
                    listeners[conId]?.onReply(data)
                }
            }
        } catch (e: Exception) {
            if (running.get()) {
                LogUtil.e(AppConfig.TAG, "UdpgwClient: read loop terminated", e)
            }
        }
    }

    private fun encodeIpv4AddressPort(host: String, port: Int): ByteArray {
        val addr = try {
            InetAddress.getByName(host).address
        } catch (e: Exception) {
            throw IOException("UDPGW: cannot resolve $host", e)
        }
        if (addr.size != 4) throw IOException("UDPGW: only IPv4 destinations are supported (got ${addr.size}-byte address for $host)")
        return byteArrayOf(addr[0], addr[1], addr[2], addr[3], (port shr 8 and 0xFF).toByte(), (port and 0xFF).toByte())
    }

    @Throws(IOException::class)
    private fun readFully(input: DataInputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read == -1) throw IOException("UDPGW: unexpected end of stream")
            offset += read
        }
    }

    @Throws(IOException::class)
    private fun skipFully(input: DataInputStream, count: Int) {
        var remaining = count
        val buffer = ByteArray(minOf(remaining, 4096))
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(remaining, buffer.size))
            if (read == -1) throw IOException("UDPGW: unexpected end of stream")
            remaining -= read
        }
    }

    companion object {
        private const val FLAG_KEEPALIVE = 1 shl 0
        private const val FLAG_REBIND = 1 shl 1
        private const val FLAG_DNS = 1 shl 2
        private const val FLAG_IPV6 = 1 shl 3
    }
}
