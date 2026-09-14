package com.miku.ray.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.miku.ray.AppConfig
import com.miku.ray.core.CoreConfigManager
import com.miku.ray.core.CoreNativeManager
import com.miku.ray.dto.SpeedTestMessage
import com.miku.ray.dto.SpeedTestProgress
import com.miku.ray.enums.NotificationChannelType
import com.miku.ray.extension.serializable
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.handler.SpeedtestManager
import com.miku.ray.helper.NotificationHelper
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.MessageUtil
import com.miku.ray.util.Utils
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

class SpeedTestService : Service() {
    private val cancelled = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var worker: Job? = null
    private var activeRequestId = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.serializable<SpeedTestMessage>("content")
        if (message == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (message.key == AppConfig.MSG_SPEED_TEST_CANCEL) {
            cancelled.set(true)
            worker?.cancel()
            sendFinish(message.requestId.ifEmpty { activeRequestId })
            NotificationHelper.stopForeground(this)
            stopSelf()
            return START_NOT_STICKY
        }
        if (message.key != AppConfig.MSG_SPEED_TEST_START) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        worker?.cancel()
        cancelled.set(false)
        activeRequestId = message.requestId
        val guids = if (message.serverGuids.isNotEmpty()) message.serverGuids
        else MmkvManager.decodeServerList(message.subscriptionId)
        if (guids.isEmpty()) {
            sendFinish(message.requestId)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        NotificationHelper.startForeground(
            this,
            NotificationChannelType.CORE_TEST,
            getString(com.miku.ray.R.string.title_speed_test),
            getString(com.miku.ray.R.string.title_speed_test),
        )
        worker = scope.launch(Dispatchers.IO.limitedParallelism(SettingsManager.getRealPingConcurrency())) {
            try {
                SettingsManager.initAssets(this@SpeedTestService, assets)
                CoreNativeManager.initCoreEnv(this@SpeedTestService)
                val completed = AtomicInteger(0)
                supervisorScope {
                    guids.distinct().map { guid ->
                        async {
                            currentCoroutineContext().ensureActive()
                            val result = runCatching { speedTestThroughProfile(guid) }
                                .getOrElse { SpeedtestManager.SpeedTestResult(error = it.message) }
                            if (!cancelled.get()) {
                                val progress = SpeedTestProgress(
                                    guid = guid,
                                    current = completed.incrementAndGet(),
                                    total = guids.size,
                                    downloadMbps = result.downloadMbps ?: 0.0,
                                    uploadMbps = result.uploadMbps ?: 0.0,
                                    error = result.error,
                                )
                                MessageUtil.sendMsg2UI(this@SpeedTestService, AppConfig.MSG_SPEED_TEST_NOTIFY, JsonUtil.toJson(progress), message.requestId)
                            }
                        }
                    }.awaitAll()
                }
            } catch (_: CancellationException) {
            } finally {
                sendFinish(message.requestId)
                NotificationHelper.stopForeground(this@SpeedTestService)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun speedTestThroughProfile(guid: String): SpeedtestManager.SpeedTestResult {
        val result = CoreConfigManager.getV2rayConfig4Speedtest(this, guid)
        if (!result.status || result.content.isBlank()) return SpeedtestManager.SpeedTestResult(error = "Invalid profile")
        val config = JsonUtil.parseString(result.content) ?: return SpeedtestManager.SpeedTestResult(error = "Invalid core config")
        val httpPort = Utils.findRandomFreePort()
        val inbounds = JsonArray().apply { add(createHttpInbound(httpPort)) }
        config.add("inbounds", inbounds)
        val controller = CoreNativeManager.newCoreController(NoopCallback())
        return try {
            controller.startLoop(JsonUtil.toJson(config), 0)
            if (!waitForProxy(httpPort)) return SpeedtestManager.SpeedTestResult(error = "Proxy startup timeout")
            SpeedtestManager.runSpeedTestThroughProxy(httpPort)
        } finally {
            runCatching { controller.stopLoop() }
        }
    }

    private fun waitForProxy(port: Int): Boolean {
        val deadline = System.currentTimeMillis() + 2_000
        while (!cancelled.get() && System.currentTimeMillis() < deadline) {
            try {
                Socket().use { it.connect(InetSocketAddress(AppConfig.LOOPBACK, port), 120) }
                return true
            } catch (_: Exception) {
                Thread.sleep(60)
            }
        }
        return false
    }

    private fun createHttpInbound(port: Int) = JsonObject().apply {
        addProperty("tag", "speed-test-http")
        addProperty("listen", AppConfig.LOOPBACK)
        addProperty("port", port)
        addProperty("protocol", "http")
        add("settings", JsonObject())
    }

    private fun sendFinish(requestId: String) {
        MessageUtil.sendMsg2UI(this, AppConfig.MSG_SPEED_TEST_FINISH, "", requestId)
    }

    private class NoopCallback : CoreCallbackHandler {
        override fun startup(): Long = 0L
        override fun shutdown(): Long = 0L
        override fun onEmitStatus(l: Long, s: String?): Long = 0L
    }
}
