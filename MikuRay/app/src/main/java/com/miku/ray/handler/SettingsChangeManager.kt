package com.miku.ray.handler

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Central hub for settings / UI-customisation change signals.
 * Uses StateFlow + SharedFlow only — no preference keys live here.
 *
 * - StateFlow flags are consumed once (edge-triggered).
 * - SharedFlow events are observed continuously by UI that can refresh in-place.
 */
object SettingsChangeManager {

    // ── one-shot flags (consumed by MainActivity / hosts) ──────────────────

    private val _restartService = MutableStateFlow(false)
    private val _setupGroupTab = MutableStateFlow(false)
    private val _refreshDisplayPrefs = MutableStateFlow(false)

    // ── continuous UI signals ──────────────────────────────────────────────

    /** Emitted when any UI customisation that does NOT require Activity.recreate() changed. */
    private val _uiCustomisation = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val uiCustomisation: SharedFlow<Unit> = _uiCustomisation.asSharedFlow()

    /** Emitted when a change requires Activity.recreate() (theme / dpi / font scale / language …). */
    private val _needsRecreate = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val needsRecreate: SharedFlow<Unit> = _needsRecreate.asSharedFlow()

    // ── producers ──────────────────────────────────────────────────────────

    fun makeRestartService() {
        _restartService.value = true
    }

    fun consumeRestartService(): Boolean {
        val v = _restartService.value
        _restartService.value = false
        return v
    }

    fun makeSetupGroupTab() {
        _setupGroupTab.value = true
    }

    fun consumeSetupGroupTab(): Boolean {
        val v = _setupGroupTab.value
        _setupGroupTab.value = false
        return v
    }

    fun makeRefreshDisplayPrefs() {
        _refreshDisplayPrefs.value = true
    }

    fun consumeRefreshDisplayPrefs(): Boolean {
        val v = _refreshDisplayPrefs.value
        _refreshDisplayPrefs.value = false
        return v
    }

    /** Notify listeners that UI customisation changed — no recreate needed. */
    fun notifyUiCustomisationChanged() {
        _uiCustomisation.tryEmit(Unit)
    }

    /** Notify that a full Activity.recreate() is required. */
    fun notifyNeedsRecreate() {
        _needsRecreate.tryEmit(Unit)
    }
}
