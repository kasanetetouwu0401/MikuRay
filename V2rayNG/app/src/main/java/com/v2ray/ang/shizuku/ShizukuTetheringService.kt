package com.v2ray.ang.shizuku

import android.content.Context
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs under the shell UID inside a dedicated Shizuku UserService process.
 *
 * Responsibilities (see MikuRay's Shizuku tethering notes for the full design):
 * 1. Create an Android test-network TUN and publish it as tethering's preferred upstream.
 * 2. Run a second hev-socks5-tunnel instance against that TUN, forwarding into the normal
 *    core's SOCKS inbound (HEV native state is process-global, so this only works because
 *    the UserService is a separate process from the app's own VPN daemon).
 * 3. Start/stop Wi-Fi hotspot and observe USB tethering, restoring the test network as the
 *    active upstream whenever Android tries to hand tethering back to a physical interface.
 * 4. Fail closed: never silently let tethered clients fall back to an unprotected route.
 *
 * This class intentionally has no Shizuku import at the top level beyond what
 * `IShizukuTetheringService.Stub` requires; the class is loaded by `Shizuku.bindUserService`
 * using `UserServiceArgs`, not started directly.
 */
class ShizukuTetheringService(private val context: Context) : IShizukuTetheringService.Stub() {

    companion object {
        const val ROUTING_STATE_IDLE = 0
        const val ROUTING_STATE_STARTING = 1
        const val ROUTING_STATE_ACTIVE = 2
        const val ROUTING_STATE_RETRYING = 3
        const val ROUTING_STATE_ERROR = 4

        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyStartService(configPath: String, fd: Int)

        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyStopService()

        init {
            // Loaded fresh in this process; HEV's native state is process-global, which is
            // exactly why the tethering engine needs its own process in the first place.
            try {
                System.loadLibrary("hev-socks5-tunnel")
            } catch (_: Throwable) {
                // If the .so can't be located from the shell UserService's classloader,
                // startRouting() below fails closed with ROUTING_STATE_ERROR.
            }
        }
    }

    private val lock = Any()
    private val executor = Executors.newSingleThreadExecutor()
    private var testNetwork: TetheringPlatformCompat.TestNetworkHandle? = null
    private var hevConfigFile: File? = null
    private var hevRunning = false
    private var currentToken: String? = null
    private val state = AtomicReference(ROUTING_STATE_IDLE)
    private var lastDetail: String = ""
    private var pendingWarning: String? = null
    private var unregisterCallback: (() -> Unit)? = null
    private var activeTypesMask = 0
    private var lease: ICoreTetheringLease? = null
    private var leaseDeathRecipient: IBinder.DeathRecipient? = null

    override fun getRoutingState(): Int = state.get()

    override fun getRoutingDetail(): String = lastDetail

    override fun getActiveTetheringTypes(): Int = activeTypesMask

    override fun setWifiHotspotEnabled(enabled: Boolean): Int {
        return try {
            val ok = if (enabled) {
                TetheringApi36.stopTetheringType(context, TetheringPlatformCompat.TETHERING_WIFI).let { }
                startWifiTethering()
            } else {
                TetheringApi36.stopTetheringType(context, TetheringPlatformCompat.TETHERING_WIFI)
            }
            if (ok) 0 else -1
        } catch (e: Throwable) {
            lastDetail = e.message ?: e.javaClass.simpleName
            -1
        }
    }

    override fun startRouting(
        hevConfigYaml: String,
        profileName: String,
        ipv6Enabled: Boolean,
        syncToken: String,
        lease: ICoreTetheringLease?,
    ): Int = synchronized(lock) {
        if (syncToken.isBlank()) return -1
        try {
            state.set(ROUTING_STATE_STARTING)
            adoptLease(lease)

            val ipv4 = AppConfigCompat.SHIZUKU_TUN_ADDR_V4
            val ipv6 = if (ipv6Enabled) AppConfigCompat.SHIZUKU_TUN_ADDR_V6 else null
            val handle = TetheringPlatformCompat.createTestNetwork(context, ipv4, ipv6)
            testNetwork = handle

            TetheringPlatformCompat.setPreferTestNetworks(context, true)
            startHevEngine(hevConfigYaml, handle)

            // Android only picks a tethering upstream once, when a downstream first comes
            // up. If Wi-Fi hotspot was already running before protected routing existed,
            // it already locked onto a physical/no upstream and setPreferTestNetworks()
            // above does nothing for it retroactively. Bounce it so Android re-evaluates
            // now that the test network is the preferred upstream.
            if (ShellContextCompat.isWifiApEnabled(context)) {
                TetheringApi36.stopTetheringType(context, TetheringPlatformCompat.TETHERING_WIFI)
                startWifiTethering()
            }

            unregisterCallback = TetheringApi36.registerEventCallback(context, executor) { mask ->
                activeTypesMask = mask
            }

            // Fail-closed postcondition: we cannot reliably confirm which interface
            // Android's tethering stack actually picked as upstream without OEM-fragile
            // dumpsys parsing, but we CAN confirm our own protected test network is still
            // alive before telling the user routing is active. If it died mid-setup
            // (TUN closed, framework tore it down under memory pressure, etc.) report a
            // real error instead of a false ACTIVE.
            if (!waitForTestNetworkAlive(handle, 2000L)) {
                throw IllegalStateException("test_network_died_during_setup")
            }

            currentToken = syncToken
            state.set(ROUTING_STATE_ACTIVE)
            lastDetail = ""
            0
        } catch (e: Throwable) {
            state.set(ROUTING_STATE_ERROR)
            lastDetail = e.message ?: e.javaClass.simpleName
            cleanupInternal()
            -1
        }
    }

    override fun stopRouting(): Int = synchronized(lock) {
        stopWifiTethering()
        cleanupInternal()
        state.set(ROUTING_STATE_IDLE)
        0
    }

    override fun synchronizeRouting(
        syncToken: String,
        hevConfigYaml: String,
        profileName: String,
        ipv6Enabled: Boolean,
        lease: ICoreTetheringLease?,
    ): Int = synchronized(lock) {
        if (syncToken.isBlank()) return -1
        try {
            adoptLease(lease)
            // Prefer switching the engine in place while retaining the TUN; fall back to a
            // full rebuild if the existing test network is unusable.
            val handle = testNetwork
            if (handle != null) {
                if (!waitForTestNetworkAlive(handle, 500L)) {
                    // The retained TUN is dead (e.g. system reclaimed it while the core was
                    // stopped) — rebuild from scratch instead of feeding HEV a broken fd.
                    testNetwork = null
                    return startRouting(hevConfigYaml, profileName, ipv6Enabled, syncToken, lease)
                }
                stopHevEngine()
                startHevEngine(hevConfigYaml, handle)
            } else {
                return startRouting(hevConfigYaml, profileName, ipv6Enabled, syncToken, lease)
            }
            currentToken = syncToken
            state.set(ROUTING_STATE_ACTIVE)
            lastDetail = ""
            0
        } catch (e: Throwable) {
            pendingWarning = "tethering_handover_retry"
            lastDetail = e.message ?: e.javaClass.simpleName
            state.set(ROUTING_STATE_RETRYING)
            // Fail closed: keep the (now stale) TUN alive rather than letting Android fall
            // back to a physical upstream while we retry on the next sync.
            -1
        }
    }

    override fun notifyCoreStopping(syncToken: String): Int = synchronized(lock) {
        if (syncToken != currentToken) return -1
        // Keep the protected test network alive; just mark that no HEV engine is fed until
        // the next synchronizeRouting/startRouting call arrives with a fresh snapshot.
        stopHevEngine()
        state.set(ROUTING_STATE_RETRYING)
        0
    }

    override fun notifyCoreStartFailed(syncToken: String, detail: String): Int = synchronized(lock) {
        if (syncToken != currentToken) return -1
        lastDetail = detail
        state.set(ROUTING_STATE_ERROR)
        // Deliberately do NOT tear down the test network here: Android must never be
        // allowed to select a physical upstream while the user still thinks tethering is
        // protected. Routing stays fail-closed (no HEV engine = no forwarded traffic) until
        // the app calls stopRouting() explicitly or a new snapshot arrives.
        0
    }

    override fun consumeWarning(): String {
        val w = pendingWarning
        pendingWarning = null
        return w ?: ""
    }

    override fun destroy() {
        synchronized(lock) {
            stopWifiTethering()
            cleanupInternal()
        }
    }

    // ---- internals ----

    private fun adoptLease(newLease: ICoreTetheringLease?) {
        leaseDeathRecipient?.let { recipient ->
            try {
                lease?.asBinder()?.unlinkToDeath(recipient, 0)
            } catch (_: Throwable) {
            }
        }
        lease = newLease
        if (newLease != null) {
            val recipient = IBinder.DeathRecipient {
                // The app/daemon process died. Fail closed: stop forwarding but leave the
                // test network up so nothing falls back to a physical upstream.
                synchronized(lock) {
                    stopHevEngine()
                    state.set(ROUTING_STATE_ERROR)
                    lastDetail = "app_process_died"
                }
            }
            leaseDeathRecipient = recipient
            try {
                newLease.asBinder().linkToDeath(recipient, 0)
            } catch (_: Throwable) {
            }
        }
    }

    private fun waitForTestNetworkAlive(handle: TetheringPlatformCompat.TestNetworkHandle, timeoutMillis: Long): Boolean {
        val cm = ShellContextCompat.getConnectivityManager(context) ?: return false
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            try {
                if (cm.getNetworkCapabilities(handle.network) != null) return true
            } catch (_: Throwable) {
            }
            Thread.sleep(200)
        }
        return false
    }

    private fun startWifiTethering(): Boolean {
        val tm = ShellContextCompat.getTetheringManager(context) ?: return false
        return try {
            val startTethering = tm.javaClass.methods.first { it.name == "startTethering" }
            // Best-effort: exact overload (TetheringRequest vs (int, Executor, callback))
            // varies by API level; both accept a Wi-Fi type constant on 33+.
            when (startTethering.parameterCount) {
                3 -> startTethering.invoke(tm, TetheringPlatformCompat.TETHERING_WIFI, executor, null)
                else -> startTethering.invoke(tm, TetheringPlatformCompat.TETHERING_WIFI)
            }
            true
        } catch (e: Throwable) {
            lastDetail = e.message ?: e.javaClass.simpleName
            false
        }
    }

    private fun stopWifiTethering() {
        TetheringApi36.stopTetheringType(context, TetheringPlatformCompat.TETHERING_WIFI)
    }

    private fun startHevEngine(hevConfigYaml: String, handle: TetheringPlatformCompat.TestNetworkHandle) {
        val file = File(context.filesDir, "hev-socks5-tunnel-shizuku.yaml").apply { writeText(hevConfigYaml) }
        hevConfigFile = file
        TProxyStartService(file.absolutePath, handle.tun.fd)
        hevRunning = true
    }

    private fun stopHevEngine() {
        if (!hevRunning) return
        try {
            TProxyStopService()
        } catch (_: Throwable) {
        }
        hevRunning = false
    }

    private fun cleanupInternal() {
        stopHevEngine()
        TetheringPlatformCompat.setPreferTestNetworks(context, false)
        unregisterCallback?.invoke()
        unregisterCallback = null
        testNetwork?.let { TetheringPlatformCompat.teardownTestNetwork(context, it) }
        testNetwork = null
        hevConfigFile?.delete()
        hevConfigFile = null
        currentToken = null
    }
}

/**
 * Tiny local mirror of the two [com.v2ray.ang.AppConfig] tethering-address constants.
 * The shell UserService is created from a stripped-down `UserServiceArgs` classloader
 * context in some Shizuku versions, so this avoids depending on BuildConfig-backed
 * constants inside `AppConfig` initializing awkwardly outside the normal app process.
 */
private object AppConfigCompat {
    const val SHIZUKU_TUN_ADDR_V4 = "192.0.2.2/24"
    const val SHIZUKU_TUN_ADDR_V6 = "2001:db8:9877::1/64"
}
