package com.miku.ray.handler

import com.miku.ray.AppConfig
import com.miku.ray.dto.IPAPIInfo
import com.miku.ray.dto.UrlContentRequest
import com.miku.ray.util.HttpUtil
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.Proxy
import java.util.concurrent.TimeUnit

object SpeedtestManager {

    fun socketConnectTime(url: String, port: Int, timeoutMs: Int = 1500): Long {
        var socket: Socket? = null
        val start = System.currentTimeMillis()

        try {
            socket = Socket()
            socket.connect(InetSocketAddress(url, port), timeoutMs)

            return System.currentTimeMillis() - start
        } catch (e: UnknownHostException) {
            LogUtil.e(AppConfig.TAG, "Unknown host: $url", e)
        } catch (e: IOException) {
            LogUtil.e(AppConfig.TAG, "socketConnectTime IOException: ${e.message}")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to establish socket connection to $url:$port", e)
        } finally {
            socket?.let { s ->
                try {
                    if (!s.isClosed) {
                        s.close()
                    }
                } catch (closeEx: IOException) {
                }
            }
        }
        return -1
    }

    fun getCountryCodeThroughProxy(httpPort: Int, timeoutMs: Int = 3500): String? {
        if (httpPort <= 0) return null

        val configuredUrl = MmkvManager.decodeSettingsString(AppConfig.PREF_IP_API_URL)
        .takeIf { !it.isNullOrBlank() } ?: AppConfig.IP_API_URL
        val url = configuredUrl.replace("{ip}", "", ignoreCase = true)
        val content = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = timeoutMs,
                httpPort = httpPort
            )
        ) ?: return null
        val ipInfo = JsonUtil.fromJsonSafe(content, IPAPIInfo::class.java) ?: return null

        return listOf(
            ipInfo.country_code,
            ipInfo.country,
            ipInfo.countryCode,
            ipInfo.location?.country_code
        ).firstOrNull { !it.isNullOrBlank() }
        ?.trim()
        ?.uppercase()
        ?.takeIf { it.length == 2 }
    }

    fun getRemoteIPInfo(): String? {
        val url = MmkvManager.decodeSettingsString(AppConfig.PREF_IP_API_URL)
        .takeIf { !it.isNullOrBlank() } ?: AppConfig.IP_API_URL

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()
        val httpPort = SettingsManager.getHttpPort()
        if (httpPort == 0) return null
        val content = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000,
                httpPort = httpPort,
                proxyUsername = proxyUsername,
                proxyPassword = proxyPassword
            )
        ) ?: return null
        val ipInfo = JsonUtil.fromJsonSafe(content, IPAPIInfo::class.java) ?: return null

        val ip = listOf(
            ipInfo.ip,
            ipInfo.clientIp,
            ipInfo.ip_addr,
            ipInfo.query
        ).firstOrNull { !it.isNullOrBlank() }

        val country = listOf(
            ipInfo.country_code,
            ipInfo.country,
            ipInfo.countryCode,
            ipInfo.location?.country_code
        ).firstOrNull { !it.isNullOrBlank() }

        val showIsp = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_ISP_INFO, true)
        val isp = if (showIsp) {
            listOf(
                ipInfo.isp,
                ipInfo.organization,
                ipInfo.org,
                ipInfo.asn_organization,
                ipInfo.asOrg,
                ipInfo.asname
            ).firstOrNull { !it.isNullOrBlank() }
        } else {
            null
        }

        val flag = Utils.countryCodeToFlag(country)
        val flagPrefix = if (flag.isNotEmpty()) "$flag " else ""
        val ispSuffix = if (!isp.isNullOrBlank()) " · $isp" else ""
        return "${flagPrefix}(${country ?: "unknown"}) ${ip ?: "unknown"}$ispSuffix"
    }


    /**
     * Measure download throughput (bytes/sec) via local HTTP proxy on [httpPort].
     * Returns -1 on failure.
     */
    fun measureDownloadSpeed(
        httpPort: Int,
        url: String = AppConfig.SPEED_TEST_DOWNLOAD_URL,
        timeoutMs: Int = 20000,
    ): Long {
        if (httpPort <= 0) return -1L
        return try {
            val client = OkHttpClient.Builder()
                .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(AppConfig.LOOPBACK, httpPort)))
                .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .build()
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Connection", "close")
                .build()
            val start = System.nanoTime()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return -1L
                val body = response.body ?: return -1L
                var total = 0L
                body.byteStream().use { input ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                    }
                }
                val elapsedNs = System.nanoTime() - start
                if (elapsedNs <= 0L || total <= 0L) return -1L
                (total * 1_000_000_000L) / elapsedNs
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "measureDownloadSpeed failed: ${e.message}")
            -1L
        }
    }

    /**
     * Measure upload throughput (bytes/sec) via local HTTP proxy on [httpPort].
     * Returns -1 on failure.
     */
    fun measureUploadSpeed(
        httpPort: Int,
        url: String = AppConfig.SPEED_TEST_UPLOAD_URL,
        uploadBytes: Long = AppConfig.SPEED_TEST_UPLOAD_BYTES,
        timeoutMs: Int = 20000,
    ): Long {
        if (httpPort <= 0) return -1L
        return try {
            val size = uploadBytes.coerceAtLeast(64 * 1024L).toInt().coerceAtMost(8 * 1024 * 1024)
            val payload = ByteArray(size) { (it % 256).toByte() }
            val client = OkHttpClient.Builder()
                .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(AppConfig.LOOPBACK, httpPort)))
                .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .build()
            val body = payload.toRequestBody("application/octet-stream".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Connection", "close")
                .build()
            val start = System.nanoTime()
            client.newCall(request).execute().use { response ->
                // Cloudflare __up returns 200 even if body discarded
                if (!response.isSuccessful && response.code !in 200..399) return -1L
                response.body?.close()
                val elapsedNs = System.nanoTime() - start
                if (elapsedNs <= 0L) return -1L
                (size.toLong() * 1_000_000_000L) / elapsedNs
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "measureUploadSpeed failed: ${e.message}")
            -1L
        }
    }

}
