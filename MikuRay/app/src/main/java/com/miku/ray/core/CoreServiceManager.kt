package com.miku.ray.core

import android.app.Service
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import com.miku.ray.AppConfig
import com.miku.ray.R
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
            reportStartFailure(service, message)
            NotificationManager.cancelNotification()
            return false
        }
    }

    @Throws(Exception::class)
    private fun doStartCoreLoop(service: Service, vpnInterface: ParcelFileDescriptor?) {
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
            emitServiceEvent(com.miku.ray.aidl.AidlProtocol.EVENT_STATE_START_SUCCESS, restarted.toString())
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
            emitServiceEvent(com.miku.ray.aidl.AidlProtocol.EVENT_STATE_STOP_SUCCESS)
        }
        NotificationManager.cancelNotification()

        return true
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
            emitServiceEvent(com.miku.ray.aidl.AidlProtocol.EVENT_STATE_START_FAILURE, message)
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
            emitServiceEvent(com.miku.ray.aidl.AidlProtocol.EVENT_MEASURE_DELAY, result)

            if (time >= 0) {
                emitServiceEvent(com.miku.ray.aidl.AidlProtocol.EVENT_MEASURE_IP, ip.orEmpty())
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
            emitServiceEvent(com.miku.ray.aidl.AidlProtocol.EVENT_MEASURE_IP, ip.orEmpty())
        }
    }

    fun emitServiceEvent(event: Int, content: String = "") {
        serviceControl?.getAidlBinder()?.emit(event, content)
    }

    fun handleAidlCommand(command: Int, content: String): Boolean {
        val control = serviceControl ?: return false
        return when (command) {
            com.miku.ray.aidl.AidlProtocol.CORE_STOP -> {
                serviceRestartLifecycle.cancel()
                control.stopService()
                true
            }
            com.miku.ray.aidl.AidlProtocol.CORE_RESTART -> {
                val service = control.getService()
                val launched = serviceRestartLifecycle.launch(
                    onStarting = { emitServiceEvent(com.miku.ray.aidl.AidlProtocol.EVENT_STATE_RESTART) },
                ) { token ->
                    try {
                        control.stopService()
                        if (!waitForCoreToStop()) {
                            reportRestartFailure(service, token, "Timed out waiting for core to stop")
                            return@launch
                        }
                        if (!serviceRestartLifecycle.isCurrent(token)) return@launch
                        if (!LauncherManager.startServiceAfterRestart(service)) {
                            reportRestartFailure(service, token, "")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
                        LogUtil.e(AppConfig.TAG, "StartCore-Manager: Restart failed: $message", e)
                        reportRestartFailure(service, token, message)
                    }
                }
                launched
            }
            com.miku.ray.aidl.AidlProtocol.CORE_MEASURE_DELAY -> {
                measureV2rayDelay()
                true
            }
            com.miku.ray.aidl.AidlProtocol.CORE_MEASURE_IP -> {
                measureIpOnly()
                true
            }
            else -> false
        }
    }

    private fun getService(): Service? {
        return serviceControl?.getService()
    }

    internal fun reportStartFailure(service: Service, message: String) {
        serviceRestartLifecycle.completeCurrent()
        emitServiceEvent(com.miku.ray.aidl.AidlProtocol.EVENT_STATE_START_FAILURE, message)
    }

    private fun reportRestartFailure(
        service: Service,
        token: ServiceRestartLifecycle.Token,
        message: String,
    ) {
        if (serviceRestartLifecycle.complete(token)) {
            emitServiceEvent(com.miku.ray.aidl.AidlProtocol.EVENT_STATE_START_FAILURE, message)
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
