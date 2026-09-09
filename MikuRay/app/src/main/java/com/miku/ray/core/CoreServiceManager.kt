package com.miku.ray.core

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteCallbackList
import android.system.OsConstants
import androidx.core.content.ContextCompat
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.aidl.ICoreService
import com.miku.ray.aidl.ICoreServiceCallback
import com.miku.ray.contracts.ServiceControl
import com.miku.ray.dto.OutboundTrafficStat
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.BrowserDialerMode
import com.miku.ray.extension.delay
import com.miku.ray.extension.isNotNullEmpty
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.NotificationManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.handler.SpeedtestManager
import com.miku.ray.service.DialerNativeService
import com.miku.ray.service.DialerWebviewService
import com.miku.ray.contracts.IDialerService
import com.miku.ray.service.NetworkMonitor
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.jvm.Volatile
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.ProcessFinder
import java.io.File
import java.net.InetSocketAddress

object CoreServiceManager {

    private const val RESTART_STOP_TIMEOUT_MS = 5_000
    private const val RESTART_STOP_POLL_INTERVAL_MS = 50

    private val coreController: CoreController = CoreNativeManager.newCoreController(CoreCallback())
    private var currentConfig: ProfileItem? = null
    private var processFinder: XrayProcessFinder? = null
    private var browserDialer: IDialerService? = null
    private var networkMonitor: NetworkMonitor? = null
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiverRegistered = false

    @Volatile
    private var isReloading = false

    private val serviceRestartLifecycle = ServiceRestartLifecycle()

    private var currentVpnInterface: ParcelFileDescriptor? = null

    var serviceControl: ServiceControl? = null
    set(value) {
        field = value
        val service = value?.getService()
        CoreNativeManager.initCoreEnv(service)
        if (service != null && processFinder == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            processFinder = XrayProcessFinder(service)
            coreController.registerProcessFinder(processFinder)
        }
    }

    fun clearServiceControl(instance: ServiceControl) {
        if (serviceControl === instance) {
            serviceControl = null
        }
    }

    fun isRunning() = coreController.isRunning

    fun getRunningServerName() = currentConfig?.remarks.orEmpty()

    // ---------------------------------------------------------------------
    // AIDL binder (NekoBox-style ISagerNetService equivalent)
    // ---------------------------------------------------------------------

    private val callbacks = RemoteCallbackList<ICoreServiceCallback>()

    private val binder = object : ICoreService.Stub() {
        override fun getState(): Int = when {
            isRunning() -> AppConfig.MSG_STATE_RUNNING
            else -> AppConfig.MSG_STATE_NOT_RUNNING
        }

        override fun getProfileName(): String = getRunningServerName()

        override fun registerCallback(cb: ICoreServiceCallback?) {
            cb?.let { callbacks.register(it) }
        }

        override fun unregisterCallback(cb: ICoreServiceCallback?) {
            cb?.let { callbacks.unregister(it) }
        }

        override fun requestRestart(): Boolean =
            this@CoreServiceManager.requestRestart()

        override fun stopCore() = handleStopCommand()

        override fun measureDelay() = this@CoreServiceManager.measureV2rayDelay()

        override fun measureIpOnly() = this@CoreServiceManager.measureIpOnly()
    }

    val coreBinder: IBinder
        get() = binder

    fun notifyStateChanged(state: Int, msg: String) {
        val profileName = if (state == AppConfig.MSG_STATE_RUNNING ||
            state == AppConfig.MSG_STATE_START_SUCCESS
        ) {
            getRunningServerName()
        } else {
            ""
        }
        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                callbacks.getBroadcastItem(i).stateChanged(state, profileName, msg)
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "StartCore-Manager: Failed to notify state", e)
            }
        }
        callbacks.finishBroadcast()

        // Widgets live outside the binder lifecycle; keep a minimal
        // package-scoped state broadcast for them (replaces the old msg2 UI broadcast).
        try {
            getService()?.sendBroadcast(
                Intent(AppConfig.ACTION_WIDGET_UPDATE)
                .setPackage(AppConfig.ANG_PACKAGE)
                .putExtra("kind", AppConfig.WIDGET_UPDATE_KIND_STATE)
                .putExtra("state", state)
            )
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "StartCore-Manager: Failed to broadcast widget state", e)
        }
    }

    fun notifySpeedUpdate(speedText: String) {
        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                callbacks.getBroadcastItem(i).cbSpeedUpdate(speedText)
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "StartCore-Manager: Failed to notify speed", e)
            }
        }
        callbacks.finishBroadcast()
    }

    fun notifyTrafficUpdate(guid: String) {
        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                callbacks.getBroadcastItem(i).cbTrafficUpdate(guid)
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "StartCore-Manager: Failed to notify traffic", e)
            }
        }
        callbacks.finishBroadcast()

        try {
            getService()?.sendBroadcast(
                Intent(AppConfig.ACTION_WIDGET_UPDATE)
                .setPackage(AppConfig.ANG_PACKAGE)
                .putExtra("kind", AppConfig.WIDGET_UPDATE_KIND_TRAFFIC)
            )
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "StartCore-Manager: Failed to broadcast widget traffic", e)
        }
    }

    private fun notifyDelayResult(result: String) {
        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                callbacks.getBroadcastItem(i).cbMeasureDelayResult(result)
            } catch (_: Exception) {
            }
        }
        callbacks.finishBroadcast()
    }

    private fun notifyIpResult(ip: String) {
        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                callbacks.getBroadcastItem(i).cbMeasureIpResult(ip)
            } catch (_: Exception) {
            }
        }
        callbacks.finishBroadcast()
    }

    // ---------------------------------------------------------------------
    // Commands (explicit actions, replaces the msg2 broadcast protocol)
    // ---------------------------------------------------------------------

    fun handleStopCommand() {
        val sc = serviceControl ?: run {
            LogUtil.w(AppConfig.TAG, "StartCore-Manager: Stop command ignored, serviceControl is null")
            return
        }
        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Stop service")
        serviceRestartLifecycle.cancel()
        sc.stopService()
    }

    fun handleRestartCommand() {
        val sc = serviceControl ?: run {
            LogUtil.w(AppConfig.TAG, "StartCore-Manager: Restart command ignored, serviceControl is null")
            return
        }
        if (!isRunning()) return

        val launched = try {
            serviceRestartLifecycle.launch(
                onStarting = {
                    notifyStateChanged(AppConfig.MSG_STATE_RESTART, "")
                },
            ) { token ->
                try {
                    sc.stopService()
                    if (!waitForCoreToStop()) {
                        val message = "Timed out waiting for core to stop"
                        LogUtil.e(AppConfig.TAG, "StartCore-Manager: $message")
                        reportRestartFailure(sc.getService(), token, message)
                        return@launch
                    }
                    if (!serviceRestartLifecycle.isCurrent(token)) return@launch
                    val startRequested = LauncherManager.startServiceAfterRestart(
                        sc.getService(),
                    )
                    if (!startRequested) {
                        reportRestartFailure(sc.getService(), token, "")
                    }
                } catch (e: CancellationException) {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Restart canceled")
                    throw e
                } catch (e: Exception) {
                    val message = e.message?.takeUnless { it.isBlank() }
                    ?: e.javaClass.simpleName
                    LogUtil.e(AppConfig.TAG, "StartCore-Manager: Restart failed: $message", e)
                    reportRestartFailure(sc.getService(), token, message)
                }
            }
        } catch (e: Exception) {
            val message = e.message?.takeUnless { it.isBlank() }
            ?: e.javaClass.simpleName
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to schedule restart: $message", e)
            reportStartFailure(sc.getService(), message)
            return
        }
        if (!launched) {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Restart already in progress")
        }
    }

    private fun requestRestart(): Boolean {
        val sc = serviceControl ?: return false
        if (!isRunning()) return false
        handleRestartCommand()
        return true
    }

    fun isRunningLive(): Boolean = isRunning()

    // ---------------------------------------------------------------------
    // Core lifecycle
    // ---------------------------------------------------------------------

    fun startCoreLoop(vpnInterface: ParcelFileDescriptor?): Boolean {
        if (isRunning()) {
            LogUtil.w(AppConfig.TAG, "StartCore-Manager: Core already running")
            return false
        }

        val service = getService()
        if (service == null) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Service is null")
            return false
        }

        try {
            doStartCoreLoop(service, vpnInterface)
            return true
        } catch (e: Exception) {
            val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: $message", e)
            cleanupServiceReceiver(service)
            reportStartFailure(service, message)
            NotificationManager.cancelNotification()
            return false
        }
    }

    @Throws(Exception::class)
    private fun doStartCoreLoop(service: Service, vpnInterface: ParcelFileDescriptor?) {
        val mFilter = IntentFilter().apply {
            addAction(AppConfig.ACTION_CORE_STOP)
            addAction(AppConfig.ACTION_CORE_RESTART)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(service, commandReceiver, mFilter, Utils.receiverFlags())
        receiverRegistered = true

        currentVpnInterface = vpnInterface
        launchCore(service, vpnInterface)
        startNetworkMonitor(service)
    }

    @Throws(Exception::class)
    private fun launchCore(service: Service, vpnInterface: ParcelFileDescriptor?, isReload: Boolean = false) {
        val guid = MmkvManager.getSelectServer() ?: error("No server selected")
        val config = MmkvManager.decodeServerConfig(guid) ?: error("Failed to decode server config")

        SettingsManager.initAssets(service, service.assets)
        val assetFolder = Utils.userAssetPath(service)
        val missingGeoFiles = listOf(AppConfig.GEOSITE_DAT, AppConfig.GEOIP_DAT)
        .filterNot { File(assetFolder, it).exists() }
        if (missingGeoFiles.isNotEmpty()) {
            error("Geo data file not found: ${missingGeoFiles.joinToString()}. Try clearing the app data and then reopening it.")
        }

        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Starting core loop for ${config.remarks}")
        val result = CoreConfigManager.getV2rayConfig(service, guid)
        LogUtil.d(AppConfig.TAG, result.content)
        if (!result.status) {
            error(result.errorMessage.ifBlank { "Failed to get V2Ray config" })
        }

        currentConfig = config
        var tunFd = vpnInterface?.fd ?: 0
        val dialerMode = BrowserDialerMode.from(config.browserDialerMode)
        val dialerAddr = if (dialerMode != null) {
            "127.0.0.1:${Utils.findRandomFreePort()}"
        } else {
            ""
        }
        if (SettingsManager.isUsingHevTun()) {
            tunFd = 0
        }

        NotificationManager.showNotification(currentConfig)
        if (dialerAddr.isNotNullEmpty()) {
            CoreNativeManager.reconcileBrowserDialer(dialerAddr)
        }
        coreController.startLoop(result.content, tunFd)

        if (!isRunning()) {
            error("Core failed to start")
        }

        if (browserDialer != null) {
            browserDialer!!.stop()
            browserDialer = null
        }
        when (dialerMode) {
            BrowserDialerMode.OKHTTP -> {
                browserDialer = DialerNativeService()
                browserDialer!!.start(service, dialerAddr)
            }

            BrowserDialerMode.WEBVIEW -> {
                browserDialer = DialerWebviewService()
                browserDialer!!.start(service, dialerAddr)
            }

            else -> {}
        }

        if (!isReload) {
            val restarted = serviceRestartLifecycle.completeCurrent()
            notifyStateChanged(AppConfig.MSG_STATE_START_SUCCESS, restarted.toString())
        }
        NotificationManager.startSpeedNotification()
        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core started successfully")
    }

    fun stopCoreLoop(): Boolean {
        val service = getService() ?: return false

        networkMonitor?.unregister()
        networkMonitor = null
        currentVpnInterface = null

        if (isRunning()) {
            backgroundScope.launch {
                try {
                    coreController.stopLoop()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to stop V2Ray loop", e)
                }
            }
        }

        CoreNativeManager.reconcileBrowserDialer("")
        if (browserDialer != null) {
            browserDialer!!.stop()
            browserDialer = null
        }

        if (!serviceRestartLifecycle.isActive()) {
            notifyStateChanged(AppConfig.MSG_STATE_STOP_SUCCESS, "")
        }
        NotificationManager.cancelNotification()

        cleanupServiceReceiver(service)

        return true
    }

    private fun cleanupServiceReceiver(service: Service) {
        if (!receiverRegistered) return
        try {
            service.unregisterReceiver(commandReceiver)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to unregister receiver", e)
        } finally {
            receiverRegistered = false
        }
    }

    private fun startNetworkMonitor(service: Service) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (networkMonitor != null) return

        val connectivity = service.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkMonitor = NetworkMonitor(
            connectivity = connectivity,
            onUnderlyingNetworksChanged = { networks -> serviceControl?.setUnderlyingNetworks(networks) },
            onHandover = { reloadCore() },
        ).also { it.register() }
    }

    private fun reloadCore(): Boolean {
        if (isReloading) return false
        val service = getService() ?: return false
        if (!isRunning()) return false

        return try {
            val tunFd = currentVpnInterface

            isReloading = true
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core reload start...")

            coreController.stopLoop()
            launchCore(service, tunFd, isReload = true)

            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core reload finished")
            true
        } catch (e: Exception) {
            val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to reload core: $message", e)
            notifyStateChanged(AppConfig.MSG_STATE_START_FAILURE, message)
            false
        } finally {
            isReloading = false
        }
    }

    fun queryAllOutboundTrafficStats(): List<OutboundTrafficStat> {
        if (!isRunning()) return emptyList()

        val payload = coreController.queryAllOutboundTrafficStats()

        val result = ArrayList<OutboundTrafficStat>()

        payload.split(';').forEach { entry ->
            if (entry.isBlank()) return@forEach

            val parts = entry.split(',', limit = 3)
            if (parts.size != 3) return@forEach

            val value = parts[2].toLongOrNull() ?: return@forEach

            result.add(
                OutboundTrafficStat(
                    tag = parts[0],
                    direction = parts[1],
                    value = value,
                )
            )
        }
        return result
    }

    private fun measureV2rayDelay() {
        if (!isRunning()) {
            return
        }

        backgroundScope.launch {
            val service = getService() ?: return@launch
            var time = -1L
            var errorStr = ""

            try {
                time = coreController.measureDelay(SettingsManager.getDelayTestUrl())
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to measure delay", e)
                errorStr = e.message?.substringAfter("\":") ?: "empty message"
            }
            if (time == -1L) {
                try {
                    time = coreController.measureDelay(SettingsManager.getDelayTestUrl(true))
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to measure delay", e)
                    errorStr = e.message?.substringAfter("\":") ?: "empty message"
                }
            }

            val ip = if (time >= 0) SpeedtestManager.getRemoteIPInfo() else null
            val result = if (time >= 0) {
                service.getString(R.string.connection_test_available, time)
            } else {
                service.getString(R.string.connection_test_error, errorStr)
            }
            notifyDelayResult(result)

            if (time >= 0) {
                notifyIpResult(ip.orEmpty())
            }
        }
    }

    private fun measureIpOnly() {
        if (!isRunning()) {
            return
        }

        backgroundScope.launch {
            val service = getService() ?: return@launch
            val ip = SpeedtestManager.getRemoteIPInfo()
            notifyIpResult(ip.orEmpty())
        }
    }

    private fun getService(): Service? {
        return serviceControl?.getService()
    }

    internal fun reportStartFailure(service: Service, message: String) {
        serviceRestartLifecycle.completeCurrent()
        notifyStateChanged(AppConfig.MSG_STATE_START_FAILURE, message)
    }

    private fun reportRestartFailure(
        service: Service,
        token: ServiceRestartLifecycle.Token,
        message: String,
    ) {
        if (serviceRestartLifecycle.complete(token)) {
            notifyStateChanged(AppConfig.MSG_STATE_START_FAILURE, message)
        }
    }

    private suspend fun waitForCoreToStop(): Boolean {
        var waitedMs = 0
        while (isRunning() && waitedMs < RESTART_STOP_TIMEOUT_MS) {
            delay(RESTART_STOP_POLL_INTERVAL_MS)
            waitedMs += RESTART_STOP_POLL_INTERVAL_MS
        }
        return !isRunning()
    }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (serviceControl == null) {
                LogUtil.w(
                    AppConfig.TAG,
                    "StartCore-Manager: Dropped command action=${intent?.action}, serviceControl is null"
                )
                return
            }
            when (intent?.action) {
                AppConfig.ACTION_CORE_STOP -> handleStopCommand()

                AppConfig.ACTION_CORE_RESTART -> handleRestartCommand()

                Intent.ACTION_SCREEN_OFF -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Screen off")
                    NotificationManager.stopSpeedNotification()
                }

                Intent.ACTION_SCREEN_ON -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Screen on")
                    NotificationManager.startSpeedNotification()
                }
            }
        }
    }

    private class CoreCallback : CoreCallbackHandler {
        override fun startup(): Long {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: CoreCallback startup")
            return 0
        }

        override fun shutdown(): Long {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: CoreCallback shutdown")
            return 0
        }

        override fun onEmitStatus(l: Long, s: String?): Long {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: CoreCallback onEmitStatus $s")
            return 0
        }
    }

    private class XrayProcessFinder(context: Context) : ProcessFinder {
        private val cm: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

        override fun findProcessByConnection(network: String, srcIP: String, srcPort: Long, destIP: String, destPort: Long): Long {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1L
            if (cm == null) return -1L
            val proto = when (network) {
                "tcp" -> OsConstants.IPPROTO_TCP
                "udp" -> OsConstants.IPPROTO_UDP
                else -> return -1L
            }

            if (destIP.isBlank() || destPort == 0L) {
                LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to :$destPort, (no dest)")
                return -1L
            }

            return try {
                val uid = cm.getConnectionOwnerUid(
                    proto,
                    InetSocketAddress(srcIP, srcPort.toInt()),
                    InetSocketAddress(destIP, destPort.toInt())
                ).toLong()
                LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to $destIP:$destPort, uid=$uid")

                uid
            } catch (_: Exception) {
                -1L
            }
        }
    }
}
