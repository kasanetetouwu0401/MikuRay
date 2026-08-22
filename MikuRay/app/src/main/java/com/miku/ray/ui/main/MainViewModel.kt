package com.miku.ray.ui.main

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.miku.ray.AngApplication
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.GroupMapItem
import com.miku.ray.dto.entities.ServersCache
import com.miku.ray.dto.entities.SubscriptionCache
import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.TestProgressInfo
import com.miku.ray.ui.bottomsheet.SortSubBottomSheet
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.extension.isComplexType
import com.miku.ray.extension.matchesPattern
import com.miku.ray.extension.serializable
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MessageUtil
import com.miku.ray.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.regex.PatternSyntaxException

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var serverList = mutableListOf<String>()
    @Volatile
    var subscriptionId: String = MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()
    @Volatile
    var keywordFilter = ""
    val serversCache = mutableListOf<ServersCache>()

    private val reloadLock = Any()
    private var reloadGeneration = 0L
    private var reloadJob: Job? = null

    val isRunning by lazy { MutableLiveData<Boolean>() }
    val updateListAction by lazy { MutableLiveData<Int>() }
    val updateTestResultAction by lazy { MutableLiveData<String>() }
    val testProgressAction by lazy { MutableLiveData<TestProgressInfo?>() }
    val countryCodeProgressAction by lazy { MutableLiveData<TestProgressInfo?>() }
    val updateIpResultAction by lazy { MutableLiveData<String>() }
    val updateTrafficSpeedAction by lazy { MutableLiveData<String>() }
    val alertAction by lazy { MutableLiveData<Pair<Boolean, String>>() }
    val updateGroupBadgeAction by lazy { MutableLiveData<Unit>() }
    val updateGroupOrderAction by lazy { MutableLiveData<Unit>() }

    fun startListenBroadcast() {
        isRunning.value = false
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
        ContextCompat.registerReceiver(getApplication(), mMsgReceiver, mFilter, Utils.receiverFlags())
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    fun resyncState() {
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onCleared() {
        try {
            getApplication<AngApplication>().unregisterReceiver(mMsgReceiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
        LogUtil.i(AppConfig.TAG, "Main ViewModel is cleared")
        super.onCleared()
    }

    fun reloadServerList() {
        scheduleReload()
    }

    private fun cancelPendingReload() {
        synchronized(reloadLock) {
            reloadGeneration++
            reloadJob?.cancel()
            reloadJob = null
        }
    }

    private fun scheduleReload(delayMillis: Long = 0L) {
        val requestedSubscriptionId = subscriptionId
        val requestedKeyword = keywordFilter
        val generation: Long
        synchronized(reloadLock) {
            generation = ++reloadGeneration
            reloadJob?.cancel()
            reloadJob = viewModelScope.launch(Dispatchers.IO) {
                if (delayMillis > 0L) delay(delayMillis)

                val subId = requestedSubscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
                val order = MmkvManager.decodeSettingsInt("${AppConfig.PREF_SERVER_ORDER}_$subId", 0)
                if (order == 0) {
                    if (requestedSubscriptionId.isEmpty()) {
                        MmkvManager.decodeSubsList().forEach { MmkvManager.restoreOriginServerList(it) }
                    } else {
                        MmkvManager.restoreOriginServerList(requestedSubscriptionId)
                    }
                }

                val nextServerList = if (requestedSubscriptionId.isEmpty()) {
                    MmkvManager.decodeAllServerList()
                } else {
                    MmkvManager.decodeServerList(requestedSubscriptionId)
                }
                val nextCache = buildServerCache(nextServerList, requestedKeyword, order)

                withContext(Dispatchers.Main.immediate) {
                    val isCurrent = synchronized(reloadLock) {
                        generation == reloadGeneration &&
                            requestedSubscriptionId == subscriptionId &&
                            requestedKeyword == keywordFilter
                    }
                    if (!isCurrent) return@withContext

                    serverList = nextServerList.toMutableList()
                    serversCache.clear()
                    serversCache.addAll(nextCache)
                    updateListAction.value = -1
                }
            }
        }
    }

    fun removeServer(guid: String) {
        cancelPendingReload()
        serverList.remove(guid)
        MmkvManager.removeServer(guid)
        val index = getPosition(guid)
        if (index >= 0) {
            serversCache.removeAt(index)
        }
        updateGroupBadgeAction.postValue(Unit)
    }

    fun swapServer(fromPosition: Int, toPosition: Int) {
        if (subscriptionId.isEmpty()) {
            return
        }

        cancelPendingReload()
        Collections.swap(serverList, fromPosition, toPosition)
        Collections.swap(serversCache, fromPosition, toPosition)

        MmkvManager.encodeServerList(serverList, subscriptionId)
    }

    fun updateCache() {
        scheduleReload()
    }

    private fun buildServerCache(
        sourceServerList: List<String>,
        keyword: String,
        order: Int,
    ): List<ServersCache> {
        val kw = keyword.trim()
        val searchRegex = try {
            if (kw.isNotEmpty()) Regex(kw, setOf(RegexOption.IGNORE_CASE)) else null
        } catch (e: PatternSyntaxException) {
            null
        }
        val pinnedServers = MmkvManager.decodePinnedServers()
        val trafficEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_TRAFFIC_ENABLED) == true
        val subscriptionRemarks = MmkvManager.decodeSubscriptions()
            .associate { it.guid to it.subscription.remarks }
        val result = ArrayList<ServersCache>(sourceServerList.size)

        sourceServerList.forEach { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@forEach
            val matches = kw.isEmpty() ||
                profile.remarks.matchesPattern(searchRegex, kw) ||
                profile.description.orEmpty().matchesPattern(searchRegex, kw) ||
                profile.server.orEmpty().matchesPattern(searchRegex, kw) ||
                profile.configType.name.matchesPattern(searchRegex, kw)
            if (!matches) return@forEach

            val affiliation = MmkvManager.decodeServerAffiliationInfo(guid)
            val traffic = if (trafficEnabled && affiliation != null &&
                (affiliation.uplinkTotal != 0L || affiliation.downlinkTotal != 0L)
            ) {
                "↑ ${MmkvManager.formatTrafficBytesPublic(affiliation.uplinkTotal)}  ↓ " +
                    MmkvManager.formatTrafficBytesPublic(affiliation.downlinkTotal)
            } else {
                null
            }
            val remarks = when {
                profile.configType == com.miku.ray.enums.EConfigType.POLICYGROUP ->
                    profile.policyGroupSubscriptionId?.let { subscriptionRemarks[it] }
                else -> subscriptionRemarks[profile.subscriptionId]
            }
            result.add(
                ServersCache(
                    guid = guid,
                    profile = profile,
                    affiliation = affiliation,
                    traffic = traffic,
                    isPinned = pinnedServers.contains(guid),
                    subscriptionRemarks = remarks,
                )
            )
        }

        when (order) {
            1 -> result.sortWith(compareBy { it.profile.remarks.lowercase() })
            2 -> result.sortWith(compareBy {
                val delay = it.affiliation?.testDelayMillis ?: 0L
                if (delay <= 0L) Long.MAX_VALUE else delay
            })
        }
        if (pinnedServers.isNotEmpty()) {
            result.sortByDescending { it.isPinned }
        }
        return result
    }

    /**
     * Toggles the pinned state of [guid], re-sorts the cache so pinned
     * servers float to the top immediately, and returns the new state.
     */
    fun togglePinServer(guid: String): Boolean {
        val nowPinned = MmkvManager.togglePinnedServer(guid)
        reloadServerList()
        return nowPinned
    }

    fun updateConfigViaSubAll(): SubscriptionUpdateResult {
        if (subscriptionId.isEmpty()) {
            return AngConfigManager.updateConfigViaSubAll()
        } else {
            val subItem = MmkvManager.decodeSubscription(subscriptionId) ?: return SubscriptionUpdateResult()
            return AngConfigManager.updateConfigViaSub(SubscriptionCache(subscriptionId, subItem))
        }
    }

    fun exportAllServer(): Int {
        val serverListCopy =
            if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
                serverList
            } else {
                serversCache.map { it.guid }.toList()
            }

        val ret = AngConfigManager.shareNonCustomConfigsToClipboard(
            getApplication<AngApplication>(),
            serverListCopy
        )
        return ret
    }

    fun testAllRealPing(onlyTcp: Boolean = false) {
        MessageUtil.sendMsg2TestService(
            getApplication(),
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
        val targetGuids = serversCache.map { it.guid }.toList()
        MmkvManager.clearAllTestDelayResults(targetGuids)
        updateListAction.value = -1

        viewModelScope.launch(Dispatchers.Default) {
            if (targetGuids.isEmpty()) {
                return@launch
            }
            MessageUtil.sendMsg2TestService(
                getApplication(),
                TestServiceMessage(
                    key = AppConfig.MSG_MEASURE_CONFIG_START,
                    subscriptionId = subscriptionId,
                    serverGuids = if (keywordFilter.isNotEmpty()) targetGuids else emptyList(),
                    onlyTcp = onlyTcp
                )
            )
        }
    }

    fun testAllCountryCodes() {
        MessageUtil.sendMsg2CountryCodeTestService(
            getApplication(),
            CountryCodeTestMessage(key = AppConfig.MSG_COUNTRY_CODE_CANCEL)
        )
        val guids = serversCache.map { it.guid }.toList()
        MmkvManager.clearAllCountryCodes(guids)
        updateListAction.value = -1

        viewModelScope.launch(Dispatchers.Default) {
            if (guids.isEmpty()) return@launch
            MessageUtil.sendMsg2CountryCodeTestService(
                getApplication(),
                CountryCodeTestMessage(
                    key = AppConfig.MSG_COUNTRY_CODE_START,
                    subscriptionId = subscriptionId,
                    serverGuids = if (keywordFilter.isNotEmpty()) guids else emptyList()
                )
            )
        }
    }

    fun cancelCountryCodeTest() {
        MessageUtil.sendMsg2CountryCodeTestService(
            getApplication(),
            CountryCodeTestMessage(key = AppConfig.MSG_COUNTRY_CODE_CANCEL)
        )
    }

    fun clearCountryCodes() {
        cancelCountryCodeTest()
        MmkvManager.clearAllCountryCodes(MmkvManager.decodeAllServerList())
        updateListAction.postValue(-1)
    }

    fun testCurrentServerRealPing() {
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_MEASURE_DELAY, "")
    }

    fun fetchCurrentIp() {
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_MEASURE_IP, "")
    }

    fun subscriptionIdChanged(id: String) {
        if (subscriptionId != id) {
            subscriptionId = id
            MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, subscriptionId)
        }
        reloadServerList()
    }

    fun subscriptionIdChangedAsync(id: String) {
        if (subscriptionId != id) {
            subscriptionId = id
            MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, subscriptionId)
        }
        reloadServerList()
    }

    fun getSubscriptions(context: Context): List<GroupMapItem> {
        val subscriptions = SortSubBottomSheet.sorted(
            MmkvManager.decodeSubscriptions(),
            addedTime = { it.subscription.addedTime },
            lastUpdated = { it.subscription.lastUpdated }
        )
        if (subscriptionId.isNotEmpty()
            && !subscriptions.map { it.guid }.contains(subscriptionId)
        ) {
            subscriptionIdChanged("")
        }

        val groups = mutableListOf<GroupMapItem>()
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_GROUP_ALL_DISPLAY)) {
            groups.add(
                GroupMapItem(
                    id = "",
                    remarks = context.getString(R.string.filter_config_all),
                    serverCount = MmkvManager.decodeAllServerList().size,
                    icon = MmkvManager.decodeSettingsString(AppConfig.PREF_GROUP_ALL_TAB_ICON),
                )
            )
        }
        subscriptions.forEach { sub ->
            groups.add(
                GroupMapItem(
                    id = sub.guid,
                    remarks = sub.subscription.remarks,
                    serverCount = MmkvManager.decodeServerList(sub.guid).size,
                    icon = sub.subscription.tabIcon,
                )
            )
        }
        return groups
    }

    fun getPosition(guid: String): Int {
        serversCache.forEachIndexed { index, it ->
            if (it.guid == guid)
                return index
        }
        return -1
    }

    fun removeDuplicateServer(): Int {
        val serversCacheCopy = serversCache.toList().toMutableList()
        val deleteServer = mutableListOf<String>()
        val pinnedServers = MmkvManager.decodePinnedServers()

        serversCacheCopy.forEachIndexed { index, sc ->
            val profile = sc.profile
            if (profile.configType.isComplexType()) {
                return@forEachIndexed
            }

            serversCacheCopy.forEachIndexed { index2, sc2 ->
                if (index2 > index) {
                    val profile2 = sc2.profile
                    if (profile2.configType.isComplexType()) {
                        return@forEachIndexed
                    }

                    // Pinned servers are never treated as the removable duplicate, so
                    // pinning a server is enough to keep it even if an identical
                    // config exists elsewhere in the list.
                    if (profile == profile2 && !deleteServer.contains(sc2.guid) && !pinnedServers.contains(sc2.guid)) {
                        deleteServer.add(sc2.guid)
                    }
                }
            }
        }
        for (it in deleteServer) {
            MmkvManager.removeServer(it)
        }

        return deleteServer.count()
    }

    fun removeAllServer(): Int {
        val count =
            if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
                MmkvManager.removeAllServer()
            } else {
                val pinnedServers = MmkvManager.decodePinnedServers()
                val serversCopy = serversCache.toList().filterNot { pinnedServers.contains(it.guid) }
                for (item in serversCopy) {
                    MmkvManager.removeServer(item.guid)
                }
                serversCopy.count()
            }
        return count
    }

    fun removeInvalidServer(): Int {
        var count = 0
        if (subscriptionId.isEmpty() && keywordFilter.isEmpty()) {
            count += MmkvManager.removeInvalidServer("")
        } else {
            val serversCopy = serversCache.toList()
            for (item in serversCopy) {
                // MmkvManager.removeInvalidServer already skips pinned servers.
                count += MmkvManager.removeInvalidServer(item.guid)
            }
        }
        return count
    }

    fun sortByTestResults() {
        if (subscriptionId.isEmpty()) {
            MmkvManager.decodeSubsList().forEach { guid ->
                sortByTestResultsForSub(guid)
            }
        } else {
            sortByTestResultsForSub(subscriptionId)
        }
    }

    private fun sortByTestResultsForSub(subId: String) {
        data class ServerDelay(var guid: String, var testDelayMillis: Long)

        val serverDelays = mutableListOf<ServerDelay>()
        val serverListToSort = MmkvManager.decodeServerList(subId)

        serverListToSort.forEach { key ->
            val delay = MmkvManager.decodeServerAffiliationInfo(key)?.testDelayMillis ?: 0L
            serverDelays.add(ServerDelay(key, if (delay <= 0L) 999999 else delay))
        }
        serverDelays.sortBy { it.testDelayMillis }

        val sortedServerList = serverDelays.map { it.guid }.toMutableList()

        MmkvManager.encodeServerList(sortedServerList, subId)
    }


    fun initAssets(assets: AssetManager) {
        viewModelScope.launch(Dispatchers.Default) {
            SettingsManager.initAssets(getApplication<AngApplication>(), assets)
        }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) {
            return
        }
        keywordFilter = keyword
        scheduleReload(delayMillis = 120L)
    }

    fun findSubscriptionIdBySelect(): String? {
        val selectedGuid = MmkvManager.getSelectServer()
        if (selectedGuid.isNullOrEmpty()) {
            return null
        }

        val config = MmkvManager.decodeServerConfig(selectedGuid)
        return config?.subscriptionId
    }

    fun onTestsFinished() {
        viewModelScope.launch(Dispatchers.Default) {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST)) {
                removeInvalidServer()
            }

            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST)) {
                if (subscriptionId.isEmpty()) {
                    MmkvManager.decodeSubsList().forEach { subId ->
                        MmkvManager.saveOriginServerList(subId)
                        MmkvManager.encodeSettings("${AppConfig.PREF_SERVER_ORDER}_$subId", 2)
                    }
                    MmkvManager.encodeSettings("${AppConfig.PREF_SERVER_ORDER}_${AppConfig.DEFAULT_SUBSCRIPTION_ID}", 2)
                } else {
                    MmkvManager.saveOriginServerList(subscriptionId)
                    val subIdToSave = subscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
                    MmkvManager.encodeSettings("${AppConfig.PREF_SERVER_ORDER}_$subIdToSave", 2)
                }
                sortByTestResults()
            }

            withContext(Dispatchers.Main) {
                reloadServerList()
            }
        }
    }

    fun resetCurrentProfileTraffic() {
        MmkvManager.getSelectServer()?.let { guid ->
            MmkvManager.resetProfileTraffic(guid)
            updateListAction.postValue(getPosition(guid))
        }
    }

    fun resetGroupTraffic() {
        MmkvManager.resetGroupTraffic(subscriptionId)
        updateListAction.postValue(-1)
    }

    fun resetAllTraffic() {
        MmkvManager.resetAllTraffic()
        updateListAction.postValue(-1)
    }

    fun cancelRealPingTest() {
        MessageUtil.sendMsg2TestService(
            getApplication(),
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
    }

    fun clearTestResults() {
        MessageUtil.sendMsg2TestService(
            getApplication(),
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
        MmkvManager.clearAllTestDelayResults(MmkvManager.decodeAllServerList())
        reloadServerList()
    }

    private val mMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    isRunning.value = true
                }

                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    isRunning.value = false
                }

                AppConfig.MSG_STATE_START_SUCCESS -> {
                    val app = getApplication<AngApplication>()
                    alertAction.value = Pair(true, app.getString(R.string.toast_services_success))
                    isRunning.value = true
                }

                AppConfig.MSG_STATE_START_FAILURE -> {
                    val app = getApplication<AngApplication>()
                    val errorMessage = intent.getStringExtra("content")
                    val msg = if (!errorMessage.isNullOrBlank()) {
                        errorMessage
                    } else {
                        app.getString(R.string.toast_services_failure)
                    }

                    alertAction.value = Pair(false, msg)
                    isRunning.value = false
                }

                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    isRunning.value = false
                }

                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
                    updateTestResultAction.value = intent.getStringExtra("content").orEmpty()
                }

                AppConfig.MSG_MEASURE_IP_SUCCESS -> {
                    val ip = intent.getStringExtra("content")
                    updateIpResultAction.value = ip
                }

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                    val content = intent.getStringExtra("content")
                    updateListAction.value = getPosition(content ?: "")
                }

                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> {
                    testProgressAction.value = intent.serializable<TestProgressInfo>("content")
                }

                AppConfig.MSG_MEASURE_CONFIG_FINISH -> {
                    testProgressAction.value = null
                    onTestsFinished()
                }

                AppConfig.MSG_COUNTRY_CODE_SUCCESS -> {
                    val content = intent.getStringExtra("content")
                    updateListAction.value = getPosition(content ?: "")
                }

                AppConfig.MSG_COUNTRY_CODE_NOTIFY -> {
                    countryCodeProgressAction.value = intent.serializable<TestProgressInfo>("content")
                }

                AppConfig.MSG_COUNTRY_CODE_FINISH -> {
                    countryCodeProgressAction.value = null
                }

                AppConfig.MSG_TRAFFIC_UPDATED -> {
                    val guid = intent.getStringExtra("content") ?: return
                    updateListAction.postValue(getPosition(guid))
                }

                AppConfig.MSG_TRAFFIC_SPEED_UPDATED -> {
                    val speedText = intent.getStringExtra("content") ?: return
                    updateTrafficSpeedAction.postValue(speedText)
                }

                AppConfig.MSG_SUB_UPDATE_FINISH -> {
                    updateGroupOrderAction.postValue(Unit)
                }
            }
        }
    }
}
