package com.miku.ray.ui.main

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
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
import com.miku.ray.dto.RealPingProgress
import com.miku.ray.dto.RealPingResult
import com.miku.ray.dto.RealPingSummary
import com.miku.ray.dto.TestProgressInfo
import com.miku.ray.ui.bottomsheet.SortSubBottomSheet
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.extension.isComplexType
import com.miku.ray.extension.matchesPattern
import com.miku.ray.extension.serializable
import com.miku.ray.core.LauncherManager
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.LogUtil
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.MessageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.PatternSyntaxException

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var serverList = mutableListOf<String>()
    var subscriptionId: String = MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()
    var keywordFilter = ""
    private var activeTestId: String? = null
    private var activeTestCompleted = 0
    private var activeTestTotal = 0
    private var isRestarting = false
    private var pendingServerRestartGuid: String? = null
    private var reloadJob: Job? = null
    private val serviceRepository = MainServiceRepository.get(application as AngApplication)
    @Volatile
    private var serverCacheLoaded = false
    val serversCache = mutableListOf<ServersCache>()

    private val _uiState = MutableStateFlow(
        MainUiState(
            isRunning = LauncherManager.isConnected(),
            testResult = MmkvManager.decodeSettingsString(AppConfig.PREF_LAST_TEST_RESULT, "").orEmpty(),
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _serversState = MutableStateFlow<List<ServersCache>>(emptyList())
    val serversState: StateFlow<List<ServersCache>> = _serversState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val groupCache = ConcurrentHashMap<String, List<ServersCache>>()
    private val groupStates = ConcurrentHashMap<String, MutableStateFlow<List<ServersCache>>>()

    val isRunning by lazy {
        MutableLiveData(LauncherManager.isConnected())
    }
    val updateListAction by lazy { MutableLiveData<Int>() }
    val updateTestResultAction by lazy { MutableLiveData<String>() }
    val testProgressAction by lazy { MutableLiveData<TestProgressInfo?>() }
    val countryCodeProgressAction by lazy { MutableLiveData<TestProgressInfo?>() }
    val updateIpResultAction by lazy { MutableLiveData<String>() }
    val updateTrafficSpeedAction by lazy { MutableLiveData<String>() }
    val serviceRestartAction by lazy { MutableLiveData<Unit>() }
    val alertAction by lazy { MutableLiveData<Pair<Boolean, String>>() }
    val updateGroupBadgeAction by lazy { MutableLiveData<Unit>() }
    val updateGroupOrderAction by lazy { MutableLiveData<Unit>() }

    init {
        viewModelScope.launch {
            serviceRepository.events.collect { handleServiceIntent(it) }
        }
        reloadServerList(notify = false)
    }

    fun resyncState() {
        serviceRepository.resync()
    }

    /**
     * What the FAB should do next, decided here so MainActivity stays a thin
     * dispatcher — same pattern as [requestConnectionTest] for the test tap.
     * The two truly Activity-bound steps (VpnService.prepare() needs a
     * Context, and launching the permission request needs an ActivityResult
     * launcher) still happen in MainActivity; everything else lives here.
     */
    sealed class FabDecision {
        object Stop : FabDecision()
        object NeedVpnPermission : FabDecision()
        object StartDirect : FabDecision()
    }

    fun decideFabAction(): FabDecision {
        resyncState()
        return when {
            isRunning.value == true -> FabDecision.Stop
            SettingsManager.isVpnMode() -> FabDecision.NeedVpnPermission
            else -> FabDecision.StartDirect
        }
    }

    /**
     * Requests a connection test for the FAB/status pill.
     * If the service is already running the test starts immediately;
     * otherwise it's deferred until the service reports it's running
     * (see [consumePendingConnectionTest]).
     */
    fun requestConnectionTest() {
        resyncState()
        if (isRunning.value == true) {
            _uiState.update { it.copy(pendingConnectionTest = false) }
            testCurrentServerRealPing()
        } else {
            markConnectionTestPending()
        }
    }

    fun markConnectionTestPending() {
        _uiState.update { it.copy(pendingConnectionTest = true) }
    }

    /**
     * Returns true (and clears the flag) if a connection test was
     * waiting for the service to come up.
     */
    fun consumePendingConnectionTest(): Boolean {
        val pending = _uiState.value.pendingConnectionTest
        if (pending) _uiState.update { it.copy(pendingConnectionTest = false) }
        return pending
    }

    fun refreshStateFromStorage() {
        val running = LauncherManager.isConnected()
        if (running) isRunning.value = true
        updateListAction.value = -1
        val urlProgress = decodeProgress(AppConfig.PREF_ACTIVE_URL_TEST_PROGRESS)
        val countryProgress = decodeProgress(AppConfig.PREF_ACTIVE_COUNTRY_CODE_PROGRESS)
        _uiState.update {
            it.copy(
                isRunning = it.isRunning || running,
                isTesting = urlProgress != null || countryProgress != null,
                testProgress = urlProgress,
                countryCodeProgress = countryProgress,
            )
        }
        urlProgress?.let { testProgressAction.value = it }
        countryProgress?.let { countryCodeProgressAction.value = it }
    }

    override fun onCleared() {
        reloadJob?.cancel()
        LogUtil.i(AppConfig.TAG, "Main ViewModel is cleared")
        super.onCleared()
    }

    @Synchronized
    fun reloadServerList(notify: Boolean = true) {
        val subId = subscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
        val order = MmkvManager.decodeSettingsInt("${AppConfig.PREF_SERVER_ORDER}_$subId", 0)
        if (order == 0) {
            if (subscriptionId.isEmpty()) {
                MmkvManager.decodeSubsList().forEach { MmkvManager.restoreOriginServerList(it) }
            } else {
                MmkvManager.restoreOriginServerList(subscriptionId)
            }
        }

        serverList = if (subscriptionId.isEmpty()) {
            MmkvManager.decodeAllServerList()
        } else {
            MmkvManager.decodeServerList(subscriptionId)
        }

        updateCache()
        serverCacheLoaded = true
        if (notify) updateListAction.postValue(-1)
    }

    fun refreshServerList(updateSubscription: Boolean = false): Job {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                if (updateSubscription) {
                    updateConfigViaSubAll()
                    refreshAllGroupCaches()
                }
                reloadServerList(notify = false)
                withContext(Dispatchers.Main) {
                    updateListAction.value = -1
                    updateGroupBadgeAction.value = Unit
                }
            } finally {
                _isRefreshing.value = false
            }
        }
        return reloadJob!!
    }

    private fun refreshAllGroupCaches() {
        val groupIds = buildList {
            add("")
            addAll(MmkvManager.decodeSubsList())
        }.distinct()
        groupIds.forEach { groupId ->
            val snapshot = buildServerCache(groupId)
            groupCache[groupId] = snapshot
            groupStates.computeIfAbsent(groupId) { MutableStateFlow(emptyList()) }.value = snapshot
        }
    }

    fun refreshGroups() {
        refreshServerList(updateSubscription = false)
    }

    fun ensureServerCacheReady() {
        if (!serverCacheLoaded) reloadServerList()
    }

    fun removeServer(guid: String) {
        serverList.remove(guid)
        MmkvManager.removeServer(guid)
        updateCache()
        refreshAllGroupCaches()
        updateListAction.postValue(-1)
        updateGroupBadgeAction.postValue(Unit)
    }

    fun swapServer(fromPosition: Int, toPosition: Int) {
        if (subscriptionId.isEmpty()) {
            return
        }

        Collections.swap(serverList, fromPosition, toPosition)
        Collections.swap(serversCache, fromPosition, toPosition)
        val reordered = serversCache.toList()
        groupCache[subscriptionId] = reordered
        groupStates.computeIfAbsent(subscriptionId) { MutableStateFlow(emptyList()) }.value = reordered
        _serversState.value = reordered

        MmkvManager.encodeServerList(serverList, subscriptionId)
    }

    @Synchronized
    fun updateCache() {
        val groupId = subscriptionId
        val snapshot = buildServerCache(groupId)
        serverList = if (groupId.isEmpty()) {
            MmkvManager.decodeAllServerList()
        } else {
            MmkvManager.decodeServerList(groupId)
        }
        serversCache.clear()
        serversCache.addAll(snapshot)
        groupCache[groupId] = snapshot
        groupStates.computeIfAbsent(groupId) { MutableStateFlow(emptyList()) }.value = snapshot
        _serversState.value = snapshot
    }

    fun serversForGroup(groupId: String): StateFlow<List<ServersCache>> {
        val state = groupStates.computeIfAbsent(groupId) { MutableStateFlow(emptyList()) }
        if (state.value.isEmpty() && !groupCache.containsKey(groupId)) {
            val snapshot = buildServerCache(groupId)
            groupCache[groupId] = snapshot
            state.value = snapshot
        }
        return state.asStateFlow()
    }

    private fun buildServerCache(groupId: String): List<ServersCache> {
        val guids = if (groupId.isEmpty()) {
            MmkvManager.decodeAllServerList()
        } else {
            MmkvManager.decodeServerList(groupId)
        }
        val kw = keywordFilter.trim()
        val searchRegex = try {
            if (kw.isNotEmpty()) Regex(kw, setOf(RegexOption.IGNORE_CASE)) else null
        } catch (_: PatternSyntaxException) {
            null
        }
        val result = guids.mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            if (kw.isEmpty()) return@mapNotNull ServersCache(guid, profile)
            val matches = profile.remarks.matchesPattern(searchRegex, kw)
            || profile.description.orEmpty().matchesPattern(searchRegex, kw)
            || profile.server.orEmpty().matchesPattern(searchRegex, kw)
            || profile.configType.name.matchesPattern(searchRegex, kw)
            if (matches) ServersCache(guid, profile) else null
        }.toMutableList()

        val subId = groupId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
        when (MmkvManager.decodeSettingsInt("${AppConfig.PREF_SERVER_ORDER}_$subId", 0)) {
            1 -> result.sortWith(compareBy { it.profile.remarks.lowercase() })
            2 -> result.sortWith(compareBy {
                    val delay = MmkvManager.decodeServerAffiliationInfo(it.guid)?.testDelayMillis ?: 0L
                    if (delay <= 0L) Long.MAX_VALUE else delay
            })
        }
        val pinnedServers = MmkvManager.decodePinnedServers()
        if (pinnedServers.isNotEmpty()) {
            result.sortByDescending { pinnedServers.contains(it.guid) }
        }
        return result
    }

    fun togglePinServer(guid: String): Boolean {
        val nowPinned = MmkvManager.togglePinnedServer(guid)
        updateCache()
        refreshAllGroupCaches()
        updateListAction.postValue(-1)
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
        val testId = UUID.randomUUID().toString()
        activeTestId = testId
        activeTestCompleted = 0
        activeTestTotal = serversCache.size
        MmkvManager.clearAllTestDelayResults(serversCache.map { it.guid }.toList())
        persistProgress(AppConfig.PREF_ACTIVE_URL_TEST_PROGRESS, TestProgressInfo("", -1L, 0, activeTestTotal))
        _uiState.update { it.copy(isTesting = true, isUrlTestMinimized = false) }
        updateListAction.value = -1

        viewModelScope.launch(Dispatchers.Default) {
            if (serversCache.isEmpty()) {
                withContext(Dispatchers.Main) {
                    reloadServerList()
                    activeTestTotal = serversCache.size
                }
            }
            if (serversCache.isEmpty()) {
                activeTestId = null
                withContext(Dispatchers.Main) {
                    testProgressAction.value = null
                    clearUrlTestProgressState()
                }
                return@launch
            }
            MessageUtil.sendMsg2TestService(
                getApplication(),
                TestServiceMessage(
                    key = AppConfig.MSG_MEASURE_CONFIG_START,
                    testId = testId,
                    subscriptionId = subscriptionId,
                    serverGuids = if (keywordFilter.isNotEmpty()) serversCache.map { it.guid } else emptyList(),
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
        persistProgress(AppConfig.PREF_ACTIVE_COUNTRY_CODE_PROGRESS, TestProgressInfo("", -1L, 0, guids.size))
        _uiState.update { it.copy(isTesting = true, isCountryCodeTestMinimized = false) }
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

    fun clearCountryCodesForGroup() {
        cancelCountryCodeTest()
        MmkvManager.clearAllCountryCodes(MmkvManager.decodeServerList(subscriptionId))
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
        viewModelScope.launch(Dispatchers.IO) {
            reloadServerList()
        }
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
        groupCache.clear()
        refreshAllGroupCaches()
        reloadServerList()
    }

    fun beginServerRestart(guid: String): Boolean {
        if (guid == MmkvManager.getSelectServer() || pendingServerRestartGuid != null) return false

        MmkvManager.setSelectServer(guid)
        pendingServerRestartGuid = guid
        isRestarting = true
        return true
    }

    fun onServerRestartRequestResult(guid: String, handled: Boolean) {
        if (handled || pendingServerRestartGuid != guid) return

        pendingServerRestartGuid = null
        isRestarting = false
        markConnectionStopped()
        isRunning.value = false
        _uiState.update { it.copy(isRunning = false) }
    }

    private fun markConnectionStopped() {
        LauncherManager.markConnectStopped()
    }

    fun findSubscriptionIdBySelect(): String? {
        val selectedGuid = MmkvManager.getSelectServer()
        if (selectedGuid.isNullOrEmpty()) {
            return null
        }

        val config = MmkvManager.decodeServerConfig(selectedGuid)
        return config?.subscriptionId
    }

    fun onTestsFinished(cancelled: Boolean = false) {
        viewModelScope.launch(Dispatchers.Default) {
            if (cancelled) return@launch
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
        val testId = activeTestId.orEmpty()
        activeTestId = null
        testProgressAction.value = null
        clearUrlTestProgressState()
        MessageUtil.sendMsg2TestService(
            getApplication(),
            TestServiceMessage(
                key = AppConfig.MSG_MEASURE_CONFIG_CANCEL,
                testId = testId,
            )
        )
    }

    fun clearTestResults() {
        MessageUtil.sendMsg2TestService(
            getApplication(),
            TestServiceMessage(
                key = AppConfig.MSG_MEASURE_CONFIG_CANCEL,
                testId = activeTestId.orEmpty(),
            )
        )
        MmkvManager.clearAllTestDelayResults(MmkvManager.decodeAllServerList())
        updateCache()
        updateListAction.postValue(-1)
    }

    fun clearTestResultsForGroup() {
        MessageUtil.sendMsg2TestService(
            getApplication(),
            TestServiceMessage(
                key = AppConfig.MSG_MEASURE_CONFIG_CANCEL,
                testId = activeTestId.orEmpty(),
            )
        )
        MmkvManager.clearAllTestDelayResults(MmkvManager.decodeServerList(subscriptionId))
        updateCache()
        updateListAction.postValue(-1)
    }

    private fun handleServiceIntent(intent: Intent) {
            val key = intent?.getIntExtra("key", 0)
            when (key) {
                AppConfig.MSG_STATE_RUNNING -> {
                    if (!isRestarting) {
                        _uiState.update { it.copy(isRunning = true) }
                        if (isRunning.value != true) {
                            isRunning.value = true
                        }

                        updateListAction.postValue(-1)
                    }
                }

                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    if (!isRestarting) {
                        _uiState.update { it.copy(isRunning = false, isTesting = false) }
                        markConnectionStopped()
                        if (isRunning.value != false) {
                            isRunning.value = false
                        }
                        updateListAction.postValue(-1)
                    }
                }

                AppConfig.MSG_STATE_RESTART -> {
                    markConnectionStopped()
                    isRestarting = true
                    serviceRestartAction.value = Unit
                }

                AppConfig.MSG_STATE_START_SUCCESS -> {
                    val app = getApplication<AngApplication>()
                    val restarted = intent.serializable<Boolean>("content") == true
                    pendingServerRestartGuid = null
                    isRestarting = false
                    alertAction.value = Pair(
                        true,
                        app.getString(
                            if (restarted) R.string.toast_services_restart_success
                            else R.string.toast_services_success,
                        ),
                    )
                    isRunning.value = true
                    _uiState.update { it.copy(isRunning = true) }
                    updateListAction.postValue(-1)
                }

                AppConfig.MSG_STATE_START_FAILURE -> {
                    val app = getApplication<AngApplication>()
                    val errorMessage = intent.getStringExtra("content")
                    val msg = if (!errorMessage.isNullOrBlank()) {
                        errorMessage
                    } else {
                        app.getString(R.string.toast_services_failure)
                    }

                    pendingServerRestartGuid = null
                    isRestarting = false
                    alertAction.value = Pair(false, msg)
                    markConnectionStopped()
                    isRunning.value = false
                    _uiState.update { it.copy(isRunning = false, isTesting = false, pendingConnectionTest = false) }
                    updateListAction.postValue(-1)
                }

                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    pendingServerRestartGuid = null
                    isRestarting = false
                    _uiState.update { it.copy(isRunning = false, isTesting = false, pendingConnectionTest = false) }
                    markConnectionStopped()
                    isRunning.value = false
                    updateListAction.postValue(-1)
                }

                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
                    val result = intent.getStringExtra("content").orEmpty()
                    MmkvManager.encodeSettings(AppConfig.PREF_LAST_TEST_RESULT, result)
                    _uiState.update { it.copy(testResult = result) }
                    updateTestResultAction.value = result
                }

                AppConfig.MSG_MEASURE_IP_SUCCESS -> {
                    val ip = intent.getStringExtra("content")
                    updateIpResultAction.value = ip
                }

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {

                    val result = parseExtra(intent, RealPingResult::class.java)
                    ?: intent.serializable<RealPingResult>("content")
                    if (result != null) {
                        if (acceptsTestEvent(result.testId)) {
                            updateListAction.postValue(getPosition(result.guid))
                            activeTestCompleted += 1
                            activeTestTotal = maxOf(activeTestTotal, activeTestCompleted)
                            testProgressAction.postValue(
                                TestProgressInfo(
                                    guid = result.guid,
                                    delayMillis = result.delayMillis,
                                    current = activeTestCompleted,
                                    total = activeTestTotal,
                                )
                            )
                            persistProgress(
                                AppConfig.PREF_ACTIVE_URL_TEST_PROGRESS,
                                TestProgressInfo(result.guid, result.delayMillis, activeTestCompleted, activeTestTotal)
                            )
                        }
                    } else {
                        val content = intent.getStringExtra("content")

                        val guid = content?.takeIf { !it.trimStart().startsWith("{") }.orEmpty()
                        updateListAction.postValue(getPosition(guid))
                    }
                }

                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> {
                    val progress = parseExtra(intent, RealPingProgress::class.java)
                    ?: intent.serializable<RealPingProgress>("content")
                    if (progress != null) {
                        if (acceptsTestEvent(progress.testId)) {
                            activeTestCompleted = maxOf(activeTestCompleted, progress.completed)
                            activeTestTotal = maxOf(activeTestTotal, progress.total)
                            testProgressAction.postValue(
                                TestProgressInfo(
                                    guid = "",
                                    delayMillis = -1L,
                                    current = activeTestCompleted,
                                    total = activeTestTotal,
                                )
                            )
                            persistProgress(
                                AppConfig.PREF_ACTIVE_URL_TEST_PROGRESS,
                                TestProgressInfo("", -1L, activeTestCompleted, activeTestTotal)
                            )
                        }
                    } else {
                        val info = parseExtra(intent, TestProgressInfo::class.java)
                        ?: intent.serializable<TestProgressInfo>("content")
                        if (info != null) {
                            persistProgress(AppConfig.PREF_ACTIVE_URL_TEST_PROGRESS, info)
                            testProgressAction.postValue(info)
                        }
                    }
                }

                AppConfig.MSG_MEASURE_CONFIG_FINISH -> {
                    val summary = parseExtra(intent, RealPingSummary::class.java)
                    ?: intent.serializable<RealPingSummary>("content")
                    if (summary != null) {
                        if (!acceptsTestEvent(summary.testId)) return
                        activeTestId = null
                        MmkvManager.encodeSettings(AppConfig.PREF_ACTIVE_URL_TEST_PROGRESS, "")
                        clearUrlTestProgressState()
                        testProgressAction.postValue(null)
                        onTestsFinished(summary.cancelled)
                    } else {
                        activeTestId = null
                        MmkvManager.encodeSettings(AppConfig.PREF_ACTIVE_URL_TEST_PROGRESS, "")
                        clearUrlTestProgressState()
                        testProgressAction.postValue(null)
                        onTestsFinished()
                    }
                }

                AppConfig.MSG_COUNTRY_CODE_SUCCESS -> {
                    val content = intent.getStringExtra("content")
                    updateListAction.postValue(getPosition(content ?: ""))
                }

                AppConfig.MSG_COUNTRY_CODE_NOTIFY -> {

                    val info = parseExtra(intent, TestProgressInfo::class.java)
                    ?: intent.serializable<TestProgressInfo>("content")
                    if (info != null) {
                        persistProgress(AppConfig.PREF_ACTIVE_COUNTRY_CODE_PROGRESS, info)
                        countryCodeProgressAction.postValue(info)
                    }
                }

                AppConfig.MSG_COUNTRY_CODE_FINISH -> {
                    MmkvManager.encodeSettings(AppConfig.PREF_ACTIVE_COUNTRY_CODE_PROGRESS, "")
                    clearCountryCodeProgressState()
                    countryCodeProgressAction.postValue(null)
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

    private fun acceptsTestEvent(testId: String): Boolean =
    testId.isEmpty() || testId == activeTestId

    private fun <T> parseExtra(intent: Intent, cls: Class<T>): T? {
        val raw = intent.getStringExtra("content") ?: return null
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
        return JsonUtil.fromJsonSafe(trimmed, cls)
    }

    private fun persistProgress(key: String, info: TestProgressInfo) {
        MmkvManager.encodeSettings(key, JsonUtil.toJson(info))
        _uiState.update {
            if (key == AppConfig.PREF_ACTIVE_URL_TEST_PROGRESS) {
                it.copy(isTesting = true, testProgress = info)
            } else {
                it.copy(isTesting = true, countryCodeProgress = info)
            }
        }
    }

    private fun clearUrlTestProgressState() {
        _uiState.update {
            it.copy(testProgress = null, isTesting = it.countryCodeProgress != null, isUrlTestMinimized = false)
        }
    }

    private fun clearCountryCodeProgressState() {
        _uiState.update {
            it.copy(countryCodeProgress = null, isTesting = it.testProgress != null, isCountryCodeTestMinimized = false)
        }
    }

    fun markUrlTestMinimized() {
        _uiState.update { it.copy(isUrlTestMinimized = true) }
    }

    fun markCountryCodeTestMinimized() {
        _uiState.update { it.copy(isCountryCodeTestMinimized = true) }
    }

    private fun decodeProgress(key: String): TestProgressInfo? {
        val raw = MmkvManager.decodeSettingsString(key)?.trim().orEmpty()
        return if (raw.startsWith("{")) JsonUtil.fromJsonSafe(raw, TestProgressInfo::class.java) else null
    }

}
