package com.v2ray.ang.enums

/**
 * The 4 SSH tunnel variants, mirroring what SSH-injector style apps (e.g. Neko Injector,
 * HTTP Injector) offer:
 *
 * - SSH: plain SSH2 connection, then dynamic port forwarding (local SOCKS).
 * - SSH_PAYLOAD: an HTTP-like "payload" is sent over the raw TCP socket first (bug-host /
 *   DPI-evasion trick), then the SSH handshake continues on the same socket.
 * - SSH_SSL_PAYLOAD: the socket is wrapped in TLS first, then the payload, then SSH.
 * - SSH_SSL_PAYLOAD_PROXY: an HTTP CONNECT is issued through an upstream HTTP proxy first,
 *   then TLS, then the payload, then SSH.
 */
enum class ESshMode(val value: Int) {
    SSH(0),
    SSH_PAYLOAD(1),
    SSH_SSL_PAYLOAD(2),
    SSH_SSL_PAYLOAD_PROXY(3);

    val usesPayload: Boolean get() = this != SSH
    val usesSsl: Boolean get() = this == SSH_SSL_PAYLOAD || this == SSH_SSL_PAYLOAD_PROXY
    val usesProxy: Boolean get() = this == SSH_SSL_PAYLOAD_PROXY

    companion object {
        fun fromInt(value: Int?) = entries.firstOrNull { it.value == value } ?: SSH
        fun fromName(name: String?) = entries.firstOrNull { it.name.equals(name, true) } ?: SSH
    }
}
