package com.miku.ray.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.miku.ray.AppConfig
import com.miku.ray.dto.GroupMapItem
import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.TestProgressInfo
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.dto.entities.ServersCache
import com.miku.ray.extension.isComplexType
import com.miku.ray.extension.matchesPattern
import com.miku.ray.ui.base.BaseViewModel
import com.miku.ray.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.PatternSyntaxException

/**
 * MainViewModel restructured to match v2rayNG's StateFlow/MVI shape:
 * MainUiState + sealed MainAction/MainServiceEvent + MainDataSource
 * abstraction + BaseViewModel toast/event helpers, in place of the previous
 * AndroidViewModel + LiveData + raw BroadcastReceiver design. All of
 * MikuRay's own functionality (server pinning, country-code testing,
 * restart-safe server switching, traffic reset, per-group tab badges/order)
 * is preserved, exposed either through [onAction] for fire-and-forget
 * intents, or as regular public methods where the caller needs a
 * synchronous return value - the same split v2rayNG's own MainViewModel
 * uses for things like serversForGroup()/getSubscriptions().
 */
class MainViewModel(
    application: Application,
    private val dataSource: MainDataSource
) : BaseViewModel(application) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default

    // ---------- UI state ----------
    private val _uiState = MutableStateFlow(
        MainUiState(
            selectedGroupId = dataSource.getSelectedSubscriptionId(),
            selectedGuid = dataSource.getSelectServer()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // ---------- Keyword filtering (shared across every cached group, like v2rayNG) ----------
    @Volatile
    private var keywordFilter: String = ""

    // ---------- Groups & per-group server cache ----------
    private val cacheMutex = Mutex()
    private val groupDataCache = mutableMapOf<String, List<ServersCache>>()
    private val groupServerFlows = ConcurrentHashMap<String, MutableStateFlow<List<ServersCache>>>()
    private val groupLoadMutexes = ConcurrentHashMap<String, Mutex>()

    private var setupGroupJob: Job? = null
    private var selectedGroupLoadJob: Job? = null

    @Volatile
    private var isRestarting = false

    @Volatile
    private var pendingServerRestartGuid: String? = null

    private var activeTestId: String? = null
    private var activeTestCompleted = 0
    private var activeTestTotal = 0

    // ---------- Service events ----------
    init {
        collectServiceEvents()
    }

    private fun collectServiceEvents() {
        viewModelScope.launch {
            dataSource.mainServiceEvent.collect { event -> handleServiceEvent(event) }
        }
    }

    private fun handleServiceEvent(event: MainServiceEvent) {
        when (event) {
            MainServiceEvent.StateRunning -> if (!isRestarting) updateRunningState(true)

            MainServiceEvent.StateNotRunning -> if (!isRestarting) {
                markConnectionStopped()
                updateRunningState(false)
            }

            MainServiceEvent.StateRestart -> {
                markConnectionStopped()
                isRestarting = true
                emitEvent(MainViewModelEvent.ServiceRestart)
            }

            is MainServiceEvent.StateStartSuccess -> {
                pendingServerRestartGuid = null
                isRestarting = false
                emitEvent(
                    MainViewModelEvent.Alert(
                        true,
                        getString(if (event.restarted) com.miku.ray.R.string.toast_services_restart_success else com.miku.ray.R.string.toast_services_success)
                    )
                )
                updateRunningState(true)
                onAction(MainAction.FetchCurrentIp)
            }

            is MainServiceEvent.StateStartFailure -> {
                val msg = event.message?.takeIf { it.isNotBlank() } ?: getString(com.miku.ray.R.string.toast_services_failure)
                pendingServerRestartGuid = null
                isRestarting = false
                emitEvent(MainViewModelEvent.Alert(false, msg))
                markConnectionStopped()
                updateRunningState(false)
            }

            MainServiceEvent.StateStopSuccess -> {
                pendingServerRestartGuid = null
                isRestarting = false
                markConnectionStopped()
                updateRunningState(false)
            }

            is MainServiceEvent.MeasureDelaySuccess -> emitEvent(MainViewModelEvent.TestResultText(event.content))

            is MainServiceEvent.MeasureIpSuccess -> _uiState.update { it.copy(ipResult = event.ip) }

            is MainServiceEvent.MeasureConfigResult -> {
                val guid = event.result?.guid ?: event.legacyGuid
                if (event.result != null) {
                    if (!acceptsTestEvent(event.result.testId)) return
                    activeTestCompleted += 1
                    activeTestTotal = maxOf(activeTestTotal, activeTestCompleted)
                    _uiState.update {
                        it.copy(
                            testProgress = TestProgressInfo(
                                guid = event.result.guid,
                                delayMillis = event.result.delayMillis,
                                current = activeTestCompleted,
                                total = activeTestTotal,
                            )
                        )
                    }
                }
                emitEvent(MainViewModelEvent.ListChanged(guid))
            }

            is MainServiceEvent.MeasureConfigNotify -> {
                if (event.progress != null) {
                    if (!acceptsTestEvent(event.progress.testId)) return
                    activeTestCompleted = maxOf(activeTestCompleted, event.progress.completed)
                    activeTestTotal = maxOf(activeTestTotal, event.progress.total)
                    _uiState.update {
                        it.copy(
                            testProgress = TestProgressInfo(
                                guid = "",
                                delayMillis = -1L,
                                current = activeTestCompleted,
                                total = activeTestTotal,
                            )
                        )
                    }
                } else if (event.legacy != null) {
                    _uiState.update { it.copy(testProgress = event.legacy) }
                }
            }

            is MainServiceEvent.MeasureConfigFinish -> {
                val summary = event.summary
                if (summary != null) {
                    if (!acceptsTestEvent(summary.testId)) return
                    activeTestId = null
                    _uiState.update { it.copy(testProgress = null) }
                    onTestsFinished(summary.cancelled)
                } else {
                    activeTestId = null
                    _uiState.update { it.copy(testProgress = null) }
                    onTestsFinished()
                }
            }

            is MainServiceEvent.CountryCodeSuccess -> emitEvent(MainViewModelEvent.ListChanged(event.guid))

            is MainServiceEvent.CountryCodeNotify -> _uiState.update { it.copy(countryCodeProgress = event.info) }

            MainServiceEvent.CountryCodeFinish -> _uiState.update { it.copy(countryCodeProgress = null) }

            is MainServiceEvent.TrafficUpdated -> emitEvent(MainViewModelEvent.ListChanged(event.guid))

            is MainServiceEvent.TrafficSpeedUpdated -> _uiState.update { it.copy(trafficSpeedText = event.text) }

            MainServiceEvent.SubUpdateFinish -> emitEvent(MainViewModelEvent.GroupOrderChanged)
        }
    }

    private fun acceptsTestEvent(testId: String): Boolean = testId.isEmpty() || testId == activeTestId

    private fun emitEvent(event: MainViewModelEvent) {
        viewModelScope.launch { _viewModelEvent.send(event) }
    }

    private fun updateRunningState(running: Boolean) {
        _uiState.update { it.copy(isRunning = running) }
    }

    private fun markConnectionStopped() = dataSource.markConnectionStopped()

    // ---------- Action handler ----------
    fun onAction(action: MainAction) {
        when (action) {
            MainAction.Initialize -> initialize()
            is MainAction.SelectGroup -> selectGroup(action.groupId)
            MainAction.ReloadServerList -> reloadCurrentGroup()
            is MainAction.FilterConfig -> filterConfig(action.keyword)

            is MainAction.TestAllRealPing -> testAllRealPing(action.onlyTcp)
            MainAction.TestAllCountryCodes -> testAllCountryCodes()
            MainAction.CancelRealPingTest -> cancelRealPingTest()
            MainAction.CancelCountryCodeTest -> cancelCountryCodeTest()
            MainAction.ClearTestResults -> clearTestResults(forGroupOnly = false)
            MainAction.ClearTestResultsForGroup -> clearTestResults(forGroupOnly = true)
            MainAction.ClearCountryCodes -> clearCountryCodes(forGroupOnly = false)
            MainAction.ClearCountryCodesForGroup -> clearCountryCodes(forGroupOnly = true)

            MainAction.ResetCurrentProfileTraffic -> resetCurrentProfileTraffic()
            MainAction.ResetGroupTraffic -> resetGroupTraffic()
            MainAction.ResetAllTraffic -> resetAllTraffic()

            MainAction.ResyncState -> dataSource.registerClient()
            MainAction.FetchCurrentIp -> dataSource.sendMsg2Service(AppConfig.MSG_MEASURE_IP, "")
        }
    }

    private fun initialize() {
        dataSource.registerClient()
        viewModelScope.launch(defaultDispatcher) { dataSource.initAssets() }
        refreshGroups()
    }

    // ---------- Groups ----------
    fun refreshGroups() {
        setupGroupJob?.cancel()
        setupGroupJob = viewModelScope.launch(ioDispatcher) {
            val groups = dataSource.getGroups()
            val current = uiState.value.selectedGroupId
            val resolved = when {
                groups.isEmpty() -> ""
                groups.any { it.id == current } -> current
                else -> groups.first().id
            }
            if (resolved != current) dataSource.setSelectedSubscriptionId(resolved)
            val validIds = groups.mapTo(HashSet()) { it.id }
            groupServerFlows.keys.removeAll { it !in validIds }
            groupLoadMutexes.keys.removeAll { it !in validIds }
            cacheMutex.withLock { groupDataCache.keys.removeAll { it !in validIds } }

            _uiState.update { it.copy(groups = groups, selectedGroupId = resolved) }
            emitEvent(MainViewModelEvent.GroupBadgeChanged)
        }
    }

    /** Kept with the same signature/behaviour v2rayNG and the old ViewModel exposed. */
    fun getSubscriptions(): List<GroupMapItem> {
        val groups = dataSource.getGroups()
        if (uiState.value.selectedGroupId.isNotEmpty() && groups.none { it.id == uiState.value.selectedGroupId }) {
            selectGroup("")
        }
        return groups
    }

    fun selectGroup(id: String) {
        if (uiState.value.selectedGroupId != id) {
            dataSource.setSelectedSubscriptionId(id)
            _uiState.update { it.copy(selectedGroupId = id) }
        }
        mutableServerFlow(id)
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            try {
                updateGroupUi(id, loadGroup(id, forceRefresh = true))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to load selected group: $id", error)
            }
        }
    }

    private fun reloadCurrentGroup() {
        val groupId = uiState.value.selectedGroupId
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
        }
    }

    // ---------- Public state accessors ----------
    fun serversForGroup(groupId: String): StateFlow<List<ServersCache>> =
        mutableServerFlow(groupId).asStateFlow()

    private fun mutableServerFlow(groupId: String): MutableStateFlow<List<ServersCache>> =
        groupServerFlows.computeIfAbsent(groupId) { MutableStateFlow(emptyList()) }

    fun getPosition(groupId: String, guid: String): Int =
        mutableServerFlow(groupId).value.indexOfFirst { it.guid == guid }

    // ---------- Group & server loading ----------
    private suspend fun buildServersCache(guids: List<String>): List<ServersCache> =
        guids.mapNotNull { guid ->
            currentCoroutineContext().ensureActive()
            val profile = dataSource.decodeServerConfig(guid) ?: return@mapNotNull null
            ServersCache(guid = guid, profile = profile)
        }

    private suspend fun loadGroup(groupId: String, forceRefresh: Boolean = false): List<ServersCache> {
        val loadMutex = groupLoadMutexes.computeIfAbsent(groupId) { Mutex() }
        return loadMutex.withLock {
            if (!forceRefresh) {
                cacheMutex.withLock { groupDataCache[groupId]?.let { return@withLock it } }
            }
            dataSource.restoreOriginServerListIfNeeded(groupId)
            val servers = buildServersCache(dataSource.getServerGuidList(groupId))
            currentCoroutineContext().ensureActive()
            cacheMutex.withLock { groupDataCache[groupId] = servers }
            servers
        }
    }

    private fun applyKeywordFilter(servers: List<ServersCache>): List<ServersCache> {
        val keyword = keywordFilter.trim()
        if (keyword.isEmpty()) return servers
        val regex = try {
            Regex(keyword, RegexOption.IGNORE_CASE)
        } catch (_: PatternSyntaxException) {
            return servers
        }
        return servers.filter { cache ->
            val profile = cache.profile
            profile.remarks.matchesPattern(regex, keyword) ||
                profile.description.orEmpty().matchesPattern(regex, keyword) ||
                profile.server.orEmpty().matchesPattern(regex, keyword) ||
                profile.configType.name.matchesPattern(regex, keyword)
        }
    }

    /** Sort by the group's stored order preference, then float pinned servers to the top. */
    private fun applyOrderAndPin(groupId: String, servers: List<ServersCache>): List<ServersCache> {
        val ordered = when (dataSource.getSortOrder(groupId)) {
            1 -> servers.sortedBy { it.profile.remarks.lowercase() }
            2 -> servers.sortedBy { server ->
                val delay = dataSource.decodeAffiliationInfo(server.guid)?.testDelayMillis ?: 0L
                if (delay <= 0L) Long.MAX_VALUE else delay
            }
            else -> servers
        }
        val pinned = dataSource.decodePinnedServers()
        return if (pinned.isEmpty()) ordered else ordered.sortedByDescending { pinned.contains(it.guid) }
    }

    private fun updateGroupUi(groupId: String, servers: List<ServersCache>) {
        val filtered = applyKeywordFilter(servers)
        mutableServerFlow(groupId).value = applyOrderAndPin(groupId, filtered)
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) return
        keywordFilter = keyword
        viewModelScope.launch(defaultDispatcher) {
            val snapshot = cacheMutex.withLock { groupDataCache.toMap() }
            snapshot.forEach { (groupId, servers) -> updateGroupUi(groupId, servers) }
        }
    }

    // ---------- Server mutation ----------
    /**
     * Plain delete used by GroupServerFragment's own row swipe-to-remove,
     * which already does its own optimistic RecyclerView removal animation -
     * no list refresh here, mirroring the old ViewModel's behaviour.
     */
    fun removeServer(guid: String) {
        dataSource.removeServer(guid)
        viewModelScope.launch(ioDispatcher) {
            cacheMutex.withLock { groupDataCache.clear() }
        }
        emitEvent(MainViewModelEvent.GroupBadgeChanged)
    }

    /** Toggles the pinned state, re-sorts the visible list immediately, and returns the new state. */
    fun togglePinServer(groupId: String, guid: String): Boolean {
        val nowPinned = dataSource.togglePinnedServer(guid)
        // Re-run the pin/order pass over the list already on screen - it has
        // already been through the keyword filter, so this only re-sorts it.
        mutableServerFlow(groupId).value = applyOrderAndPin(groupId, mutableServerFlow(groupId).value)
        return nowPinned
    }

    fun swapServer(groupId: String, fromPosition: Int, toPosition: Int) {
        if (groupId.isEmpty()) return
        val current = mutableServerFlow(groupId).value.toMutableList()
        if (fromPosition !in current.indices || toPosition !in current.indices) return
        val moved = current.removeAt(fromPosition)
        current.add(toPosition, moved)
        mutableServerFlow(groupId).value = current
        viewModelScope.launch(ioDispatcher) {
            dataSource.encodeServerList(current.map { it.guid }, groupId)
            cacheMutex.withLock { groupDataCache[groupId] = current }
        }
    }

    /** Persists a new selection while waiting for the active daemon to accept restart. */
    fun beginServerRestart(guid: String): Boolean {
        if (guid == dataSource.getSelectServer() || pendingServerRestartGuid != null) return false
        dataSource.setSelectServer(guid)
        _uiState.update { it.copy(selectedGuid = guid) }
        pendingServerRestartGuid = guid
        isRestarting = true
        return true
    }

    /** Clears the optimistic restart state when no active daemon accepted the request. */
    fun onServerRestartRequestResult(guid: String, handled: Boolean) {
        if (handled || pendingServerRestartGuid != guid) return
        pendingServerRestartGuid = null
        isRestarting = false
        markConnectionStopped()
        updateRunningState(false)
    }

    fun findSubscriptionIdBySelect(): String? {
        val selectedGuid = dataSource.getSelectServer().takeUnless { it.isNullOrEmpty() } ?: return null
        return dataSource.decodeServerConfig(selectedGuid)?.subscriptionId
    }

    // ---------- Testing ----------
    fun cancelRealPingTest() {
        val testId = activeTestId.orEmpty()
        activeTestId = null
        _uiState.update { it.copy(testProgress = null, isTesting = false) }
        dataSource.sendMsg2TestService(TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL, testId = testId))
    }

    fun cancelCountryCodeTest() {
        dataSource.sendMsg2CountryCodeTestService(CountryCodeTestMessage(key = AppConfig.MSG_COUNTRY_CODE_CANCEL))
        _uiState.update { it.copy(isTestingCountryCode = false) }
    }

    fun testAllRealPing(onlyTcp: Boolean = false) {
        // Always cancel whatever the test service might still be running first,
        // exactly like v2rayNG's dataSource.cancelAllPing() at the top of its own
        // testAllRealPing() - unconditional, no need to gate on activeTestId.
        // CoreTestService now cancels its own active batch synchronously on this
        // message instead of the old process-kill/redeliver dance, so this never
        // wakes a dead service and never leaves a batch's FINISH summary stranded
        // (which was leaving isTesting stuck true forever).
        dataSource.sendMsg2TestService(TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL))
        val groupId = uiState.value.selectedGroupId
        val testId = UUID.randomUUID().toString()
        activeTestId = testId
        activeTestCompleted = 0
        val servers = mutableServerFlow(groupId).value
        activeTestTotal = servers.size
        dataSource.clearAllTestDelayResults(servers.map { it.guid })
        _uiState.update { it.copy(isTesting = true, testProgress = null) }

        viewModelScope.launch(defaultDispatcher) {
            var currentServers = servers
            if (currentServers.isEmpty()) {
                withContext(ioDispatcher) { updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true)) }
                currentServers = mutableServerFlow(groupId).value
                activeTestTotal = currentServers.size
            }
            if (currentServers.isEmpty()) {
                activeTestId = null
                _uiState.update { it.copy(isTesting = false, testProgress = null) }
                return@launch
            }
            dataSource.sendMsg2TestService(
                TestServiceMessage(
                    key = AppConfig.MSG_MEASURE_CONFIG_START,
                    testId = testId,
                    subscriptionId = groupId,
                    serverGuids = if (keywordFilter.isNotEmpty()) currentServers.map { it.guid } else emptyList(),
                    onlyTcp = onlyTcp
                )
            )
        }
    }

    fun testAllCountryCodes() {
        dataSource.sendMsg2CountryCodeTestService(CountryCodeTestMessage(key = AppConfig.MSG_COUNTRY_CODE_CANCEL))
        val groupId = uiState.value.selectedGroupId
        val guids = mutableServerFlow(groupId).value.map { it.guid }
        dataSource.clearAllCountryCodes(guids)
        _uiState.update { it.copy(isTestingCountryCode = true) }
        emitEvent(MainViewModelEvent.ListChanged(null))

        viewModelScope.launch(defaultDispatcher) {
            if (guids.isEmpty()) {
                _uiState.update { it.copy(isTestingCountryCode = false) }
                return@launch
            }
            dataSource.sendMsg2CountryCodeTestService(
                CountryCodeTestMessage(
                    key = AppConfig.MSG_COUNTRY_CODE_START,
                    subscriptionId = groupId,
                    serverGuids = if (keywordFilter.isNotEmpty()) guids else emptyList()
                )
            )
        }
    }

    fun testCurrentServerRealPing() {
        dataSource.sendMsg2Service(AppConfig.MSG_MEASURE_DELAY, "")
    }

    private fun clearTestResults(forGroupOnly: Boolean) {
        dataSource.sendMsg2TestService(TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL, testId = activeTestId.orEmpty()))
        val guids = if (forGroupOnly) dataSource.getServerGuidList(uiState.value.selectedGroupId) else dataSource.getServerGuidList("")
        dataSource.clearAllTestDelayResults(guids)
        reloadCurrentGroup()
    }

    private fun clearCountryCodes(forGroupOnly: Boolean) {
        cancelCountryCodeTest()
        val guids = if (forGroupOnly) dataSource.getServerGuidList(uiState.value.selectedGroupId) else dataSource.getServerGuidList("")
        dataSource.clearAllCountryCodes(guids)
        emitEvent(MainViewModelEvent.ListChanged(null))
    }

    private fun onTestsFinished(cancelled: Boolean = false) {
        viewModelScope.launch(defaultDispatcher) {
            _uiState.update { it.copy(isTesting = false) }
            if (cancelled) return@launch
            val groupId = uiState.value.selectedGroupId
            if (dataSource.isAutoRemoveInvalidAfterTest()) {
                removeInvalidServerInternal(forGroupOnly = groupId.isNotEmpty() || keywordFilter.isNotBlank())
            }
            if (dataSource.isAutoSortAfterTest()) {
                if (groupId.isEmpty()) {
                    dataSource.getSubsList().forEach { subId -> dataSource.prepareGroupForAutoSortByDelay(subId) }
                    dataSource.prepareGroupForAutoSortByDelay("")
                } else {
                    dataSource.prepareGroupForAutoSortByDelay(groupId)
                }
                sortByTestResults(groupId)
            }
            cacheMutex.withLock { groupDataCache.clear() }
            reloadCurrentGroup()
        }
    }

    private fun sortByTestResults(groupId: String) {
        val subs = if (groupId.isEmpty()) dataSource.getSubsList() else listOf(groupId)
        subs.forEach { dataSource.sortByTestResultsForSub(it) }
    }

    // ---------- Bulk operations (kept as direct suspend/sync methods: callers show their own dialogs/toasts) ----------
    suspend fun updateConfigViaSubAll(): SubscriptionUpdateResult = withContext(ioDispatcher) {
        val groupId = uiState.value.selectedGroupId
        if (groupId.isEmpty()) {
            dataSource.updateConfigViaSubAll()
        } else {
            val item = dataSource.getSubscriptionItem(groupId) ?: return@withContext SubscriptionUpdateResult()
            dataSource.updateConfigViaSub(com.miku.ray.dto.entities.SubscriptionCache(groupId, item))
        }
    }

    fun exportAllServer(): Int {
        val groupId = uiState.value.selectedGroupId
        val guids = if (groupId.isEmpty() && keywordFilter.isEmpty()) {
            dataSource.getServerGuidList("")
        } else {
            mutableServerFlow(groupId).value.map { it.guid }
        }
        return dataSource.shareNonCustomConfigsToClipboard(guids)
    }

    fun removeAllServer(): Int {
        val groupId = uiState.value.selectedGroupId
        val count = if (groupId.isEmpty() && keywordFilter.isEmpty()) {
            dataSource.removeAllServer()
        } else {
            val pinned = dataSource.decodePinnedServers()
            val removable = mutableServerFlow(groupId).value.filterNot { pinned.contains(it.guid) }
            removable.forEach { dataSource.removeServer(it.guid) }
            removable.size
        }
        return count
    }

    fun removeDuplicateServer(): Int {
        val groupId = uiState.value.selectedGroupId
        val servers = mutableServerFlow(groupId).value
        val pinned = dataSource.decodePinnedServers()
        val seen = HashSet<ProfileItem>()
        val duplicates = mutableListOf<String>()
        servers.forEachIndexed { index, server ->
            val profile = server.profile
            if (profile.configType.isComplexType()) return@forEachIndexed
            servers.forEachIndexed inner@{ index2, server2 ->
                if (index2 <= index) return@inner
                val profile2 = server2.profile
                if (profile2.configType.isComplexType()) return@inner
                if (profile == profile2 && server2.guid !in duplicates && !pinned.contains(server2.guid)) {
                    duplicates += server2.guid
                }
            }
        }
        duplicates.forEach { dataSource.removeServer(it) }
        return duplicates.size
    }

    fun removeInvalidServer(): Int {
        val groupId = uiState.value.selectedGroupId
        return removeInvalidServerInternal(forGroupOnly = groupId.isNotEmpty() || keywordFilter.isNotBlank())
    }

    private fun removeInvalidServerInternal(forGroupOnly: Boolean): Int {
        val groupId = uiState.value.selectedGroupId
        return if (forGroupOnly) {
            mutableServerFlow(groupId).value.sumOf { dataSource.removeInvalidServerByGuid(it.guid) }
        } else {
            dataSource.removeInvalidServerByGuid("")
        }
    }

    // ---------- Traffic ----------
    private fun resetCurrentProfileTraffic() {
        dataSource.getSelectServer()?.let { guid ->
            dataSource.resetProfileTraffic(guid)
            emitEvent(MainViewModelEvent.ListChanged(guid))
        }
    }

    private fun resetGroupTraffic() {
        dataSource.resetGroupTraffic(uiState.value.selectedGroupId)
        emitEvent(MainViewModelEvent.ListChanged(null))
    }

    private fun resetAllTraffic() {
        dataSource.resetAllTraffic()
        emitEvent(MainViewModelEvent.ListChanged(null))
    }

    override fun onCleared() {
        setupGroupJob?.cancel()
        selectedGroupLoadJob?.cancel()
        dataSource.close()
        super.onCleared()
    }

    // ---------- Factory ----------
    class Factory(
        private val application: Application,
        private val dataSource: MainDataSource
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application, dataSource) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
