package com.miku.ray.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import androidx.core.app.NotificationCompat
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.aidl.ICoreTestService
import com.miku.ray.aidl.ICoreTestServiceCallback
import com.miku.ray.core.CoreNativeManager
import com.miku.ray.dto.RealPingEvent
import com.miku.ray.dto.RealPingProgress
import com.miku.ray.dto.RealPingResult
import com.miku.ray.dto.RealPingSummary
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.enums.NotificationChannelType
import com.miku.ray.extension.serializable
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import com.miku.ray.helper.NotificationHelper
import com.miku.ray.remixicon.R as RemixR

class CoreTestService : Service() {
    @Volatile
    private var activeWorker: RealPingWorkerService? = null

    @Volatile
    private var activeMessage: TestServiceMessage? = null

    @Volatile
    private var suppressWorkerEvents = false

    private val terminalLock = Any()
    private var batchStarted = false

    private val callbacks = RemoteCallbackList<ICoreTestServiceCallback>()

    private val binder = object : ICoreTestService.Stub() {
        override fun registerCallback(cb: ICoreTestServiceCallback?) {
            cb?.let { callbacks.register(it) }
        }

        override fun unregisterCallback(cb: ICoreTestServiceCallback?) {
            cb?.let { callbacks.unregister(it) }
        }

        override fun cancelTest(testId: String?) {
            this@CoreTestService.cancelActiveBatch()
        }
    }

    private val cancelAction by lazy {
        val intent = Intent(this, CoreTestService::class.java)
        .setAction(AppConfig.ACTION_TEST_CANCEL)
        .putExtra(
            "content",
            TestServiceMessage(AppConfig.MSG_MEASURE_CONFIG_CANCEL),
        )
        val pendingIntent = PendingIntent.getService(
            this,
            NotificationChannelType.CORE_TEST.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationCompat.Action.Builder(
            RemixR.drawable.rmx_media_stop_line,
            getString(android.R.string.cancel),
            pendingIntent,
        ).build()
    }

    override fun onCreate() {
        super.onCreate()
        CoreNativeManager.initCoreEnv(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        LogUtil.i(AppConfig.TAG, "CoreTestService is being destroyed")
        suppressWorkerEvents = true
        activeWorker?.cancel()
        activeWorker = null
        activeMessage = null
        callbacks.kill()
        NotificationHelper.stopForeground(this)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.serializable<TestServiceMessage>("content")
        val isTcping = message?.onlyTcp == true
        NotificationHelper.startForeground(
            this,
            NotificationChannelType.CORE_TEST,
            getString(R.string.app_name),
            getString(if (isTcping) R.string.title_ping_all_server else R.string.title_real_ping_all_server),
            cancelAction,
        )
        if (message == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        return when (message.key) {
            AppConfig.MSG_MEASURE_CONFIG_START -> handleMeasureStart(message, startId)
            AppConfig.MSG_MEASURE_CONFIG_CANCEL -> cancelActiveBatch()
            else -> {
                NotificationHelper.stopForeground(this)
                stopSelf(startId)
                START_NOT_STICKY
            }
        }
    }

    private fun handleMeasureStart(message: TestServiceMessage, startId: Int): Int {
        if (batchStarted) {
            synchronized(terminalLock) {
                suppressWorkerEvents = true
                activeWorker?.cancel()
            }
            LogUtil.i(AppConfig.TAG, "CoreTestService replacing the active batch with a new one")
        }
        batchStarted = true
        suppressWorkerEvents = false
        activeMessage = message

        val guids = when {
            message.serverGuids.isNotEmpty() -> message.serverGuids
            message.subscriptionId.isNotEmpty() -> MmkvManager.decodeServerList(message.subscriptionId)
            else -> MmkvManager.decodeAllServerList()
        }.distinct()

        if (guids.isEmpty()) {
            sendSummary(
                RealPingSummary(
                    testId = message.testId,
                    live = 0,
                    total = 0,
                    cancelled = false,
                ),
            )
            NotificationHelper.stopForeground(this)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        LogUtil.i(AppConfig.TAG, "CoreTestService starting ${guids.size} probes")
        activeWorker = RealPingWorkerService(
            context = this,
            guids = guids,
            onlyTcp = message.onlyTcp,
            onEvent = ::handleWorkerEvent,
        ).also { it.start() }
        return START_NOT_STICKY
    }

    private fun cancelActiveBatch(): Int {
        LogUtil.i(AppConfig.TAG, "CoreTestService cancelling the active batch")
        synchronized(terminalLock) {
            suppressWorkerEvents = true
            val message = activeMessage
            val summary = activeWorker?.cancel()
            activeWorker = null
            activeMessage = null
            if (message != null && summary != null) {
                sendSummary(
                    RealPingSummary(
                        testId = message.testId,
                        live = summary.live,
                        total = summary.total,
                        cancelled = true,
                    ),
                )
            }
        }
        NotificationHelper.stopForeground(this)
        stopSelf()
        return START_NOT_STICKY
    }

    private fun handleWorkerEvent(event: RealPingEvent) {
        if (suppressWorkerEvents) return
        val message = activeMessage ?: return
        when (event) {
            is RealPingEvent.Progress -> {
                val progressText = "${event.completed} / ${event.total}"
                val progressTextRes = if (message.onlyTcp) {
                    R.string.connection_runing_tcping_task_left
                } else {
                    R.string.connection_runing_real_delay_task_left
                }
                NotificationHelper.updateNotification(
                    channelType = NotificationChannelType.CORE_TEST,
                    context = this,
                    title = getString(if (message.onlyTcp) R.string.title_ping_all_server else R.string.title_real_ping_all_server),
                    content = getString(progressTextRes, progressText),
                )

                notifyProgress(
                    JsonUtil.toJson(RealPingProgress(message.testId, event.completed, event.total)),
                )
            }

            is RealPingEvent.Result -> {
                MmkvManager.encodeServerTestDelayMillis(event.guid, event.delayMillis)
                notifyResult(
                    JsonUtil.toJson(RealPingResult(message.testId, event.guid, event.delayMillis)),
                )
            }

            is RealPingEvent.Finish -> synchronized(terminalLock) {
                if (!suppressWorkerEvents && activeMessage == message) {
                    finishBatch(message, event)
                }
            }
        }
    }

    private fun finishBatch(message: TestServiceMessage, event: RealPingEvent.Finish) {
        val autoRemove = message.subscriptionId.isNotEmpty() &&
        MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)
        val autoSort = message.subscriptionId.isNotEmpty() &&
        MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)
        val listBefore = if (autoRemove || autoSort) {
            MmkvManager.decodeServerList(message.subscriptionId)
        } else {
            emptyList()
        }
        if (autoRemove) {
            AngConfigManager.removeInvalidServer(message.subscriptionId)
        }
        if (autoSort) {
            AngConfigManager.sortByTestResultsForSub(message.subscriptionId)
        }
        val listChanged = (autoRemove || autoSort) &&
        listBefore != MmkvManager.decodeServerList(message.subscriptionId)

        sendSummary(
            RealPingSummary(
                testId = message.testId,
                live = event.live,
                total = event.total,
                cancelled = false,
                listChanged = listChanged,
            ),
        )
        suppressWorkerEvents = true
        activeWorker = null
        activeMessage = null
        NotificationHelper.stopForeground(this)
        stopSelf()
    }

    private fun notifyProgress(json: String) {
        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                callbacks.getBroadcastItem(i).onTestProgress(json)
            } catch (_: Exception) {
            }
        }
        callbacks.finishBroadcast()
    }

    private fun notifyResult(json: String) {
        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                callbacks.getBroadcastItem(i).onTestResult(json)
            } catch (_: Exception) {
            }
        }
        callbacks.finishBroadcast()
    }

    private fun sendSummary(summary: RealPingSummary) {
        val json = JsonUtil.toJson(summary)
        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                callbacks.getBroadcastItem(i).onTestFinish(json)
            } catch (_: Exception) {
            }
        }
        callbacks.finishBroadcast()
    }
}
