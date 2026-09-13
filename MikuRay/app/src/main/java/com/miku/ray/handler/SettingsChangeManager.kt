package com.miku.ray.handler

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object SettingsChangeManager {
    val uiCustomizationState: StateFlow<UiCustomizationState> = UiCustomizationStateStore.state
    val uiCustomizationEvents: SharedFlow<Unit> = UiCustomizationStateStore.events

    private val _settingsVersion = MutableStateFlow(0L)
    val settingsVersion: StateFlow<Long> = _settingsVersion.asStateFlow()

    private val _settingsChanged = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val settingsChanged: SharedFlow<Unit> = _settingsChanged.asSharedFlow()

    private val _restartService = MutableStateFlow(false)
    private val _setupGroupTab = MutableStateFlow(false)
    private val _refreshDisplayPrefs = MutableStateFlow(false)

    fun notifySettingsChanged() {
        _settingsVersion.value++
        _settingsChanged.tryEmit(Unit)
    }

    fun makeRestartService() {
        _restartService.value = true
    }

    fun consumeRestartService(): Boolean {
        val value = _restartService.value
        _restartService.value = false
        return value
    }

    fun makeSetupGroupTab() {
        _setupGroupTab.value = true
    }

    fun consumeSetupGroupTab(): Boolean {
        val value = _setupGroupTab.value
        _setupGroupTab.value = false
        return value
    }

    fun makeRefreshDisplayPrefs() {
        _refreshDisplayPrefs.value = true
    }

    fun consumeRefreshDisplayPrefs(): Boolean {
        val value = _refreshDisplayPrefs.value
        _refreshDisplayPrefs.value = false
        return value
    }
}
