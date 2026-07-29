package com.v2ray.ang.core.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import java.io.File

/**
 * Owns a single SSH session used to tunnel MikuRay's Xray core through an SSH server,
 * with optional pre-handshake custom payload (see [SshPayloadSocketFactory]).
 *
 * Xray never talks SSH itself: this class opens a local dynamic SOCKS5 listener
 * (JSch's `-D` equivalent) and Xray's outbound is just a plain `socks` outbound
 * pointing at that local port. See CoreOutboundBuilder.toOutboundSsh.
 */
object SshTunnelManager {

    private var session: Session? = null
    private var socksServer: Socks5OverSsh? = null

    @Volatile
    var localPort: Int = 0
        private set

    val isConnected: Boolean
        get() = session?.isConnected == true

    /**
     * Connects the SSH session (blocking) and starts the local dynamic SOCKS forwarder.
     * Must be called before Xray's core loop starts, and matched with [disconnect] when
     * the core loop stops.
     *
     * @return the local port Xray's SSH outbound should point to.
     */
    @Throws(Exception::class)
    @Synchronized
    fun connect(profile: ProfileItem): Int {
        disconnect()

        val host = profile.server?.trim().orEmpty()
        if (host.isEmpty()) error("SSH: server address is empty")
        val port = profile.serverPort?.trim()?.toIntOrNull() ?: 22
        val username = profile.username?.trim().orEmpty()
        if (username.isEmpty()) error("SSH: username is empty")
        val authType = profile.sshAuthType ?: AppConfig.SSH_AUTH_PASSWORD

        val jsch = JSch()
        val keyAlias = "mikuray-ssh-${profile.subscriptionId}-${System.identityHashCode(profile)}"

        when (authType) {
            AppConfig.SSH_AUTH_PRIVATE_KEY -> {
                val privateKey = profile.sshPrivateKey.orEmpty()
                if (privateKey.isBlank()) error("SSH: private key is empty")
                val passphrase = profile.sshPrivateKeyPassphrase?.takeIf { it.isNotEmpty() }
                jsch.addIdentity(
                    keyAlias,
                    privateKey.toByteArray(),
                    null,
                    passphrase?.toByteArray()
                )
            }

            AppConfig.SSH_AUTH_CERTIFICATE -> {
                val privateKey = profile.sshPrivateKey.orEmpty()
                val certificate = profile.sshCertificate.orEmpty()
                if (privateKey.isBlank()) error("SSH: private key is empty")
                if (certificate.isBlank()) error("SSH: certificate is empty")
                val passphrase = profile.sshPrivateKeyPassphrase?.takeIf { it.isNotEmpty() }

                // JSch (mwiede fork) locates the OpenSSH certificate for a key via a
                // sibling "<key>-cert.pub" file, so both are written to app-private
                // cache storage using that naming convention before being loaded.
                val cacheDir = File(AngApplication.application.cacheDir, "ssh_keys").apply { mkdirs() }
                val keyFile = File(cacheDir, "$keyAlias")
                val certFile = File(cacheDir, "$keyAlias-cert.pub")
                keyFile.writeText(privateKey)
                certFile.writeText(certificate)

                try {
                    jsch.addIdentity(keyFile.absolutePath, passphrase)
                } finally {
                    // Best-effort cleanup; JSch has already read the bytes into memory by now.
                    keyFile.delete()
                    certFile.delete()
                }
            }

            else -> {
                // password auth handled via session.setPassword below
            }
        }

        val newSession = jsch.getSession(username, host, port)
        newSession.setConfig("StrictHostKeyChecking", "no")
        newSession.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
        newSession.timeout = 20000

        if (authType == AppConfig.SSH_AUTH_PASSWORD || authType.isBlank()) {
            newSession.setPassword(profile.password.orEmpty())
        }

        newSession.setSocketFactory(SshPayloadSocketFactory(host, port, profile.sshPayload))

        LogUtil.i(AppConfig.TAG, "SshTunnelManager: connecting to $host:$port as $username (auth=$authType)")
        newSession.connect(15000)

        val preferredPort = Utils.findRandomFreePort()
        // "127.0.0.1" only: the dynamic SOCKS listener never needs to be reachable
        // from outside the device, Xray is the only client.
        // JSch has no native "-D" dynamic/SOCKS forwarding (only setPortForwardingL/R),
        // so Socks5OverSsh implements a minimal local SOCKS5 server that tunnels each
        // connection through this session via direct-tcpip channels.
        val newSocksServer = Socks5OverSsh(newSession)
        val boundPort = newSocksServer.start("127.0.0.1", preferredPort)

        session = newSession
        socksServer = newSocksServer
        localPort = boundPort
        LogUtil.i(AppConfig.TAG, "SshTunnelManager: connected, local SOCKS on 127.0.0.1:$boundPort")
        return boundPort
    }

    @Synchronized
    fun disconnect() {
        try {
            socksServer?.stop()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "SshTunnelManager: error while stopping SOCKS server", e)
        }
        try {
            session?.let {
                if (it.isConnected) it.disconnect()
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "SshTunnelManager: error while disconnecting", e)
        }
        socksServer = null
        session = null
        localPort = 0
    }
}
