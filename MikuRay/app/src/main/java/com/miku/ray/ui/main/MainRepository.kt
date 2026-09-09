package com.miku.ray.ui.main

import com.miku.ray.AngApplication
import com.miku.ray.AppConfig
import com.miku.ray.aidl.JobServiceConnection
import com.miku.ray.aidl.MikuRayConnection
import com.miku.ray.core.PreStartFailureNotifier
import com.miku.ray.core.ServiceCommands
import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.RealPingResult
import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.dto.entities.SubscriptionCache
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.service.CountryCodeTestService
import com.miku.ray.service.CoreTestService
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AIDL-backed implementation of [MainDataSource]. Replaces the old BROADCAST_ACTION_ACTIVITY
 * BroadcastReceiver + MessageUtil.sendMsg2Service/sendMsg2TestService/sendMsg2CountryCodeTestService
 * plumbing with three live service connections, modeled after NekoBox's SagerConnection.
 */
class MainRepository(
    private val app: AngApplication,
) : MainDataSource {
    private val closed = AtomicBoolean(false)

    private val _mainServiceEvent = MutableSharedFlow<MainServiceEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val mainServiceEvent: SharedFlow<MainServiceEvent> = _mainServiceEvent.asSharedFlow()

    private val _rawServiceEvents = MutableSharedFlow<RawServiceEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val rawServiceEvents: SharedFlow<RawServiceEvent> = _rawServiceEvents.asSharedFlow()

    private val mainConnection = MikuRayConnection { key, content -> onEvent(key, content) }
    private val testConnection = JobServiceConnection(CoreTestService::class.java) { key, content -> onEvent(key, content) }
    private val countryCodeConnection = JobServiceConnection(CountryCodeTestService::class.java) { key, content -> onEvent(key, content) }

    init {
        PreStartFailureNotifier.listener = { message ->
            onEvent(AppConfig.MSG_STATE_START_FAILURE, message)
        }
        mainConnection.connect(app)
        testConnection.connect(app)
        countryCodeConnection.connect(app)
    }

    private fun onEvent(key: Int, content: String?) {
        _rawServiceEvents.tryEmit(RawServiceEvent(key, content))

        val event = when (key) {
            AppConfig.MSG_STATE_RUNNING -> MainServiceEvent.StateRunning
            AppConfig.MSG_STATE_NOT_RUNNING -> MainServiceEvent.StateNotRunning
            AppConfig.MSG_STATE_START_SUCCESS -> MainServiceEvent.StateStartSuccess
            AppConfig.MSG_STATE_START_FAILURE -> MainServiceEvent.StateStartFailure
            AppConfig.MSG_STATE_STOP_SUCCESS -> MainServiceEvent.StateStopSuccess
            AppConfig.MSG_MEASURE_DELAY_SUCCESS -> content
                ?.let { JsonUtil.fromJsonSafe(it, RealPingResult::class.java) }
                ?.let(MainServiceEvent::MeasureDelayResult)
            AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> MainServiceEvent.MeasureConfigSuccess
            AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> MainServiceEvent.MeasureConfigNotify(content.orEmpty())
            AppConfig.MSG_MEASURE_CONFIG_FINISH -> MainServiceEvent.MeasureConfigFinish(content)
            else -> null
        }
        event?.let { _mainServiceEvent.tryEmit(it) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        PreStartFailureNotifier.listener = null
        runCatching { mainConnection.disconnect(app) }
            .onFailure { LogUtil.e(AppConfig.TAG, "Failed to disconnect main core connection", it) }
        runCatching { testConnection.disconnect(app) }
            .onFailure { LogUtil.e(AppConfig.TAG, "Failed to disconnect test service connection", it) }
        runCatching { countryCodeConnection.disconnect(app) }
            .onFailure { LogUtil.e(AppConfig.TAG, "Failed to disconnect country code service connection", it) }
    }

    override fun getSelectedSubscriptionId(): String =
        MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()

    override fun updateConfigViaSubAll(): SubscriptionUpdateResult =
        AngConfigManager.updateConfigViaSubAll()

    override fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult =
        AngConfigManager.updateConfigViaSub(subscriptionCache)

    override fun shareNonCustomConfigsToClipboard(guids: List<String>): Int =
        AngConfigManager.shareNonCustomConfigsToClipboard(app, guids)

    override fun resyncState() {
        mainConnection.connect(app)
        val running = mainConnection.getState() == 1
        onEvent(if (running) AppConfig.MSG_STATE_RUNNING else AppConfig.MSG_STATE_NOT_RUNNING, "")
    }

    override fun requestMeasureDelay() {
        mainConnection.requestMeasureDelay()
    }

    override fun requestMeasureIp() {
        mainConnection.requestMeasureIp()
    }

    override suspend fun startCoreTest(msg: TestServiceMessage) {
        testConnection.reconnectIfNeeded(app)
        ServiceCommands.startCoreTest(app, msg)
    }

    override suspend fun cancelCoreTest(msg: TestServiceMessage) {
        testConnection.reconnectIfNeeded(app)
        ServiceCommands.cancelCoreTest(app, msg)
    }

    override suspend fun startCountryCodeTest(msg: CountryCodeTestMessage) {
        countryCodeConnection.reconnectIfNeeded(app)
        ServiceCommands.startCountryCodeTest(app, msg)
    }

    override suspend fun cancelCountryCodeTest(msg: CountryCodeTestMessage) {
        countryCodeConnection.reconnectIfNeeded(app)
        ServiceCommands.cancelCountryCodeTest(app, msg)
    }
}
