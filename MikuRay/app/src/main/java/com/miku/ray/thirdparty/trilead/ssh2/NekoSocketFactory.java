package com.miku.ray.thirdparty.trilead.ssh2;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/** Creates the socket used by Trilead before SSH version exchange. */
public final class NekoSocketFactory {
    private static final String DEFAULT_UA = "Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.130 Safari/537.36";

    private NekoSocketFactory() { }

    public static Socket open(NekoProxyData options, String targetHost, int targetPort) throws IOException {
        String connectHost = options.remoteProxyHost == null ? options.endpointHost : options.remoteProxyHost;
        int connectPort = options.remoteProxyHost == null ? options.endpointPort : options.remoteProxyPort;
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(connectHost, connectPort), options.connectTimeout);
        raw.setTcpNoDelay(true);
        raw.setSoTimeout(options.readTimeout);

        Socket transport = raw;
        boolean tls = options.tunnelType == 3 || options.tunnelType == 4 || options.tunnelType == 5;
        if (tls) {
            transport = wrapTls(raw, options.tlsServerName == null ? targetHost : options.tlsServerName,
                    options.forcedTlsProtocol, options.trustAllCertificates);
        }

        boolean injectPayload = options.tunnelType != 3;
        if (injectPayload) {
            String expanded = options.payload;
            if (expanded == null || expanded.isEmpty()) {
                expanded = "CONNECT [host_port] HTTP/1.0[crlf*2]";
            }
            expanded = NekoPayload.expand(targetHost, targetPort, expanded, DEFAULT_UA);
            NekoPayload.write(transport.getOutputStream(), expanded);
            boolean direct = options.tunnelType == 1 && options.remoteProxyHost == null;
            if (!direct) {
                int status = readHttpStatus(transport.getInputStream());
                if (status != 200 && status != 101) {
                    closeQuietly(transport);
                    throw new IOException("Neko payload rejected by upstream, HTTP status " + status);
                }
            }
        }
        return transport;
    }

    private static Socket wrapTls(Socket raw, String serverName, String forcedProtocol,
            boolean trustAllCertificates) throws IOException {
        try {
            SSLSocketFactory factory = trustAllCertificates ? trustAllFactory() :
                    (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket ssl = (SSLSocket) factory.createSocket(raw, serverName, raw.getPort(), true);
            if (forcedProtocol != null && !forcedProtocol.isEmpty() && !"tlsAuto".equalsIgnoreCase(forcedProtocol)) {
                String protocol = forcedProtocol.equalsIgnoreCase("tls1") ? "TLSv1" :
                        forcedProtocol.equalsIgnoreCase("tls11") ? "TLSv1.1" :
                        forcedProtocol.equalsIgnoreCase("tls12") ? "TLSv1.2" :
                        forcedProtocol.equalsIgnoreCase("tls13") ? "TLSv1.3" : forcedProtocol;
                for (String supported : ssl.getSupportedProtocols()) {
                    if (supported.equalsIgnoreCase(protocol)) {
                        ssl.setEnabledProtocols(new String[] { supported });
                        break;
                    }
                }
            }
            javax.net.ssl.SSLParameters parameters = ssl.getSSLParameters();
            if (serverName != null && !serverName.isEmpty()) {
                try {
                    parameters.setServerNames(java.util.Collections.singletonList(new javax.net.ssl.SNIHostName(serverName)));
                } catch (IllegalArgumentException ignored) { }
            }
            parameters.setEndpointIdentificationAlgorithm(trustAllCertificates ? null : "HTTPS");
            ssl.setSSLParameters(parameters);
            ssl.startHandshake();
            ssl.setTcpNoDelay(true);
            return ssl;
        } catch (Exception e) {
            closeQuietly(raw);
            throw new IOException("Could not do SSL handshake", e);
        }
    }

    private static int readHttpStatus(InputStream input) throws IOException {
        ByteArrayOutputStream header = new ByteArrayOutputStream(256);
        int previous3 = -1, previous2 = -1, previous1 = -1;
        while (header.size() < 16 * 1024) {
            int value = input.read();
            if (value < 0) throw new IOException("Upstream closed before HTTP response");
            header.write(value);
            if (previous3 == '\r' && previous2 == '\n' && previous1 == '\r' && value == '\n') break;
            previous3 = previous2;
            previous2 = previous1;
            previous1 = value;
        }
        String firstLine = header.toString(StandardCharsets.ISO_8859_1.name());
        int lineEnd = firstLine.indexOf("\r\n");
        if (!firstLine.startsWith("HTTP/") || lineEnd < 12 || firstLine.charAt(8) != ' ') {
            throw new IOException("The proxy did not send back a valid HTTP response");
        }
        try {
            return Integer.parseInt(firstLine.substring(9, 12));
        } catch (NumberFormatException e) {
            throw new IOException("The proxy did not send back a valid HTTP response", e);
        }
    }

    private static SSLSocketFactory trustAllFactory() throws Exception {
        TrustManager[] managers = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String authType) { }
            public void checkServerTrusted(X509Certificate[] chain, String authType) { }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, managers, new SecureRandom());
        return context.getSocketFactory();
    }

    private static void closeQuietly(Socket socket) {
        try { socket.close(); } catch (Exception ignored) { }
    }
}
