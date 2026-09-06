package com.miku.ray.ui.main

import com.miku.ray.dto.GroupMapItem
import com.miku.ray.dto.TestProgressInfo
import com.miku.ray.ui.base.ViewModelEvent

/**
 * Main UI state, continuously observable via [MainViewModel.uiState].
 */
data class MainUiState(
    val groups: List<GroupMapItem> = emptyList(),
    val selectedGroupId: String = "",
    val selectedGuid: String? = null,
    val isRunning: Boolean = false,
    val isTesting: Boolean = false,
    val isTestingCountryCode: Boolean = false,
    val testProgress: TestProgressInfo? = null,
    val countryCodeProgress: TestProgressInfo? = null,
    val ipResult: String? = null,
    val trafficSpeedText: String = ""
)

/**
 * One-shot UI events raised by [MainViewModel], delivered through the shared
 * BaseViewModel.viewModelEvent channel so they are only handled once (no
 * replay on config change), unlike the continuously observable [MainUiState].
 */
sealed interface MainViewModelEvent : ViewModelEvent {
    /** A server row changed; null guid means "refresh the whole visible list". */
    data class ListChanged(val guid: String?) : MainViewModelEvent
    data class Alert(val isSuccess: Boolean, val message: String) : MainViewModelEvent
    data object ServiceRestart : MainViewModelEvent
    data object GroupBadgeChanged : MainViewModelEvent
    data object GroupOrderChanged : MainViewModelEvent
    data class TestResultText(val text: String) : MainViewModelEvent
}

/**
 * All possible fire-and-forget user interaction intents. Interactions that
 * need a synchronous return value (togglePinServer, beginServerRestart,
 * getPosition, ...) stay as regular public methods on [MainViewModel],
 * mirroring how v2rayNG's own MainViewModel keeps some calls outside
 * onAction() too.
 */
sealed interface MainAction {
    data object Initialize : MainAction
    data class SelectGroup(val groupId: String) : MainAction
    data class RemoveServer(val guid: String) : MainAction
    data object ReloadServerList : MainAction
    data class FilterConfig(val keyword: String) : MainAction

    data class TestAllRealPing(val onlyTcp: Boolean = false) : MainAction
    data object TestAllCountryCodes : MainAction
    data object CancelRealPingTest : MainAction
    data object CancelCountryCodeTest : MainAction
    data object ClearTestResults : MainAction
    data object ClearTestResultsForGroup : MainAction
    data object ClearCountryCodes : MainAction
    data object ClearCountryCodesForGroup : MainAction

    data object ResetCurrentProfileTraffic : MainAction
    data object ResetGroupTraffic : MainAction
    data object ResetAllTraffic : MainAction

    data object ResyncState : MainAction
    data object FetchCurrentIp : MainAction
}
