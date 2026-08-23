package com.miku.ray.core

import android.util.Log
import com.miku.ray.AppConfig
import com.miku.ray.dto.entities.ProfileItem
import com.trilead.ssh2.Connection
import com.trilead.ssh2.NekoProxyData
import com.trilead.ssh2.LocalStreamForwarder
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * SSH transport adapter modeled after Neko's runtime shape:
 * one authenticated SSH connection, one local SOCKS5 listener, and one
 * direct-tcpip channel per SOCKS request.
 *
 * Payload, TLS/SNI, HTTP proxy, split, delay_split, rotate, and random
 * semantics are provided by the bundled Neko-compatible Trilead transport.
 */
object SshTunnelManager {
    private const val TAG = "SshTunnelManager"
    private const val DEFAULT_LOCAL_PORT = 10809
    private const val MAX_LOCAL_PORT = 50146
    private const val SOCKS_VERSION = 5

    private val lock = Any()
    private val activeSockets = Collections.synchronizedSet(mutableSetOf<Socket>())
    private var connection: Connection? = null
    private var listener: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var workerPool: ExecutorService? = null
    private var running = false
    private var boundPort = 0

    fun isRunning(): Boolean = synchronized(lock) { running }

    fun getLocalPort(profile: ProfileItem): Int = synchronized(lock) {
        if (running && boundPort > 0) boundPort
        else profile.sshPortaLocal?.toIntOrNull()?.takeIf { it in 1..65535 } ?: DEFAULT_LOCAL_PORT
    }

    @Throws(IOException::class)
    fun start(profile: ProfileItem): Int {
        synchronized(lock) {
            if (running) return boundPort
            validate(profile)
            val requestedTunnelType = profile.sshTunnelType?.toIntOrNull()?.takeIf { it in 1..5 } ?: 1
            val host = profile.sshServer!!.trim()
            val port = profile.sshPort!!.toInt()
            val user = profile.sshUser.orEmpty()
            val pass = profile.sshPass.orEmpty()
            val remoteProxyHost = profile.sshRemoteProxy?.trim()?.takeIf { it.isNotEmpty() }
            val tunnelType = if (remoteProxyHost == null) requestedTunnelType else when (requestedTunnelType) {
                1, 2 -> 2
                else -> 5
            }
            val remoteProxyPort = profile.sshRemoteProxyPort?.toIntOrNull()
                ?: if (remoteProxyHost != null) throw IOException("Invalid remote proxy port") else 0
            if ((tunnelType == 2 || tunnelType == 5) && remoteProxyHost == null) {
                throw IOException("Remote proxy is required for tunnel type $tunnelType")
            }
            val customPayload = profile.sshPayload?.takeIf { it.isNotBlank() }
            val payload = customPayload
                ?: if (profile.sshUseDefaultPayload != false) "CONNECT [host_port] HTTP/1.0[crlf*2]" else null
            val tlsName = profile.sshTlsServerName?.takeIf { it.isNotBlank() }
                ?: profile.sshWsPayload?.takeIf { it.isNotBlank() }
                ?: host
            val forcedTls = profile.sshTlsForcing?.takeIf { it.isNotBlank() } ?: "tlsAuto"
            val proxyData = if (tunnelType == 1 && remoteProxyHost == null && customPayload == null) {
                null
            } else if (remoteProxyHost != null) {
                NekoProxyData.throughHttpProxy(
                    remoteProxyHost, remoteProxyPort, host, port, tunnelType, tlsName,
                    payload, profile.sshTrustAllCertificates != false, forcedTls, 10_000, 20_000
                )
            } else {
                NekoProxyData.direct(
                    host, port, tunnelType, tlsName, payload,
                    profile.sshTrustAllCertificates != false, forcedTls, 10_000, 20_000
                )
            }
            val ssh = Connection(host, port)
            if (proxyData != null) ssh.setProxyData(proxyData)
            ssh.connect(null, 10_000, 20_000)
            if (!ssh.authenticateWithPassword(user, pass)) {
                ssh.close()
                throw IOException("SSH password authentication failed")
            }

            val requestedPort = profile.sshPortaLocal?.toIntOrNull()?.takeIf { it in 1..65535 } ?: DEFAULT_LOCAL_PORT
            val local = bindLoopback(requestedPort)
            connection = ssh
            listener = local
            boundPort = local.localPort
            workerPool = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "MikuRay-SSH-worker").apply { isDaemon = true }
            }
            running = true
            acceptThread = Thread({ acceptLoop() }, "MikuRay-SSH-accept").apply {
                isDaemon = true
                start()
            }
            Log.i(TAG, "SSH connected to $host:$port; SOCKS5 listening on 127.0.0.1:$boundPort")
            return boundPort
        }
    }

    fun stop() {
        var oldConnection: Connection? = null
        var oldListener: ServerSocket? = null
        var oldPool: ExecutorService? = null
        synchronized(lock) {
            if (!running && connection == null && listener == null) return
            running = false
            oldConnection = connection
            oldListener = listener
            oldPool = workerPool
            connection = null
            listener = null
            workerPool = null
            boundPort = 0
        }
        try { oldListener?.close() } catch (_: Exception) { }
        activeSockets.toList().forEach { socket -> try { socket.close() } catch (_: Exception) { } }
        try { oldConnection?.close() } catch (_: Exception) { }
        oldPool?.shutdownNow()
        Log.i(TAG, "SSH tunnel stopped")
    }

    private fun validate(profile: ProfileItem) {
        require(!profile.sshServer.isNullOrBlank()) { "SSH server is empty" }
        require(profile.sshPort?.toIntOrNull()?.let { it in 1..65535 } == true) { "SSH port is invalid" }
        require(!profile.sshUser.isNullOrBlank()) { "SSH username is empty" }
        require(profile.sshPass != null) { "SSH password is missing" }
    }

    private fun bindLoopback(requestedPort: Int): ServerSocket {
        var port = requestedPort
        while (port <= MAX_LOCAL_PORT) {
            try {
                return ServerSocket(port, 64, InetAddress.getByName(AppConfig.LOOPBACK))
            } catch (_: IOException) {
                port++
            }
        }
        throw IOException("No local port available for SSH SOCKS5")
    }

    private fun acceptLoop() {
        while (isRunning()) {
            try {
                val socket = listener?.accept() ?: break
                socket.tcpNoDelay = true
                activeSockets.add(socket)
                workerPool?.execute { handleClient(socket) }
            } catch (_: SocketException) {
                break
            } catch (e: Exception) {
                if (isRunning()) Log.w(TAG, "SOCKS accept failed", e)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        var forwarder: LocalStreamForwarder? = null
        try {
            socket.soTimeout = 20_000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            negotiateSocks5(input, output)
            val target = readSocks5Target(input)
            val ssh = synchronized(lock) { connection } ?: throw IOException("SSH connection is not running")
            forwarder = ssh.createLocalStreamForwarder(target.host, target.port)
            writeSocks5Success(output)
            socket.soTimeout = 0
            pipeBidirectionally(socket, forwarder)
        } catch (e: Exception) {
            try { writeSocks5Failure(socket.getOutputStream()) } catch (_: Exception) { }
            Log.d(TAG, "SOCKS client closed: ${e.message}")
        } finally {
            try { forwarder?.close() } catch (_: Exception) { }
            activeSockets.remove(socket)
            try { socket.close() } catch (_: Exception) { }
        }
    }

    private fun negotiateSocks5(input: InputStream, output: OutputStream) {
        if (input.read() != SOCKS_VERSION) throw IOException("Unsupported SOCKS version")
        val methods = input.read()
        if (methods < 0) throw EOFException()
        val offered = ByteArray(methods)
        readFully(input, offered)
        if (!offered.contains(0.toByte())) {
            output.write(byteArrayOf(SOCKS_VERSION.toByte(), 0xFF.toByte()))
            output.flush()
            throw IOException("SOCKS authentication is not supported")
        }
        output.write(byteArrayOf(SOCKS_VERSION.toByte(), 0x00))
        output.flush()
    }

    private data class Target(val host: String, val port: Int)

    private fun readSocks5Target(input: InputStream): Target {
        if (input.read() != SOCKS_VERSION) throw IOException("Invalid SOCKS request")
        if (input.read() != 1) throw IOException("Only CONNECT is supported")
        if (input.read() < 0 || input.read() < 0) throw EOFException()
        val host = when (val atyp = input.read()) {
            1 -> readAddress(input, 4)
            3 -> {
                val length = input.read()
                if (length <= 0) throw IOException("Invalid SOCKS domain")
                readAddress(input, length)
            }
            4 -> readAddress(input, 16)
            else -> throw IOException("Unsupported SOCKS address type: $atyp")
        }
        val portBytes = ByteArray(2)
        readFully(input, portBytes)
        val port = ((portBytes[0].toInt() and 0xff) shl 8) or (portBytes[1].toInt() and 0xff)
        if (port !in 1..65535) throw IOException("Invalid target port")
        return Target(host, port)
    }

    private fun readAddress(input: InputStream, length: Int): String {
        val bytes = ByteArray(length)
        readFully(input, bytes)
        return if (length == 4 || length == 16) InetAddress.getByAddress(bytes).hostAddress else String(bytes, Charsets.UTF_8)
    }

    private fun writeSocks5Success(output: OutputStream) {
        output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun writeSocks5Failure(output: OutputStream) {
        output.write(byteArrayOf(5, 1, 0, 1, 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun pipeBidirectionally(socket: Socket, forwarder: LocalStreamForwarder) {
        val clientIn = socket.getInputStream()
        val clientOut = socket.getOutputStream()
        val remoteIn = forwarder.inputStream
        val remoteOut = forwarder.outputStream
        val upstream = Thread({ copy(clientIn, remoteOut) }, "MikuRay-SSH-upstream").apply { isDaemon = true }
        upstream.start()
        copy(remoteIn, clientOut)
        try { upstream.interrupt() } catch (_: Exception) { }
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16 * 1024)
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                output.flush()
            }
        } catch (_: IOException) { }
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw EOFException()
            offset += count
        }
    }
}
