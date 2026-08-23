package com.miku.ray.thirdparty.trilead.ssh2;

/**
 * Transport options for Neko-compatible SSH injection. This is a protocol
 * description object; TransportManager creates the wrapped socket.
 */
public final class NekoProxyData implements ProxyData {
    public final String endpointHost;
    public final int endpointPort;
    public final String remoteProxyHost;
    public final int remoteProxyPort;
    public final int tunnelType;
    public final String tlsServerName;
    public final String payload;
    public final boolean trustAllCertificates;
    public final String forcedTlsProtocol;
    public final int connectTimeout;
    public final int readTimeout;

    public NekoProxyData(
            String endpointHost,
            int endpointPort,
            String remoteProxyHost,
            int remoteProxyPort,
            int tunnelType,
            String tlsServerName,
            String payload,
            boolean trustAllCertificates,
            String forcedTlsProtocol,
            int connectTimeout,
            int readTimeout) {
        this.endpointHost = endpointHost;
        this.endpointPort = endpointPort;
        this.remoteProxyHost = remoteProxyHost;
        this.remoteProxyPort = remoteProxyPort;
        this.tunnelType = tunnelType;
        this.tlsServerName = tlsServerName;
        this.payload = payload;
        this.trustAllCertificates = trustAllCertificates;
        this.forcedTlsProtocol = forcedTlsProtocol;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    public static NekoProxyData direct(String host, int port, int tunnelType,
            String tlsServerName, String payload, boolean trustAllCertificates,
            String forcedTlsProtocol, int connectTimeout, int readTimeout) {
        return new NekoProxyData(host, port, null, 0, tunnelType, tlsServerName,
                payload, trustAllCertificates, forcedTlsProtocol, connectTimeout, readTimeout);
    }

    public static NekoProxyData throughHttpProxy(String proxyHost, int proxyPort,
            String sshHost, int sshPort, int tunnelType, String tlsServerName,
            String payload, boolean trustAllCertificates, String forcedTlsProtocol,
            int connectTimeout, int readTimeout) {
        return new NekoProxyData(sshHost, sshPort, proxyHost, proxyPort, tunnelType,
                tlsServerName, payload, trustAllCertificates, forcedTlsProtocol,
                connectTimeout, readTimeout);
    }
}
