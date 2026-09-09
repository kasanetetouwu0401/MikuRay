package com.miku.ray.ui.main

import androidx.core.content.ContextCompat
import com.miku.ray.AngApplication
import com.miku.ray.aidl.AidlProtocol
import com.miku.ray.aidl.MikuRayServiceConnection
import com.miku.ray.aidl.ServiceClassResolver
import com.miku.ray.dto.RealPingProgress
import com.miku.ray.dto.RealPingResult
import com.miku.ray.dto.RealPingSummary
import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.TestProgressInfo
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.entities.SubscriptionCache
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import com.miku.ray.service.CoreTestService
import com.miku.ray.service.CountryCodeTestService
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

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

    private var pendingCoreCommand: Pair<Int, String>? = null
    private var pendingTestCommand: Pair<Int, String>? = null
    private var pendingCountryCommand: Pair<Int, String>? = null

    private lateinit var coreConnection: MikuRayServiceConnection

    private fun reconnectCoreLater() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!closed.get()) coreConnection.connect()
        }, 1000L)
    }

    private val testConnection = MikuRayServiceConnection(
        context = app,
        serviceClass = CoreTestService::class.java,
        autoCreate = true,
        onEvent = ::handleTestEvent,
        onConnected = { service ->
            pendingTestCommand?.let { (command, content) ->
                pendingTestCommand = null
                runCatching { service.command(command, content) }
            }
        },
    )

    private val countryConnection = MikuRayServiceConnection(
        context = app,
        serviceClass = CountryCodeTestService::class.java,
        autoCreate = true,
        onEvent = ::handleCountryEvent,
        onConnected = { service ->
            pendingCountryCommand?.let { (command, content) ->
                pendingCountryCommand = null
                runCatching { service.command(command, content) }
            }
        },
    )

    init {
        coreConnection = MikuRayServiceConnection(
            context = app,
            serviceClass = ServiceClassResolver.coreServiceClass(),
            autoCreate = false,
            onEvent = ::handleCoreEvent,
            onConnected = { service ->
                pendingCoreCommand?.let { (command, content) ->
                    pendingCoreCommand = null
                    runCatching { service.command(command, content) }
                }
            },
            onDisconnected = ::reconnectCoreLater,
        )
        coreConnection.connect()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        coreConnection.disconnect()
        testConnection.disconnect()
        countryConnection.disconnect()
    }

    override fun getSelectedSubscriptionId(): String =
        MmkvManager.decodeSettingsString(com.miku.ray.AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()

    override fun updateConfigViaSubAll(): SubscriptionUpdateResult =
        AngConfigManager.updateConfigViaSubAll()

    override fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult =
        AngConfigManager.updateConfigViaSub(subscriptionCache)

    override fun shareNonCustomConfigsToClipboard(guids: List<String>): Int =
        AngConfigManager.shareNonCustomConfigsToClipboard(app, guids)

    override fun sendServiceCommand(command: Int, content: String) {
        if (closed.get()) return
        if (coreConnection.service != null && coreConnection.command(command, content)) return
        pendingCoreCommand = command to content
        coreConnection.connect()
    }

    override fun sendTestService(message: TestServiceMessage) {
        if (closed.get()) return
        if (message.key == com.miku.ray.aidl.AidlProtocol.TEST_CANCEL) {
            testConnection.command(AidlProtocol.TEST_CANCEL, JsonUtil.toJson(message))
            return
        }
        val started = startForegroundService(CoreTestService::class.java)
        if (!started) return
        val content = JsonUtil.toJson(message)
        if (testConnection.service != null) {
            testConnection.command(AidlProtocol.TEST_START, content)
        } else {
            pendingTestCommand = AidlProtocol.TEST_START to content
            testConnection.connect()
        }
    }

    override fun sendCountryCodeTestService(message: CountryCodeTestMessage) {
        if (closed.get()) return
        if (message.key == com.miku.ray.aidl.AidlProtocol.COUNTRY_CANCEL) {
            countryConnection.command(AidlProtocol.COUNTRY_CANCEL, JsonUtil.toJson(message))
            return
        }
        val started = startForegroundService(CountryCodeTestService::class.java)
        if (!started) return
        val content = JsonUtil.toJson(message)
        if (countryConnection.service != null) {
            countryConnection.command(AidlProtocol.COUNTRY_START, content)
        } else {
            pendingCountryCommand = AidlProtocol.COUNTRY_START to content
            countryConnection.connect()
        }
    }

    override fun testCurrentServerRealPing() {
        sendServiceCommand(AidlProtocol.CORE_MEASURE_DELAY)
    }

    fun resyncCoreState() {
        if (coreConnection.service == null) coreConnection.connect()
        coreConnection.service?.let { service ->
            runCatching {
                if (service.getState() == 1) {
                    _mainServiceEvent.tryEmit(MainServiceEvent.StateRunning)
                } else {
                    _mainServiceEvent.tryEmit(MainServiceEvent.StateNotRunning)
                }
            }
        }
    }

    private fun startForegroundService(serviceClass: Class<*>): Boolean = runCatching {
        val intent = android.content.Intent(app, serviceClass).setAction(AidlProtocol.SERVICE_ACTION)
        ContextCompat.startForegroundService(app, intent)
        true
    }.getOrElse {
        LogUtil.e(com.miku.ray.AppConfig.TAG, "Failed to start ${serviceClass.simpleName}", it)
        false
    }

    private fun handleCoreEvent(event: Int, content: String) {
        when (event) {
            AidlProtocol.EVENT_STATE_RUNNING -> _mainServiceEvent.tryEmit(MainServiceEvent.StateRunning)
            AidlProtocol.EVENT_STATE_NOT_RUNNING -> _mainServiceEvent.tryEmit(MainServiceEvent.StateNotRunning)
            AidlProtocol.EVENT_STATE_START_SUCCESS -> _mainServiceEvent.tryEmit(MainServiceEvent.StateStartSuccess(content.toBoolean()))
            AidlProtocol.EVENT_STATE_START_FAILURE -> _mainServiceEvent.tryEmit(MainServiceEvent.StateStartFailure(content))
            AidlProtocol.EVENT_STATE_STOP_SUCCESS -> _mainServiceEvent.tryEmit(MainServiceEvent.StateStopSuccess)
            AidlProtocol.EVENT_STATE_RESTART -> _mainServiceEvent.tryEmit(MainServiceEvent.StateRestart)
            AidlProtocol.EVENT_MEASURE_DELAY -> _mainServiceEvent.tryEmit(MainServiceEvent.MeasureDelayText(content))
            AidlProtocol.EVENT_MEASURE_IP -> _mainServiceEvent.tryEmit(MainServiceEvent.MeasureIp(content))
            AidlProtocol.EVENT_TRAFFIC_SPEED_UPDATED -> _mainServiceEvent.tryEmit(MainServiceEvent.TrafficSpeed(content))
            AidlProtocol.EVENT_TRAFFIC_UPDATED -> _mainServiceEvent.tryEmit(MainServiceEvent.TrafficUpdated(content))
        }
    }

    private fun handleTestEvent(event: Int, content: String) {
        when (event) {
            AidlProtocol.EVENT_TEST_SUCCESS ->
                JsonUtil.fromJsonSafe(content, RealPingResult::class.java)?.let {
                    _mainServiceEvent.tryEmit(MainServiceEvent.MeasureConfigSuccess(it))
                }
            AidlProtocol.EVENT_TEST_NOTIFY ->
                JsonUtil.fromJsonSafe(content, RealPingProgress::class.java)?.let {
                    _mainServiceEvent.tryEmit(MainServiceEvent.MeasureConfigNotify(it))
                }
            AidlProtocol.EVENT_TEST_FINISH ->
                JsonUtil.fromJsonSafe(content, RealPingSummary::class.java)?.let {
                    _mainServiceEvent.tryEmit(MainServiceEvent.MeasureConfigFinish(it))
                }
        }
    }

    private fun handleCountryEvent(event: Int, content: String) {
        when (event) {
            AidlProtocol.EVENT_COUNTRY_SUCCESS -> _mainServiceEvent.tryEmit(MainServiceEvent.CountryCodeSuccess(content))
            AidlProtocol.EVENT_COUNTRY_NOTIFY ->
                JsonUtil.fromJsonSafe(content, TestProgressInfo::class.java)?.let {
                    _mainServiceEvent.tryEmit(MainServiceEvent.CountryCodeNotify(it))
                }
            AidlProtocol.EVENT_COUNTRY_FINISH -> _mainServiceEvent.tryEmit(MainServiceEvent.CountryCodeFinish)
        }
    }
}
