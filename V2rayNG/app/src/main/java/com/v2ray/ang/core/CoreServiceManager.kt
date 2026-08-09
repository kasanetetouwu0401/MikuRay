package com.v2ray.ang.core

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.system.OsConstants
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.aidl.IMikuRayService
import com.v2ray.ang.aidl.IMikuRayServiceCallback
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.dto.OutboundTrafficStat
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.BrowserDialerMode
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.service.DialerNativeService
import com.v2ray.ang.service.DialerWebviewService
import com.v2ray.ang.contracts.IDialerService
import com.v2ray.ang.service.NetworkMonitor
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.jvm.Volatile
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.ProcessFinder
import java.io.File
import java.net.InetSocketAddress

object CoreServiceManager {

    private val coreController: CoreController = CoreNativeManager.newCoreController(CoreCallback())
    private val mMsgReceive = ReceiveMessageHandler()

    /**
     * AIDL binder for [IMikuRayService]. Bound by [MikuRayConnection] from MainViewModel /
     * QSTileService. See the class doc on MikuRayConnection for why this replaces the old
     * broadcast command channel. Each broadcastXxx() below also still fires the legacy
     * MessageUtil.sendMsg2UI() so WidgetProvider (a stateless AppWidgetProvider that can't
     * hold a bound connection) keeps working unchanged.
     */
    val binder = Binder()

    private var currentConfig: ProfileItem? = null
    private var processFinder: XrayProcessFinder? = null
    private var browserDialer: IDialerService? = null
    private var networkMonitor: NetworkMonitor? = null

    @Volatile
    private var isReloading = false

    /** Tun descriptor the core was started with, null in the proxy only and root run modes. */
    private var currentVpnInterface: ParcelFileDescriptor? = null

    // NOTE: this used to be a SoftReference<ServiceControl>. The daemon process
    // (:RunSoLibV2RayDaemon) can come under enough memory pressure while a large/complex
    // custom routing config is being built (first run especially, with cold geosite/geoip
    // caches) that ART reclaims SoftReferences well before the Service itself is destroyed.
    // When that happened, serviceControl?.get() silently returned null and every subsequent
    // MSG_STATE_STOP / MSG_REGISTER_CLIENT broadcast from the UI was
    // dropped with no log and no user-visible error - the FAB and bottom status card looked
    // "unresponsive" even though the receiver itself was alive and firing. A plain strong
    // reference is safe here because we explicitly null it out in each service's onDestroy()
    // via clearServiceControl(), so it never outlives the Service.
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

    /**
     * Clears [serviceControl] from a service's onDestroy(), but only if it still points at
     * that same instance - guards against a just-created replacement service (e.g. during a
     * quick restart) being wiped out by the old instance's onDestroy() running afterward.
     */
    fun clearServiceControl(instance: ServiceControl) {
        if (serviceControl === instance) {
            serviceControl = null
        }
    }

    /**
     * Checks if the V2Ray service is running.
     * @return True if the service is running, false otherwise.
     */
    fun isRunning() = coreController.isRunning

    /**
     * Gets the name of the currently running server.
     * @return The name of the running server.
     */
    fun getRunningServerName() = currentConfig?.remarks.orEmpty()

    /**
     * Refer to the official documentation for [registerReceiver](https://developer.android.com/reference/androidx/core/content/ContextCompat#registerReceiver(android.content.Context,android.content.BroadcastReceiver,android.content.IntentFilter,int):
     * `registerReceiver(Context, BroadcastReceiver, IntentFilter, int)`.
     * Starts the V2Ray core service.
     */
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
            binder.broadcastStateStartFailure(service, message)
            NotificationManager.cancelNotification()
            return false
        }
    }

    @Throws(Exception::class)
    private fun doStartCoreLoop(service: Service, vpnInterface: ParcelFileDescriptor?) {
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE)
        mFilter.addAction(Intent.ACTION_SCREEN_ON)
        mFilter.addAction(Intent.ACTION_SCREEN_OFF)
        mFilter.addAction(Intent.ACTION_USER_PRESENT)
        ContextCompat.registerReceiver(service, mMsgReceive, mFilter, Utils.receiverFlags())

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
            binder.broadcastStateStartSuccess(service)
        }
        NotificationManager.startSpeedNotification()
        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core started successfully")
    }

    /**
     * Stops the V2Ray core service.
     * Unregisters broadcast receivers, stops notifications, and shuts down plugins.
     * @return True if the core was stopped successfully, false otherwise.
     */
    fun stopCoreLoop(): Boolean {
        val service = getService() ?: return false

        networkMonitor?.unregister()
        networkMonitor = null
        currentVpnInterface = null

        if (isRunning()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    coreController.stopLoop()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to stop V2Ray loop", e)
                }
            }
        }

        // Close existing browser dialer
        CoreNativeManager.reconcileBrowserDialer("")
        if (browserDialer != null) {
            browserDialer!!.stop()
            browserDialer = null
        }

        binder.broadcastStateStopSuccess(service)
        NotificationManager.cancelNotification()

        try {
            service.unregisterReceiver(mMsgReceive)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to unregister receiver", e)
        }

        return true
    }

    /**
     * Subscribes to upstream network changes for whichever run mode is active.
     * All three services share this manager, so the tunnel recovers from a handover in proxy only
     * and root mode as well, not just behind the VPN interface.
     */
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

    /**
     * Restarts the core in place after the upstream network changed: the service, the notification
     * and the VPN interface all stay up, so nothing of this is visible.
     *
     * The config is rebuilt on purpose, outbound server domains are resolved while building it and
     * an address resolved on a network that is gone can be unusable on the new one.
     *
     * @return True if the core is running again.
     */
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
            binder.broadcastStateStartFailure(service, message)
            false
        } finally {
            isReloading = false
        }
    }

    /**
     * Queries and resets all outbound traffic counters in one core call.
     * Go side format: tag,direction,value;tag,direction,value;
     */
    fun queryAllOutboundTrafficStats(): List<OutboundTrafficStat> {
        // The stats manager is gone once the core stops, querying it then reaches into freed state.
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
//        LogUtil.d(AppConfig.TAG, "Queried outbound traffic stats: $result")
        return result
    }

    /**
     * Measures the connection delay for the current V2Ray configuration and returns the
     * elapsed time in ms, throwing on failure. Ported from Exclave's
     * `BaseService.Binder#urlTest()`: unlike the old broadcast-based measureV2rayDelay()
     * this runs synchronously on the AIDL binder thread the caller invoked it from (see
     * [Binder.urlTest]) and hands the result straight back over the return value/exception
     * instead of a separate measureDelayResult() callback round-trip. Tests with the
     * primary URL first, then falls back to the alternative URL if needed, and fires off a
     * remote-IP lookup in the background on success (delivered via the existing
     * measureIpResult callback/broadcast, same as before).
     */
    private fun measureV2rayDelay(): Long {
        if (!isRunning()) {
            error("core not started")
        }

        var time = -1L
        var lastError: Exception? = null
        try {
            time = coreController.measureDelay(SettingsManager.getDelayTestUrl())
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to measure delay", e)
            lastError = e
        }
        if (time < 0) {
            try {
                time = coreController.measureDelay(SettingsManager.getDelayTestUrl(true))
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to measure delay", e)
                lastError = e
            }
        }

        if (time < 0) {
            throw lastError ?: IllegalStateException("timeout")
        }

        getService()?.let { service ->
            CoroutineScope(Dispatchers.IO).launch {
                val ip = SpeedtestManager.getRemoteIPInfo()
                binder.broadcastMeasureIpResult(service, ip.orEmpty())
            }
        }

        return time
    }

    private fun measureIpOnly() {
        if (!isRunning()) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val service = getService() ?: return@launch
            val ip = SpeedtestManager.getRemoteIPInfo()
            binder.broadcastMeasureIpResult(service, ip.orEmpty())
        }
    }

    /**
     * Gets the current service instance.
     * @return The current service instance, or null if not available.
     */
    private fun getService(): Service? {
        return serviceControl?.getService()
    }

    /**
     * Core callback handler implementation for handling V2Ray core events.
     * Handles startup, shutdown, socket protection, and status emission.
     */
    private class CoreCallback : CoreCallbackHandler {
        /**
         * Called when V2Ray core starts up.
         * @return 0 for success, any other value for failure.
         */
        override fun startup(): Long {
            return 0
        }

        /**
         * Called when V2Ray core shuts down.
         * @return 0 for success, any other value for failure.
         */
        override fun shutdown(): Long {
            val serviceControl = serviceControl ?: return -1
            return try {
                serviceControl.stopService()
                0
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to stop service", e)
                -1
            }
        }

        /**
         * Called when V2Ray core emits status information.
         * @param l Status code.
         * @param s Status message.
         * @return Always returns 0.
         */
        override fun onEmitStatus(l: Long, s: String?): Long {
            return 0
        }
    }

    /**
     * Process finder implementation for Xray core.
     * Uses ConnectivityManager to find the owning UID of a connection based on network parameters.
     */
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
                //LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to $destIP:$destPort, uid=$uid,${PackageUidResolver.uidToPackageName(uid.toString())}")

                uid
            } catch (_: Exception) {
                -1L
            }
        }
    }

    /**
     * Broadcast receiver for the small set of messages that can only ever arrive as a
     * broadcast: notification/widget action buttons (PendingIntent.getBroadcast(), which
     * has no bindable caller context to hang an AIDL connection off) and OS screen-on/off
     * events. Every other control (register/stop/restart/test/traffic) that originates from
     * a live UI now goes through [binder] via [MikuRayConnection] instead - see its class
     * doc for why.
     */
    private class ReceiveMessageHandler : BroadcastReceiver() {
        /**
         * Handles received broadcast messages.
         * @param ctx The context in which the receiver is running.
         * @param intent The intent being received.
         */
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val serviceControl = serviceControl ?: run {
                LogUtil.w(
                    AppConfig.TAG,
                    "StartCore-Manager: Dropped msg key=${intent?.getIntExtra("key", 0)}, serviceControl is null"
                )
                return
            }
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_STOP -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Stop service")
                    serviceControl.stopService()
                }

                AppConfig.MSG_STATE_RESTART -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Restart service")
                    serviceControl.stopService()
                    Thread.sleep(500L)
                    LauncherManager.startService(serviceControl.getService())
                }
            }

            when (intent?.action) {
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

    /**
     * [IMikuRayService] implementation returned from each core service's onBind(). Ported
     * from Exclave's BaseService.Binder, minus the Data/proxy-instance plumbing MikuRay
     * doesn't need since CoreServiceManager itself is already the single daemon-process
     * singleton (see the serviceControl comment above for why a plain object works here).
     */
    class Binder : IMikuRayService.Stub() {
        private val callbacks = RemoteCallbackList<IMikuRayServiceCallback>()

        override fun isRunning(): Boolean = CoreServiceManager.isRunning()
        override fun getRunningServerName(): String = CoreServiceManager.getRunningServerName()

        override fun registerCallback(cb: IMikuRayServiceCallback) {
            callbacks.register(cb)
            // WidgetProvider can't hold a bound connection (see the class doc on
            // MikuRayConnection), so it relies on an opportunistic MSG_STATE_RUNNING/
            // NOT_RUNNING broadcast whenever a real client (MainViewModel, QSTileService)
            // shows up - previously sent in reply to MSG_REGISTER_CLIENT, now sent here on
            // every AIDL registration instead, same trigger points (app open, tile shown).
            val service = serviceControl?.getService() ?: return
            if (isRunning()) broadcastStateRunning(service) else broadcastStateNotRunning(service)
        }

        override fun unregisterCallback(cb: IMikuRayServiceCallback) {
            callbacks.unregister(cb)
        }

        override fun requestStop() {
            serviceControl?.stopService()
        }

        override fun requestRestart() {
            val serviceControl = serviceControl ?: return
            serviceControl.stopService()
            Thread.sleep(500L)
            LauncherManager.startService(serviceControl.getService())
        }

        // Blocking - runs on the AIDL binder thread the caller invoked it from (Binder
        // transactions for non-oneway methods already execute off the caller's main
        // thread), matching Exclave's BaseService.Binder#urlTest(). Any exception thrown
        // here propagates transparently back to the caller through the AIDL transaction.
        override fun urlTest(): Int {
            return measureV2rayDelay().toInt()
        }

        override fun measureIp() {
            measureIpOnly()
        }

        private inline fun broadcast(action: (IMikuRayServiceCallback) -> Unit) {
            val count = callbacks.beginBroadcast()
            try {
                repeat(count) {
                    try {
                        action(callbacks.getBroadcastItem(it))
                    } catch (_: RemoteException) {
                    }
                }
            } finally {
                callbacks.finishBroadcast()
            }
        }

        fun broadcastStateRunning(service: Service) {
            broadcast { it.stateRunning() }
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_RUNNING, "")
        }

        fun broadcastStateNotRunning(service: Service) {
            broadcast { it.stateNotRunning() }
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_NOT_RUNNING, "")
        }

        // stateStartSuccess()/etc. below are still pushed on real transitions; the two
        // methods above only exist for the registerCallback() sync described there.
        fun broadcastStateStartSuccess(service: Service) {
            broadcast { it.stateStartSuccess() }
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
        }

        fun broadcastStateStartFailure(service: Service, message: String) {
            broadcast { it.stateStartFailure(message) }
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, message)
        }

        fun broadcastStateStopSuccess(service: Service) {
            broadcast { it.stateStopSuccess() }
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
        }

        fun broadcastMeasureIpResult(service: Service, ip: String) {
            broadcast { it.measureIpResult(ip) }
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_MEASURE_IP_SUCCESS, ip)
        }

        fun broadcastTrafficUpdated(service: Service, guid: String) {
            broadcast { it.trafficUpdated(guid) }
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_TRAFFIC_UPDATED, guid)
        }

        fun broadcastTrafficSpeedUpdated(service: Service, speedText: String) {
            broadcast { it.trafficSpeedUpdated(speedText) }
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_TRAFFIC_SPEED_UPDATED, speedText)
        }
    }
}
