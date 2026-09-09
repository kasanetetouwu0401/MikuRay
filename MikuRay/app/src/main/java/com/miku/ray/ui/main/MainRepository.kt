package com.miku.ray.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.miku.ray.AngApplication
import com.miku.ray.AppConfig
import com.miku.ray.aidl.ICoreServiceCallback
import com.miku.ray.aidl.ICoreTestService
import com.miku.ray.aidl.ICoreTestServiceCallback
import com.miku.ray.aidl.ICountryCodeTestService
import com.miku.ray.aidl.ICountryCodeTestServiceCallback
import com.miku.ray.core.BinderServiceFactory
import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.dto.entities.SubscriptionCache
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.service.CoreTestService
import com.miku.ray.service.CountryCodeTestService
import com.miku.ray.util.LogUtil
import com.miku.ray.util.ServiceCommands
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

    private var coreService: ICoreService? = null
    private var testService: ICoreTestService? = null
    private var countryService: ICountryCodeTestService? = null
    private var coreBoundClass: Class<*>? = null
    private var coreBound = false
    private var testBound = false
    private var countryBound = false

    private fun emit(event: MainServiceEvent) {
        _mainServiceEvent.tryEmit(event)
    }

    // ------------------------------------------------------------------
    // AIDL callback stubs (service -> UI)
    // ------------------------------------------------------------------

    private val coreCallback = object : ICoreServiceCallback.Stub() {
        override fun stateChanged(state: Int, profileName: String?, msg: String?) {
            val event = when (state) {
                AppConfig.MSG_STATE_RUNNING -> MainServiceEvent.StateRunning
                AppConfig.MSG_STATE_NOT_RUNNING -> MainServiceEvent.StateNotRunning
                AppConfig.MSG_STATE_START_SUCCESS -> MainServiceEvent.StateStartSuccess(msg.orEmpty())
                AppConfig.MSG_STATE_START_FAILURE -> MainServiceEvent.StateStartFailure(msg.orEmpty())
                AppConfig.MSG_STATE_STOP_SUCCESS -> MainServiceEvent.StateStopSuccess
                AppConfig.MSG_STATE_RESTART -> MainServiceEvent.StateRestart
                else -> return
            }
            emit(event)
        }

        override fun cbSpeedUpdate(speedText: String?) {
            emit(MainServiceEvent.TrafficSpeedUpdated(speedText.orEmpty()))
        }

        override fun cbTrafficUpdate(guid: String?) {
            emit(MainServiceEvent.TrafficUpdated(guid.orEmpty()))
        }

        override fun cbMeasureDelayResult(result: String?) {
            emit(MainServiceEvent.MeasureDelayResult(result.orEmpty()))
        }

        override fun cbMeasureIpResult(ip: String?) {
            emit(MainServiceEvent.MeasureIpResult(ip.orEmpty()))
        }
    }

    private val testCallback = object : ICoreTestServiceCallback.Stub() {
        override fun onTestProgress(json: String?) {
            emit(MainServiceEvent.MeasureConfigNotify(json.orEmpty()))
        }

        override fun onTestResult(json: String?) {
            emit(MainServiceEvent.MeasureConfigSuccess(json.orEmpty()))
        }

        override fun onTestFinish(json: String?) {
            emit(MainServiceEvent.MeasureConfigFinish(json))
        }
    }

    private val countryCallback = object : ICountryCodeTestServiceCallback.Stub() {
        override fun onCountryCodeSuccess(guid: String?) {
            emit(MainServiceEvent.CountryCodeSuccess(guid.orEmpty()))
        }

        override fun onCountryCodeProgress(json: String?) {
            emit(MainServiceEvent.CountryCodeNotify(json.orEmpty()))
        }

        override fun onCountryCodeFinish() {
            emit(MainServiceEvent.CountryCodeFinish)
        }
    }

    // ------------------------------------------------------------------
    // ServiceConnections (UI -> service binders)
    // ------------------------------------------------------------------

    private val coreConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = ICoreService.Stub.asInterface(binder ?: return) ?: return
            coreService = service
            try {
                service.registerCallback(coreCallback)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to register core service callback", e)
            }
            resyncState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            coreService = null
        }
    }

    private val testConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = ICoreTestService.Stub.asInterface(binder ?: return) ?: return
            testService = service
            try {
                service.registerCallback(testCallback)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to register test service callback", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            testService = null
        }
    }

    private val countryConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = ICountryCodeTestService.Stub.asInterface(binder ?: return) ?: return
            countryService = service
            try {
                service.registerCallback(countryCallback)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to register country code service callback", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            countryService = null
        }
    }

    // ------------------------------------------------------------------

    fun connect() {
        if (closed.get()) return
        val wantedClass = BinderServiceFactory.coreServiceClass()
        if (coreBound && coreBoundClass != wantedClass) {
            // Connection mode changed (VPN / Root / ProxyOnly) while listening.
            runCatching { app.unbindService(coreConnection) }
            coreBound = false
            coreService = null
        }
        if (!coreBound) {
            try {
                app.bindService(
                    Intent(app, wantedClass),
                    coreConnection,
                    Context.BIND_AUTO_CREATE,
                )
                coreBound = true
                coreBoundClass = wantedClass
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to bind core service", e)
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (coreBound) runCatching { app.unbindService(coreConnection) }
        if (testBound) runCatching { app.unbindService(testConnection) }
        if (countryBound) runCatching { app.unbindService(countryConnection) }
        coreBound = false
        testBound = false
        countryBound = false
        coreService = null
        testService = null
        countryService = null
    }

    override fun resyncState() {
        val service = coreService
        if (service == null) {
            connect()
            return
        }
        try {
            emit(
                if (service.state == AppConfig.MSG_STATE_RUNNING) {
                    MainServiceEvent.StateRunning
                } else {
                    MainServiceEvent.StateNotRunning
                }
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to query core service state", e)
        }
    }

    override fun startRealPingTest(msg: TestServiceMessage) {
        ensureTestBound()
        ServiceCommands.startTestService(app, msg)
    }

    override fun cancelRealPingTestService(testId: String) {
        val service = testService
        if (service != null) {
            try {
                service.cancelTest(testId)
                return
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "AIDL test cancel failed, falling back to service command", e)
            }
        }
        ServiceCommands.cancelTestService(app, testId)
    }

    override fun startCountryCodeTest(msg: CountryCodeTestMessage) {
        ensureCountryBound()
        ServiceCommands.startCountryCodeService(app, msg)
    }

    override fun cancelCountryCodeTestService() {
        val service = countryService
        if (service != null) {
            try {
                service.cancelTest()
                return
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "AIDL country cancel failed, falling back to service command", e)
            }
        }
        ServiceCommands.cancelCountryCodeService(app)
    }

    override fun testCurrentServerRealPing() {
        val service = coreService
        if (service != null) {
            try {
                service.measureDelay()
                return
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to request delay measurement", e)
            }
        }
        connect()
    }

    override fun fetchCurrentIp() {
        val service = coreService
        if (service != null) {
            try {
                service.measureIpOnly()
                return
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to request ip check", e)
            }
        }
        connect()
    }

    override fun getSelectedSubscriptionId(): String =
        MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()

    override fun updateConfigViaSubAll(): SubscriptionUpdateResult =
        AngConfigManager.updateConfigViaSubAll()

    override fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult =
        AngConfigManager.updateConfigViaSub(subscriptionCache)

    override fun shareNonCustomConfigsToClipboard(guids: List<String>): Int =
        AngConfigManager.shareNonCustomConfigsToClipboard(app, guids)

    private fun ensureTestBound() {
        if (testBound || closed.get()) return
        try {
            app.bindService(
                Intent(app, CoreTestService::class.java),
                testConnection,
                Context.BIND_AUTO_CREATE,
            )
            testBound = true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to bind test service", e)
        }
    }

    private fun ensureCountryBound() {
        if (countryBound || closed.get()) return
        try {
            app.bindService(
                Intent(app, CountryCodeTestService::class.java),
                countryConnection,
                Context.BIND_AUTO_CREATE,
            )
            countryBound = true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to bind country code service", e)
        }
    }
}
