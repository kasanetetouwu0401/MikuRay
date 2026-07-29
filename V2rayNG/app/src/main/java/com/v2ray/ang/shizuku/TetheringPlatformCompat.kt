package com.v2ray.ang.shizuku

import android.content.Context
import android.net.LinkAddress
import android.net.Network
import android.os.Build
import android.os.ParcelFileDescriptor
import java.lang.reflect.Method
import java.net.InetAddress

/**
 * Reflection bridge to Android's hidden `TestNetworkManager` / `TetheringManager` APIs.
 *
 * These are `@SystemApi` classes: present on the platform but not in the public SDK, so
 * they must be reached through reflection even though the calling code runs with shell
 * UID (via Shizuku) and therefore has the underlying permissions
 * (`MANAGE_TEST_NETWORKS`, `NETWORK_SETTINGS`).
 *
 * OEMs are free to rename or remove hidden methods. Every call here is defensive: a
 * missing/renamed method surfaces as a caught [ReflectiveOperationException] so
 * [ShizukuTetheringService] can fail closed instead of crashing the shell process.
 */
internal object TetheringPlatformCompat {

    class TestNetworkHandle(
        val tun: ParcelFileDescriptor,
        val interfaceName: String,
        val network: Network,
        internal val testNetworkManager: Any,
    )

    /** Requires `MANAGE_TEST_NETWORKS` (shell UID has it). Blocks briefly for network setup. */
    fun createTestNetwork(
        context: Context,
        ipv4Address: String,
        ipv6Address: String?,
        connectivityWaitMillis: Long = 3000L,
    ): TestNetworkHandle {
        val tnm = ShellContextCompat.getTestNetworkManager(context)
            ?: error("TestNetworkManager unavailable")

        val addresses = mutableListOf(linkAddressOf(ipv4Address))
        if (ipv6Address != null) addresses += linkAddressOf(ipv6Address)

        val createTun = findMethod(
            tnm.javaClass,
            "createTunInterface",
            List::class.java,
        )
        @Suppress("UNCHECKED_CAST")
        val tunInterface = createTun.invoke(tnm, addresses)
            ?: error("createTunInterface returned null")

        val ifaceNameField = tunInterface.javaClass.getDeclaredField("interfaceName").apply { isAccessible = true }
        val fdField = tunInterface.javaClass.getDeclaredField("fileDescriptor").apply { isAccessible = true }
        val interfaceName = ifaceNameField.get(tunInterface) as String
        val pfd = fdField.get(tunInterface) as ParcelFileDescriptor

        val setupTestNetwork = findMethod(
            tnm.javaClass,
            "setupTestNetwork",
            String::class.java,
            List::class.java,
            Boolean::class.javaPrimitiveType!!,
            android.os.IBinder::class.java,
        )
        val binderToken = android.os.Binder()
        setupTestNetwork.invoke(tnm, interfaceName, emptyList<InetAddress>(), true, binderToken)

        val network = waitForTestNetwork(context, interfaceName, connectivityWaitMillis)
            ?: error("Test network for $interfaceName did not come up in time")

        return TestNetworkHandle(pfd, interfaceName, network, tnm)
    }

    fun teardownTestNetwork(context: Context, handle: TetheringPlatformCompat.TestNetworkHandle) {
        try {
            val teardown = findMethod(handle.testNetworkManager.javaClass, "teardownTestNetwork", Network::class.java)
            teardown.invoke(handle.testNetworkManager, handle.network)
        } catch (_: Throwable) {
            // best-effort; the TUN close below still removes the interface
        }
        try {
            handle.tun.close()
        } catch (_: Throwable) {
        }
    }

    fun setPreferTestNetworks(context: Context, prefer: Boolean) {
        val tm = ShellContextCompat.getTetheringManager(context) ?: return
        try {
            val method = findMethod(tm.javaClass, "setPreferTestNetworks", Boolean::class.javaPrimitiveType!!)
            method.invoke(tm, prefer)
        } catch (_: Throwable) {
            // Not available on this OEM build; tethering may still pick testtun on its own
            // once it is the only viable upstream, but this is best-effort.
        }
    }

    private fun waitForTestNetwork(context: Context, interfaceName: String, timeoutMillis: Long): Network? {
        val cm = ShellContextCompat.getConnectivityManager(context) ?: return null
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val networks = cm.allNetworks
            for (n in networks) {
                val lp = cm.getLinkProperties(n) ?: continue
                if (lp.interfaceName == interfaceName) return n
            }
            Thread.sleep(100)
        }
        return null
    }

    private fun linkAddressOf(cidr: String): LinkAddress {
        return LinkAddress(cidr)
    }

    private fun findMethod(clazz: Class<*>, name: String, vararg params: Class<*>): Method {
        return clazz.methods.firstOrNull { it.name == name && it.parameterCount == params.size }
            ?.apply { isAccessible = true }
            ?: clazz.getMethod(name, *params).apply { isAccessible = true }
    }

    // ---- Legacy (Android 11-15) tethering type/state helpers ----
    // API 36 exposes typed TetheringInterface + TetheringEventCallback; below that we only
    // get raw interface name strings from dumpsys/callbacks, so downstream types are
    // classified by their conventional Android interface-name prefixes.

    const val TETHERING_WIFI = 1 shl 0
    const val TETHERING_USB = 1 shl 1
    const val TETHERING_UNKNOWN = 1 shl 30

    fun classifyLegacyInterface(name: String): Int = when {
        name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan") -> TETHERING_WIFI
        name.startsWith("rndis") || name.startsWith("ncm") || name.startsWith("usb") -> TETHERING_USB
        else -> TETHERING_UNKNOWN
    }

    val supportsApi36Tethering: Boolean get() = Build.VERSION.SDK_INT >= 36
    val supportsShizukuTethering: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU // API 33+
}
