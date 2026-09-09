package com.miku.ray.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.miku.ray.AngApplication
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.dto.RealPingResult
import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.dto.entities.ServerAffiliationInfo
import com.miku.ray.dto.entities.SubscriptionCache
import com.miku.ray.dto.entities.SubscriptionItem
import com.miku.ray.extension.serializable
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.handler.SubscriptionUpdater
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MessageUtil
import com.miku.ray.util.Utils
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

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val safeIntent = intent ?: return
            val event = when (safeIntent.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> MainServiceEvent.StateRunning
                AppConfig.MSG_STATE_NOT_RUNNING -> MainServiceEvent.StateNotRunning
                AppConfig.MSG_STATE_START_SUCCESS -> MainServiceEvent.StateStartSuccess
                AppConfig.MSG_STATE_START_FAILURE -> MainServiceEvent.StateStartFailure
                AppConfig.MSG_STATE_STOP_SUCCESS -> MainServiceEvent.StateStopSuccess
                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> safeIntent
                    .serializable<RealPingResult>("content")
                    ?.let(MainServiceEvent::MeasureDelayResult)
                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> MainServiceEvent.MeasureConfigSuccess
                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> MainServiceEvent.MeasureConfigNotify(
                    safeIntent.getStringExtra("content").orEmpty(),
                )
                AppConfig.MSG_MEASURE_CONFIG_FINISH -> MainServiceEvent.MeasureConfigFinish(
                    safeIntent.getStringExtra("content"),
                )
                else -> null
            }
            event?.let { _mainServiceEvent.tryEmit(it) }
        }
    }

    init {
        ContextCompat.registerReceiver(
            app,
            serviceReceiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags(),
        )
        MessageUtil.sendMsg2Service(app, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { MessageUtil.sendMsg2Service(app, AppConfig.MSG_UNREGISTER_CLIENT, "") }
            .onFailure { LogUtil.e(AppConfig.TAG, "Failed to unregister service client", it) }
        runCatching { app.unregisterReceiver(serviceReceiver) }
            .onFailure { LogUtil.e(AppConfig.TAG, "Failed to unregister main service receiver", it) }
    }

    override fun getSelectedSubscriptionId(): String =
        MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()

    override fun setSelectedSubscriptionId(id: String) =
        MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, id)

    override fun getSelectServer(): String? = MmkvManager.getSelectServer()
    override fun setSelectServer(guid: String) = MmkvManager.setSelectServer(guid)
    override fun getConfirmRemove(): Boolean = MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)
    override fun getDoubleColumnDisplay(): Boolean = MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)
    override fun isGroupAllDisplayEnabled(): Boolean = MmkvManager.decodeSettingsBool(AppConfig.PREF_GROUP_ALL_DISPLAY)
    override fun getString(resId: Int): String = app.getString(resId)
    override fun getString(resId: Int, vararg formatArgs: Any): String = app.getString(resId, *formatArgs)

    override fun getSubscriptions(): List<SubscriptionCache> = buildList {
        if (isGroupAllDisplayEnabled()) {
            add(SubscriptionCache("", SubscriptionItem(remarks = app.getString(R.string.filter_config_all))))
        }
        addAll(MmkvManager.decodeSubscriptions())
    }

    override fun getSubscriptionItem(id: String): SubscriptionItem? = MmkvManager.decodeSubscription(id)
    override fun getServerGuidList(groupId: String): List<String> =
        if (groupId.isEmpty()) MmkvManager.decodeAllServerList() else MmkvManager.decodeServerList(groupId)
    override fun decodeServerConfig(guid: String): ProfileItem? = MmkvManager.decodeServerConfig(guid)
    override fun decodeAffiliationInfo(guid: String): ServerAffiliationInfo? = MmkvManager.decodeServerAffiliationInfo(guid)
    override fun encodeServerList(guids: List<String>, groupId: String) = MmkvManager.encodeServerList(ArrayList(guids), groupId)
    override fun removeServer(guid: String) = MmkvManager.removeServer(guid)
    override fun removeAllServer(): Int = MmkvManager.removeAllServer()
    override fun removeInvalidServerByGuid(guid: String): Int = MmkvManager.removeInvalidServer(guid)
    override fun removeInvalidServersInGroup(groupId: String): Int =
        if (groupId.isEmpty()) MmkvManager.removeInvalidServer("")
        else getServerGuidList(groupId).sumOf(::removeInvalidServerByGuid)
    override fun clearAllTestDelayResults(guids: List<String>) = MmkvManager.clearAllTestDelayResults(guids)
    override fun sortByTestResultsForSub(subId: String) = AngConfigManager.sortByTestResultsForSub(subId)
    override fun getSubsList(): List<String> = MmkvManager.decodeSubsList()
    override suspend fun importBatchConfig(server: String?, subscriptionId: String, updateUI: Boolean): Pair<Int, Int> =
        AngConfigManager.importBatchConfig(server, subscriptionId, updateUI)
    override fun updateConfigViaSubAll(): SubscriptionUpdateResult = AngConfigManager.updateConfigViaSubAll()
    override fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult =
        AngConfigManager.updateConfigViaSub(subscriptionCache)
    override fun shareNonCustomConfigsToClipboard(guids: List<String>): Int =
        AngConfigManager.shareNonCustomConfigsToClipboard(app, guids)
    override fun share2QRCode(guid: String) = AngConfigManager.share2QRCode(guid)
    override fun share2Clipboard(guid: String): Boolean = AngConfigManager.share2Clipboard(app, guid) == 0
    override fun sendMsg2Service(msgId: Int, content: String) = MessageUtil.sendMsg2Service(app, msgId, content)
    override fun sendMsg2TestService(msg: TestServiceMessage) = MessageUtil.sendMsg2TestService(app, msg)
    override fun cancelAllPing() = sendMsg2TestService(TestServiceMessage(AppConfig.MSG_MEASURE_CONFIG_CANCEL))
    override fun testCurrentServerRealPing() = sendMsg2Service(AppConfig.MSG_MEASURE_DELAY, "")
    override fun syncSubscriptions() = SubscriptionUpdater.sync(app)
    override fun initAssets() = SettingsManager.initAssets(app, app.assets)
}
