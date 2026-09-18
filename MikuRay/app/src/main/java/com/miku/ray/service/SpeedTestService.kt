package com.miku.ray.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.core.CoreConfigManager
import com.miku.ray.core.CoreNativeManager
import com.miku.ray.dto.SpeedTestMessage
import com.miku.ray.dto.TestProgressInfo
import com.miku.ray.enums.NotificationChannelType
import com.miku.ray.extension.serializable
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.handler.SpeedtestManager
import com.miku.ray.helper.NotificationHelper
import com.miku.ray.util.AppNameHelper
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MessageUtil
import com.miku.ray.util.Utils
import com.miku.ray.remixicon.R as RemixR
import libv2ray.CoreCallbackHandler
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Dedicated service for per-profile bandwidth speed tests (download + upload).
 * Runs independently from RealPing / CountryCode test services.
 */
class SpeedTestService : Service() {

    private val cancelled = AtomicBoolean(false)
    private val workerScope = CoroutineScope(Dispatchers.IO)
    private var worker: Job? = null
    private val progressLock = Any()
    @Volatile
    private var activeRequestId = ""

    private val cancelAction by lazy {
        val intent = Intent(this, SpeedTestService::class.java).putExtra(
            "content",
            SpeedTestMessage(AppConfig.MSG_SPEED_TEST_CANCEL),
        )
        val pendingIntent = PendingIntent.getService(
            this,
            NotificationChannelType.CORE_TEST.notificationId + 2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationCompat.Action.Builder(
            RemixR.drawable.rmx_media_stop_line,
            getString(android.R.string.cancel),
            pendingIntent,
        ).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelled.set(true)
        worker?.cancel()
        worker = null
        sendFinish(activeRequestId)
        activeRequestId = ""
        NotificationHelper.stopForeground(this)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.serializable<SpeedTestMessage>("content")
        if (message == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when (message.key) {
            AppConfig.MSG_SPEED_TEST_START -> handleStart(message, startId)
            AppConfig.MSG_SPEED_TEST_CANCEL -> handleCancel(message.requestId)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun handleStart(message: SpeedTestMessage, startId: Int) {
        if (worker?.isActive == true) {
            cancelled.set(true)
            worker?.cancel()
            sendFinish(activeRequestId)
            activeRequestId = ""
        }
        val requestId = message.requestId
        val guids = when {
            message.serverGuids.isNotEmpty() -> message.serverGuids
            message.subscriptionId.isNotEmpty() -> MmkvManager.decodeServerList(message.subscriptionId)
            else -> MmkvManager.decodeAllServerList()
        }
        if (guids.isEmpty()) {
            sendFinish(requestId)
            stopSelf(startId)
            return
        }

        cancelled.set(false)
        activeRequestId = requestId
        val targetGuids = guids.toList()

        NotificationHelper.startForeground(
            this,
            NotificationChannelType.CORE_TEST,
            AppNameHelper.getDisplayName(this),
            getString(R.string.title_speed_test_all_server),
            cancelAction,
        )

        // Concurrency 1 by default for bandwidth tests to avoid saturating the link
        val parallelism = 1
        worker = workerScope.launch(Dispatchers.IO.limitedParallelism(parallelism)) {
            try {
                SettingsManager.initAssets(this@SpeedTestService, assets)
                CoreNativeManager.initCoreEnv(this@SpeedTestService)
                val completed = AtomicInteger(0)
                supervisorScope {
                    targetGuids.map { guid ->
                        async {
                            currentCoroutineContext().ensureActive()
                            val (down, up) = try {
                                measureThroughProfile(guid)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (e: Exception) {
                                LogUtil.e(AppConfig.TAG, "Speed test failed for $guid", e)
                                -1L to -1L
                            }
                            if (!cancelled.get()) {
                                MmkvManager.encodeServerSpeedResult(guid, down, up)
                            }
                            synchronized(progressLock) {
                                MessageUtil.sendMsg2UI(
                                    this@SpeedTestService,
                                    AppConfig.MSG_SPEED_TEST_SUCCESS,
                                    guid,
                                    requestId,
                                )
                                val current = completed.incrementAndGet()
                                val progress = TestProgressInfo(guid, 0L, current, targetGuids.size)
                                NotificationHelper.updateNotification(
                                    channelType = NotificationChannelType.CORE_TEST,
                                    context = this@SpeedTestService,
                                    title = getString(R.string.title_speed_test_all_server),
                                    content = getString(
                                        R.string.connection_runing_task_left,
                                        "$current / ${targetGuids.size}",
                                    ),
                                )
                                MessageUtil.sendMsg2UI(
                                    this@SpeedTestService,
                                    AppConfig.MSG_SPEED_TEST_NOTIFY,
                                    JsonUtil.toJson(progress),
                                    requestId,
                                )
                            }
                        }
                    }.awaitAll()
                }
            } catch (_: CancellationException) {
                // cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "SpeedTestService failed", e)
            } finally {
                sendFinish(requestId)
                if (activeRequestId == requestId) activeRequestId = ""
                stopSelf(startId)
            }
        }
    }

    /**
     * Start a temporary core for [guid], measure download then upload via HTTP proxy.
     * @return Pair(downloadBps, uploadBps); negative values indicate failure.
     */
    private fun measureThroughProfile(guid: String): Pair<Long, Long> {
        val result = CoreConfigManager.getV2rayConfig4Speedtest(this, guid)
        if (!result.status || result.content.isBlank()) {
            LogUtil.w(AppConfig.TAG, "Speed test: no config for $guid (${result.errorMessage})")
            return -1L to -1L
        }

        val config = JsonUtil.parseString(result.content) ?: return -1L to -1L
        val inbounds = JsonArray()
        config.add("inbounds", inbounds)

        val httpPort = Utils.findRandomFreePort()
        var socksPort = Utils.findRandomFreePort()
        while (socksPort == httpPort) {
            socksPort = Utils.findRandomFreePort()
        }
        inbounds.add(createSocksInbound(socksPort))
        inbounds.add(createHttpInbound(httpPort))

        val controller = CoreNativeManager.newCoreController(SpeedCallback())
        return try {
            controller.startLoop(JsonUtil.toJson(config), 0)
            if (!waitForProxy(httpPort)) {
                LogUtil.w(AppConfig.TAG, "Speed test: proxy not ready on $httpPort for $guid")
                return -1L to -1L
            }
            // Let outbound handshake settle (same idea as country-code probe)
            Thread.sleep(200)

            if (cancelled.get()) return -1L to -1L
            var downloadBps = SpeedtestManager.measureDownloadSpeed(httpPort)
            if (downloadBps <= 0L && !cancelled.get()) {
                Thread.sleep(150)
                downloadBps = SpeedtestManager.measureDownloadSpeed(httpPort)
            }

            if (cancelled.get()) return downloadBps to -1L
            var uploadBps = SpeedtestManager.measureUploadSpeed(httpPort)
            if (uploadBps <= 0L && !cancelled.get()) {
                Thread.sleep(150)
                uploadBps = SpeedtestManager.measureUploadSpeed(httpPort)
            }

            downloadBps to uploadBps
        } finally {
            runCatching { controller.stopLoop() }
        }
    }

    private fun waitForProxy(port: Int, timeoutMs: Int = 4000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!cancelled.get() && System.currentTimeMillis() < deadline) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(AppConfig.LOOPBACK, port), 200)
                }
                return true
            } catch (_: Exception) {
                Thread.sleep(100)
            }
        }
        return false
    }

    private fun createSocksInbound(port: Int): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "speed-socks")
            addProperty("listen", AppConfig.LOOPBACK)
            addProperty("port", port)
            addProperty("protocol", "socks")
            add("settings", JsonObject().apply {
                addProperty("auth", "noauth")
                addProperty("udp", true)
            })
        }
    }

    private fun createHttpInbound(port: Int): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "speed-http")
            addProperty("listen", AppConfig.LOOPBACK)
            addProperty("port", port)
            addProperty("protocol", "http")
            add("settings", JsonObject())
        }
    }

    private fun handleCancel(requestId: String) {
        cancelled.set(true)
        worker?.cancel()
        worker = null
        sendFinish(requestId.ifEmpty { activeRequestId })
        activeRequestId = ""
        NotificationHelper.stopForeground(this)
        stopSelf()
    }

    private fun sendFinish(requestId: String) {
        MessageUtil.sendMsg2UI(this, AppConfig.MSG_SPEED_TEST_FINISH, "0", requestId)
    }

    private class SpeedCallback : CoreCallbackHandler {
        override fun startup(): Long = 0L
        override fun shutdown(): Long = 0L
        override fun onEmitStatus(l: Long, s: String?): Long = 0L
    }
}
