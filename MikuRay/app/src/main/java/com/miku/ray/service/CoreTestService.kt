package com.miku.ray.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.miku.ray.AppConfig
import com.miku.ray.R
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
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MessageUtil
import com.miku.ray.helper.NotificationHelper
import com.miku.ray.remixicon.R as RemixR

/**
 * Mirrors v2rayNG's CoreTestService lifecycle: a batch is cancelled and
 * replaced in place, inside the same process/instance.
 *
 * The previous design here (`batchStarted` flag + `Process.killProcess` +
 * `START_REDELIVER_INTENT`) killed the whole process whenever a second START
 * arrived before the first batch's teardown had fully finished, and asked
 * Android to redeliver the intent to a "fresh" instance. That redelivery
 * could silently drop the new batch (or its FINISH summary) depending on how
 * fast the OS respawned the process, leaving MainViewModel's `isTesting`
 * stuck true forever with no event to clear it. There is no upside to a
 * disposable *service* process here (unlike the native probe work itself, or
 * the CoreTestService->cancel flow used to kill+redeliver on purpose), so
 * this now just tracks the one active worker and cancels it synchronously
 * before starting a new one - exactly what v2rayNG's MainViewModel already
 * does at the call site via `dataSource.cancelAllPing()`.
 */
class CoreTestService : Service() {

    private val lock = Any()

    @Volatile
    private var activeMessage: TestServiceMessage? = null

    @Volatile
    private var activeWorker: RealPingWorkerService? = null

    private val cancelAction by lazy {
        val intent = Intent(this, CoreTestService::class.java).putExtra(
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        LogUtil.i(AppConfig.TAG, "CoreTestService is being destroyed")
        cancelActiveWorker(sendSummary = false)
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

        when (message.key) {
            AppConfig.MSG_MEASURE_CONFIG_START -> handleMeasureStart(message, startId)
            AppConfig.MSG_MEASURE_CONFIG_CANCEL -> handleMeasureCancel(startId)
            else -> {
                NotificationHelper.stopForeground(this)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun handleMeasureStart(message: TestServiceMessage, startId: Int) {
        // A new batch always supersedes whatever is currently running - same
        // rule v2rayNG's ViewModel enforces by cancelling before starting.
        cancelActiveWorker(sendSummary = true)

        val guids = when {
            message.serverGuids.isNotEmpty() -> message.serverGuids
            message.subscriptionId.isNotEmpty() -> MmkvManager.decodeServerList(message.subscriptionId)
            else -> MmkvManager.decodeAllServerList()
        }.distinct()

        if (guids.isEmpty()) {
            sendSummary(RealPingSummary(testId = message.testId, live = 0, total = 0, cancelled = false))
            NotificationHelper.stopForeground(this)
            stopSelf(startId)
            return
        }

        LogUtil.i(AppConfig.TAG, "CoreTestService starting ${guids.size} probes")
        activeMessage = message
        activeWorker = RealPingWorkerService(
            context = this,
            guids = guids,
            onlyTcp = message.onlyTcp,
            onEvent = { event -> handleWorkerEvent(event, message) },
        ).also { it.start() }
    }

    private fun handleWorkerEvent(event: RealPingEvent, message: TestServiceMessage) {
        if (activeMessage !== message) return
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
                MessageUtil.sendMsg2UI(
                    this,
                    AppConfig.MSG_MEASURE_CONFIG_NOTIFY,
                    RealPingProgress(message.testId, event.completed, event.total),
                )
            }

            is RealPingEvent.Result -> {
                MmkvManager.encodeServerTestDelayMillis(event.guid, event.delayMillis)
                MessageUtil.sendMsg2UI(
                    this,
                    AppConfig.MSG_MEASURE_CONFIG_SUCCESS,
                    RealPingResult(message.testId, event.guid, event.delayMillis),
                )
            }

            is RealPingEvent.Finish -> {
                val shouldFinish = synchronized(lock) {
                    if (activeMessage !== message) return@synchronized false
                    activeWorker = null
                    activeMessage = null
                    true
                }
                if (shouldFinish) {
                    finishBatch(message, event)
                    NotificationHelper.stopForeground(this)
                    stopSelf()
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
    }

    private fun handleMeasureCancel(startId: Int) {
        LogUtil.i(AppConfig.TAG, "CoreTestService cancelling the active batch")
        cancelActiveWorker(sendSummary = true)
        NotificationHelper.stopForeground(this)
        stopSelf(startId)
    }

    private fun cancelActiveWorker(sendSummary: Boolean) = synchronized(lock) {
        val message = activeMessage ?: return
        val summary = activeWorker?.cancel()
        activeWorker = null
        activeMessage = null
        if (sendSummary && summary != null) {
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

    private fun sendSummary(summary: RealPingSummary) {
        MessageUtil.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_FINISH, summary)
    }
}
