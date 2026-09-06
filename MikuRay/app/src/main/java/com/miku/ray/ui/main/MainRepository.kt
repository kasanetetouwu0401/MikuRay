package com.miku.ray.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.miku.ray.AngApplication
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.GroupMapItem
import com.miku.ray.dto.RealPingProgress
import com.miku.ray.dto.RealPingResult
import com.miku.ray.dto.RealPingSummary
import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.TestProgressInfo
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.dto.entities.ServerAffiliationInfo
import com.miku.ray.dto.entities.SubscriptionCache
import com.miku.ray.dto.entities.SubscriptionItem
import com.miku.ray.extension.serializable
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MessageUtil
import com.miku.ray.util.Utils
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

class MainRepository(
    private val app: AngApplication
) : MainDataSource {

    private val closed = AtomicBoolean(false)

    private val _mainServiceEvent = MutableSharedFlow<MainServiceEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val mainServiceEvent: SharedFlow<MainServiceEvent> = _mainServiceEvent.asSharedFlow()

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val safeIntent = intent ?: return
            val event: MainServiceEvent? = when (safeIntent.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> MainServiceEvent.StateRunning
                AppConfig.MSG_STATE_NOT_RUNNING -> MainServiceEvent.StateNotRunning
                AppConfig.MSG_STATE_RESTART -> MainServiceEvent.StateRestart

                AppConfig.MSG_STATE_START_SUCCESS -> MainServiceEvent.StateStartSuccess(
                    restarted = safeIntent.serializable<Boolean>("content") == true
                )

                AppConfig.MSG_STATE_START_FAILURE -> MainServiceEvent.StateStartFailure(
                    safeIntent.getStringExtra("content")
                )

                AppConfig.MSG_STATE_STOP_SUCCESS -> MainServiceEvent.StateStopSuccess

                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> MainServiceEvent.MeasureDelaySuccess(
                    safeIntent.getStringExtra("content").orEmpty()
                )

                AppConfig.MSG_MEASURE_IP_SUCCESS -> MainServiceEvent.MeasureIpSuccess(
                    safeIntent.getStringExtra("content")
                )

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                    val result = safeIntent.serializable<RealPingResult>("content")
                    MainServiceEvent.MeasureConfigResult(
                        result = result,
                        legacyGuid = if (result == null) safeIntent.getStringExtra("content") else null
                    )
                }

                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> {
                    val progress = safeIntent.serializable<RealPingProgress>("content")
                    MainServiceEvent.MeasureConfigNotify(
                        progress = progress,
                        legacy = if (progress == null) safeIntent.serializable<TestProgressInfo>("content") else null
                    )
                }

                AppConfig.MSG_MEASURE_CONFIG_FINISH -> MainServiceEvent.MeasureConfigFinish(
                    safeIntent.serializable<RealPingSummary>("content")
                )

                AppConfig.MSG_COUNTRY_CODE_SUCCESS -> MainServiceEvent.CountryCodeSuccess(
                    safeIntent.getStringExtra("content").orEmpty()
                )

                AppConfig.MSG_COUNTRY_CODE_NOTIFY -> MainServiceEvent.CountryCodeNotify(
                    safeIntent.serializable<TestProgressInfo>("content")
                )

                AppConfig.MSG_COUNTRY_CODE_FINISH -> MainServiceEvent.CountryCodeFinish

                AppConfig.MSG_TRAFFIC_UPDATED -> safeIntent.getStringExtra("content")
                    ?.let { MainServiceEvent.TrafficUpdated(it) }

                AppConfig.MSG_TRAFFIC_SPEED_UPDATED -> safeIntent.getStringExtra("content")
                    ?.let { MainServiceEvent.TrafficSpeedUpdated(it) }

                AppConfig.MSG_SUB_UPDATE_FINISH -> MainServiceEvent.SubUpdateFinish

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
            Utils.receiverFlags()
        )
    }

    override fun registerClient() {
        MessageUtil.sendMsg2Service(app, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching {
            app.unregisterReceiver(serviceReceiver)
        }.onFailure {
            LogUtil.e(AppConfig.TAG, "Failed to unregister main service receiver", it)
        }
    }

    override fun getSelectedSubscriptionId(): String =
        MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()

    override fun setSelectedSubscriptionId(id: String) {
        MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, id)
    }

    override fun getSelectServer(): String? = MmkvManager.getSelectServer()

    override fun setSelectServer(guid: String) = MmkvManager.setSelectServer(guid)

    override fun getString(resId: Int): String = app.getString(resId)

    override fun getString(resId: Int, vararg formatArgs: Any): String =
        app.getString(resId, *formatArgs)

    override fun isGroupAllDisplayEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_GROUP_ALL_DISPLAY)

    override fun getGroups(): List<GroupMapItem> {
        val groups = mutableListOf<GroupMapItem>()
        if (isGroupAllDisplayEnabled()) {
            groups += GroupMapItem(
                id = "",
                remarks = app.getString(R.string.filter_config_all),
                serverCount = MmkvManager.decodeAllServerList().size,
                icon = MmkvManager.decodeSettingsString(AppConfig.PREF_GROUP_ALL_TAB_ICON),
            )
        }
        MmkvManager.decodeSubscriptions().forEach { sub ->
            groups += GroupMapItem(
                id = sub.guid,
                remarks = sub.subscription.remarks,
                serverCount = MmkvManager.decodeServerList(sub.guid).size,
                icon = sub.subscription.tabIcon,
            )
        }
        return groups
    }

    override fun getSubscriptions(): List<SubscriptionCache> = MmkvManager.decodeSubscriptions()

    override fun getSubscriptionItem(id: String): SubscriptionItem? = MmkvManager.decodeSubscription(id)

    override fun getSubsList(): List<String> = MmkvManager.decodeSubsList()

    override fun getServerGuidList(groupId: String): List<String> =
        if (groupId.isEmpty()) MmkvManager.decodeAllServerList() else MmkvManager.decodeServerList(groupId)

    override fun decodeServerConfig(guid: String): ProfileItem? = MmkvManager.decodeServerConfig(guid)

    override fun decodeAffiliationInfo(guid: String): ServerAffiliationInfo? =
        MmkvManager.decodeServerAffiliationInfo(guid)

    override fun decodePinnedServers(): List<String> = MmkvManager.decodePinnedServers().toList()

    override fun togglePinnedServer(guid: String): Boolean = MmkvManager.togglePinnedServer(guid)

    override fun getSortOrder(groupId: String): Int {
        val subId = groupId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
        return MmkvManager.decodeSettingsInt("${AppConfig.PREF_SERVER_ORDER}_$subId", 0)
    }

    override fun restoreOriginServerListIfNeeded(groupId: String) {
        if (getSortOrder(groupId) != 0) return
        if (groupId.isEmpty()) {
            MmkvManager.decodeSubsList().forEach { MmkvManager.restoreOriginServerList(it) }
        } else {
            MmkvManager.restoreOriginServerList(groupId)
        }
    }

    override fun encodeServerList(guids: List<String>, groupId: String) =
        MmkvManager.encodeServerList(guids.toMutableList(), groupId)

    override fun removeServer(guid: String) = MmkvManager.removeServer(guid)

    override fun removeAllServer(): Int = MmkvManager.removeAllServer()

    override fun removeInvalidServerByGuid(guid: String): Int = MmkvManager.removeInvalidServer(guid)

    override fun swapServer(fromGuid: String, toGuid: String, groupId: String, order: List<String>) {
        encodeServerList(order, groupId)
    }

    override fun clearAllTestDelayResults(guids: List<String>) = MmkvManager.clearAllTestDelayResults(guids)

    override fun clearAllCountryCodes(guids: List<String>) = MmkvManager.clearAllCountryCodes(guids)

    override fun sortByTestResultsForSub(subId: String) = AngConfigManager.sortByTestResultsForSub(subId)

    override fun prepareGroupForAutoSortByDelay(subId: String) {
        MmkvManager.saveOriginServerList(subId)
        val key = subId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
        MmkvManager.encodeSettings("${AppConfig.PREF_SERVER_ORDER}_$key", 2)
    }

    override fun resetProfileTraffic(guid: String) = MmkvManager.resetProfileTraffic(guid)

    override fun resetGroupTraffic(groupId: String) = MmkvManager.resetGroupTraffic(groupId)

    override fun resetAllTraffic() = MmkvManager.resetAllTraffic()

    override fun markConnectionStopped() {
        MmkvManager.encodeSettings(AppConfig.PREF_VPN_CONNECT_START_TIME, 0L)
    }

    override fun isAutoRemoveInvalidAfterTest(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST)

    override fun isAutoSortAfterTest(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST)

    override fun updateConfigViaSubAll(): SubscriptionUpdateResult = AngConfigManager.updateConfigViaSubAll()

    override fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult =
        AngConfigManager.updateConfigViaSub(subscriptionCache)

    override fun shareNonCustomConfigsToClipboard(guids: List<String>): Int =
        AngConfigManager.shareNonCustomConfigsToClipboard(app, guids)

    override fun sendMsg2Service(msgId: Int, content: String) = MessageUtil.sendMsg2Service(app, msgId, content)

    override fun sendMsg2TestService(msg: TestServiceMessage) = MessageUtil.sendMsg2TestService(app, msg)

    override fun sendMsg2CountryCodeTestService(msg: CountryCodeTestMessage) =
        MessageUtil.sendMsg2CountryCodeTestService(app, msg)

    override fun initAssets() {
        SettingsManager.initAssets(app, app.assets)
    }
}
