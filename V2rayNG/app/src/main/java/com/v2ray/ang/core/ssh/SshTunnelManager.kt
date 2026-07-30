package com.v2ray.ang.core.ssh

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.ESshAuthType
import com.v2ray.ang.enums.ESshMode
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import java.io.IOException

/**
 * Owns the sshj connection + local SOCKS5 listener for the currently active SSH server config.
 *
 * MikuRay's core (Xray) has no native "ssh" outbound, so instead of feeding it an SSH config
 * directly, this stands up a local SOCKS5 proxy (backed by SSH direct-tcpip channels, the
 * equivalent of `ssh -D`) and Xray is given a plain SOCKS outbound pointing at
 * 127.0.0.1:[localPort]. See [com.v2ray.ang.core.CoreOutboundBuilder.toOutboundSsh].
 *
 * Not thread-safe against concurrent start() calls from different threads; MikuRay only ever
 * has one active server at a time so this mirrors that assumption (see SettingsManager /
 * CoreServiceManager for the same pattern with the native core).
 */
object SshTunnelManager {

    private const val TAG = "SshTunnelManager"
    private const val DEFAULT_CONNECT_TIMEOUT_MS = 8_000

    @Volatile
    private var client: SSHClient? = null

    @Volatile
    private var socksServer: MiniSocks5Server? = null

    @Volatile
    private var activeGuid: String? = null

    val isRunning: Boolean
        get() = client?.isConnected == true && socksServer?.isRunning == true

    val localPort: Int
        get() = socksServer?.localPort ?: 0

    /**
     * Connects (if not already connected for this [guid]) and returns the local SOCKS5 port to
     * route Xray's outbound through. Blocking — must not be called from the main thread; see
     * the caveat in CoreServiceManager's SSH hook about onStartCommand running on the main
     * thread today.
     */
    @Throws(IOException::class)
    @Synchronized
    fun start(guid: String, config: ProfileItem): Int {
        if (guid == activeGuid && isRunning) {
            return localPort
        }
        stop()

        val host = config.server.orEmpty()
        val port = Utils.parseInt(config.serverPort, 22)
        if (host.isEmpty() || port <= 0) {
            error("SSH host/port tidak valid")
        }
        val username = config.username.orEmpty()
        val mode = ESshMode.fromName(config.sshMode)
        val authType = ESshAuthType.fromName(config.sshAuthType)

        val sshConfig = net.schmizz.sshj.DefaultConfig()
        val sshClient = SSHClient(sshConfig)
        // Bug-host / VPS SSH endpoints used with this feature generally aren't pre-pinned by
        // the user (no known_hosts UX in-app), so we don't verify the host key here — same
        // tradeoff SSH-injector style apps make. Real auth is the password/key/cert below.
        sshClient.addHostKeyVerifier(PromiscuousVerifier())
        sshClient.connectTimeout = DEFAULT_CONNECT_TIMEOUT_MS
        sshClient.timeout = DEFAULT_CONNECT_TIMEOUT_MS

        sshClient.socketFactory = PayloadSocketFactory(
            mode = mode,
            targetHost = host,
            targetPort = port,
            sni = config.sni?.takeIf { it.isNotBlank() } ?: host,
            payloadTemplate = config.sshPayload.orEmpty(),
            proxyHost = config.sshProxyHost.orEmpty(),
            proxyPort = Utils.parseInt(config.sshProxyPort, 8080),
            proxyUsername = config.sshProxyUsername,
            proxyPassword = config.sshProxyPassword,
            connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS,
        )

        try {
            // The socket factory above already reaches the right host:port (possibly via a
            // proxy + TLS + payload first), so the host/port passed to connect() here just
            // needs to be non-blank; PayloadSocketFactory ignores them and uses its own target.
            sshClient.connect(host, port)

            when (authType) {
                ESshAuthType.PASSWORD -> {
                    sshClient.authPassword(username, config.password.orEmpty())
                }

                ESshAuthType.PRIVATE_KEY -> {
                    val passphrase = config.sshPrivateKeyPassword
                    val keyProvider = sshClient.loadKeys(
                        config.sshPrivateKey.orEmpty(),
                        null,
                        passphrase?.takeIf { it.isNotEmpty() }?.let { staticPasswordFinder(it) }
                    )
                    sshClient.authPublickey(username, keyProvider)
                }

                ESshAuthType.CERTIFICATE -> {
                    val passphrase = config.sshPrivateKeyPassword
                    // sshj accepts an OpenSSH certificate (the "-cert.pub" content) as the
                    // public-key half here; it gets attached to the keypair for cert auth.
                    val keyProvider = sshClient.loadKeys(
                        config.sshPrivateKey.orEmpty(),
                        config.sshCertificate.orEmpty(),
                        passphrase?.takeIf { it.isNotEmpty() }?.let { staticPasswordFinder(it) }
                    )
                    sshClient.authPublickey(username, keyProvider)
                }
            }

            val server = MiniSocks5Server(sshClient)
            server.start()

            client = sshClient
            socksServer = server
            activeGuid = guid
            LogUtil.i(TAG, "SSH tunnel connected to $host:$port, local SOCKS on ${server.localPort}")
            return server.localPort
        } catch (e: Exception) {
            LogUtil.e(TAG, "SSH tunnel failed to start: ${e.message}", e)
            runCatching { sshClient.disconnect() }
            client = null
            activeGuid = null
            throw IOException(e.message ?: "Gagal konek SSH", e)
        }
    }

    @Synchronized
    fun stop() {
        socksServer?.let { runCatching { it.stop() } }
        client?.let { runCatching { it.disconnect() } }
        socksServer = null
        client = null
        activeGuid = null
    }

    private fun staticPasswordFinder(value: String) = object : PasswordFinder {
        override fun reqPassword(resource: Resource<*>?): CharArray = value.toCharArray()
        override fun shouldRetry(resource: Resource<*>?): Boolean = false
    }

    init {
        // Registering BouncyCastle widens the set of key/cert formats and algorithms sshj can
        // parse (notably useful for certificate auth and newer OpenSSH key formats).
        SecurityUtils.setRegisterBouncyCastle(true)
    }
}
