package com.v2ray.ang.shizuku

import android.content.Context
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

/**
 * API 36+ path: `TetheringManager.TetheringEventCallback` reports active downstreams as
 * typed `TetheringInterface` objects, and `stopTethering(TetheringRequest, Executor,
 * StopTetheringCallback)` confirms completion instead of firing-and-forgetting.
 *
 * Both interfaces are hidden (`@SystemApi`), so they're implemented here via a dynamic
 * [Proxy] rather than compiled against directly.
 */
internal object TetheringApi36 {

    fun interface ActiveTypesListener {
        fun onActiveTypesChanged(mask: Int)
    }

    /** Registers a listener for active tethering-type changes; returns an unregister callback. */
    fun registerEventCallback(context: Context, executor: Executor, listener: ActiveTypesListener): (() -> Unit)? {
        val tm = ShellContextCompat.getTetheringManager(context) ?: return null
        return try {
            val callbackInterface = Class.forName("android.net.TetheringManager\$TetheringEventCallback")
            val proxy = Proxy.newProxyInstance(
                callbackInterface.classLoader,
                arrayOf(callbackInterface),
                CallbackHandler(listener),
            )
            val register = tm.javaClass.methods.first {
                it.name == "registerTetheringEventCallback" && it.parameterCount == 2
            }
            register.invoke(tm, executor, proxy)

            val unregisterFn: () -> Unit = {
                try {
                    val unregister = tm.javaClass.getMethod("unregisterTetheringEventCallback", callbackInterface)
                    unregister.invoke(tm, proxy)
                } catch (_: Throwable) {
                }
            }
            unregisterFn
        } catch (_: Throwable) {
            null
        }
    }

    private class CallbackHandler(private val listener: ActiveTypesListener) : InvocationHandler {
        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
            if (method.name == "onTetheredInterfacesChanged" || method.name == "onTetheringSupportedChanged") {
                notifyMask(args)
            }
            return null
        }

        private fun notifyMask(args: Array<out Any?>?) {
            val arg = args?.getOrNull(0) ?: return
            var mask = 0
            val elements: Iterable<*> = when (arg) {
                is Set<*> -> arg
                is Collection<*> -> arg
                is Array<*> -> arg.toList()
                else -> return
            }
            for (element in elements) {
                val element0 = element ?: continue
                val ifaceName = try {
                    element0.javaClass.getMethod("getInterface").invoke(element0) as? String
                } catch (_: Throwable) {
                    null
                } ?: continue
                mask = mask or TetheringPlatformCompat.classifyLegacyInterface(ifaceName)
            }
            listener.onActiveTypesChanged(mask)
        }
    }

    fun stopTetheringType(context: Context, type: Int): Boolean {
        val tm = ShellContextCompat.getTetheringManager(context) ?: return false
        return try {
            val method = tm.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
            method.invoke(tm, type)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
