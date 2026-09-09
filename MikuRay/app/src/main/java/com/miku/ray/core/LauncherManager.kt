package com.miku.ray.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.aidl.ICoreService
import com.miku.ray.extension.isComplexType
import com.miku.ray.extension.snackbarDefault
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.toastError
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.root.RootManager
import com.miku.ray.service.CoreProxyOnlyService
import com.miku.ray.service.CoreRootService
import com.miku.ray.service.CoreVpnService
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils
import java.util.concurrent.atomic.AtomicBoolean

object LauncherManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private const val COMMAND_TIMEOUT_MS = 3_000L

    private fun showFeedback(context: Context, message: String, type: Int = 0) {
        if (context is Activity) {
            when (type) {
                1 -> context.snackbarDefault(
                    message,
                    title = context.getString(R.string.title_alerter_success)
                )
                2 -> context.snackbarError(
                    message,
                    title = context.getString(R.string.title_alerter_error)
                )
                else -> context.snackbarDefault(
                    message,
                    title = context.getString(R.string.title_alerter_info)
                )
            }
        } else {
            context.snackbarDefault(message, title = context.getString(R.string.title_alerter_info))
        }
    }

    fun startServiceFromToggle(context: Context): Boolean =
    requestServiceStart(context, guid = null, showLifecycleFeedback = true)

    fun startService(context: Context, guid: String? = null) {
        requestServiceStart(context, guid, showLifecycleFeedback = true)
    }

    internal fun startServiceAfterRestart(context: Context): Boolean =
    requestServiceStart(context, guid = null, showLifecycleFeedback = false)

    private fun requestServiceStart(
        context: Context,
        guid: String?,
        showLifecycleFeedback: Boolean,
    ): Boolean {
        LogUtil.i(AppConfig.TAG, "LauncherManager: startService from ${context::class.java.simpleName}")

        if (guid != null) {
            MmkvManager.setSelectServer(guid)
        }

        try {
            startContextService(context, showLifecycleFeedback)
            return true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: ${e.message}", e)
            val message = e.message ?: e.javaClass.simpleName
            if (showLifecycleFeedback) {
                showFeedback(context, message, 2)
            }
            return false
        }
    }

    /**
     * Stops the running core service over its AIDL binder. A no-op when the
     * core is not running.
     */
    fun stopService(context: Context) {
        val appContext = context.applicationContext
        val id = BinderServiceFactory.CONNECTION_ID_STOP
        val done = AtomicBoolean(false)
        fun finish() {
            if (done.compareAndSet(false, true)) {
                mainHandler.post {
                    BinderServiceFactory.disconnect(appContext, id)
                }
            }
        }

        BinderServiceFactory.connect(appContext, id, object : BinderServiceFactory.Callback {
            override fun onServiceConnected(service: ICoreService) {
                try {
                    if (service.state == AppConfig.MSG_STATE_RUNNING) {
                        service.stopCore()
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "LauncherManager: Failed to stop core service", e)
                }
                finish()
            }

            override fun onServiceDisconnected() {
                // Core service went away on its own; nothing left to stop.
                finish()
            }
        })
        mainHandler.postDelayed({ finish() }, COMMAND_TIMEOUT_MS)
    }

    fun restartService(context: Context) {
        restartService(context) { }
    }

    /**
     * Asks the running core service (via AIDL) to restart itself. The result is
     * `true` when a running service accepted the request; `false` means nothing
     * is running (or the request failed) and the caller may start it instead.
     */
    fun restartService(context: Context, onResult: (handled: Boolean) -> Unit) {
        val appContext = context.applicationContext
        val id = BinderServiceFactory.CONNECTION_ID_RESTART
        val done = AtomicBoolean(false)
        fun deliver(handled: Boolean) {
            if (done.compareAndSet(false, true)) {
                mainHandler.post {
                    onResult(handled)
                    BinderServiceFactory.disconnect(appContext, id)
                }
            }
        }

        BinderServiceFactory.connect(appContext, id, object : BinderServiceFactory.Callback {
            override fun onServiceConnected(service: ICoreService) {
                val handled = try {
                    service.requestRestart()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "LauncherManager: Failed to request restart", e)
                    false
                }
                deliver(handled)
            }
        })
        mainHandler.postDelayed({ deliver(false) }, COMMAND_TIMEOUT_MS)
    }

    fun restartServiceOrStart(context: Context, startIfStopped: () -> Unit) {
        restartService(context) { handled ->
            if (!handled) startIfStopped()
        }
    }

    @Throws(Exception::class)
    private fun startContextService(context: Context, showLifecycleFeedback: Boolean) {
        val guid = MmkvManager.getSelectServer()
        ?: run {
            LogUtil.e(AppConfig.TAG, "LauncherManager: No server selected")
            error(context.getString(R.string.app_tile_first_use))
        }

        val config = MmkvManager.decodeServerConfig(guid)
        ?: run {
            LogUtil.e(AppConfig.TAG, "LauncherManager: Failed to decode server config")
            error(context.getString(R.string.toast_config_file_invalid))
        }

        if (!config.configType.isComplexType()
            && !Utils.isValidUrl(config.server)
            && !Utils.isPureIpAddress(config.server.orEmpty())
        ) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: Invalid server configuration")
            error(context.getString(R.string.toast_config_file_invalid))
        }

        SettingsManager.refreshRuntimeSocksPort()

        if (config.insecure == true && config.pinnedCA256.isNullOrEmpty()) {
            context.toastError(context.getString(R.string.toast_allow_insecure_deprecated))
            Utils.setClipboard(context, context.getString(R.string.toast_allow_insecure_deprecated))
        }

        if (showLifecycleFeedback && MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)) {
            showFeedback(context, context.getString(R.string.toast_warning_pref_proxysharing_short), 0)
        }

        val isRootMode = SettingsManager.isRootMode()
        if (isRootMode && !RootManager.isRootAvailable()) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: root mode requires root but none available")
            error(context.getString(R.string.toast_root_required))
        }

        val intent = if (isRootMode) {
            LogUtil.i(AppConfig.TAG, "LauncherManager: Starting Root service")
            Intent(context.applicationContext, CoreRootService::class.java)
        } else if (SettingsManager.isVpnMode()) {
            LogUtil.i(AppConfig.TAG, "LauncherManager: Starting VPN service")
            Intent(context.applicationContext, CoreVpnService::class.java)
        } else {
            LogUtil.i(AppConfig.TAG, "LauncherManager: Starting Proxy service")
            Intent(context.applicationContext, CoreProxyOnlyService::class.java)
        }

        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: SecurityException) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: Missing permission to start foreground service", e)
            throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
        } catch (e: RuntimeException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException"
            ) {
                LogUtil.e(AppConfig.TAG, "LauncherManager: Foreground service start not allowed", e)
                throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
            }
            throw e
        }
    }
}
