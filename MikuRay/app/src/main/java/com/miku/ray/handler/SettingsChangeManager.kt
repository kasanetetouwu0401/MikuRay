package com.miku.ray.handler

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single bus for all settings / UI side-effects.
 * Includes heavy theme recreate (replaces ThemeStateManager).
 */
object SettingsChangeManager {

    enum class Event {
        RestartService,
        SetupGroupTab,
        RefreshDisplayPrefs,
        LightUiRefresh,
        /** Theme / font / DPI / true-black — activities should recreate. */
        HeavyThemeRecreate,
    }

    fun interface LightUiListener {
        fun onLightUiRefresh()
    }

    private val _events = MutableSharedFlow<Event>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val _displayPrefsVersion = MutableStateFlow(0L)
    val displayPrefsVersion: StateFlow<Long> = _displayPrefsVersion.asStateFlow()

    private val _groupTabVersion = MutableStateFlow(0L)
    val groupTabVersion: StateFlow<Long> = _groupTabVersion.asStateFlow()

    /** Bumped on heavy theme change; each Activity tracks last applied value. */
    private val _heavyThemeVersion = MutableStateFlow(0L)
    val heavyThemeVersion: StateFlow<Long> = _heavyThemeVersion.asStateFlow()

    private val pendingRestart = AtomicBoolean(false)
    private val pendingSetupGroupTab = AtomicBoolean(false)
    private val pendingRefreshDisplayPrefs = AtomicBoolean(false)
    private val pendingLightUi = AtomicBoolean(false)

    private val lightUiListeners = CopyOnWriteArrayList<WeakReference<LightUiListener>>()

    fun registerLightUiListener(listener: LightUiListener) {
        lightUiListeners.removeAll { it.get() == null }
        if (lightUiListeners.none { it.get() === listener }) {
            lightUiListeners.add(WeakReference(listener))
        }
    }

    fun unregisterLightUiListener(listener: LightUiListener) {
        lightUiListeners.removeAll { ref ->
            val v = ref.get()
            v == null || v === listener
        }
    }

    fun makeRestartService() {
        pendingRestart.set(true)
        _events.tryEmit(Event.RestartService)
    }

    fun consumeRestartService(): Boolean = pendingRestart.getAndSet(false)

    fun makeSetupGroupTab() {
        pendingSetupGroupTab.set(true)
        _groupTabVersion.value = _groupTabVersion.value + 1
        _events.tryEmit(Event.SetupGroupTab)
    }

    fun consumeSetupGroupTab(): Boolean = pendingSetupGroupTab.getAndSet(false)

    fun makeRefreshDisplayPrefs() {
        pendingRefreshDisplayPrefs.set(true)
        _displayPrefsVersion.value = _displayPrefsVersion.value + 1
        _events.tryEmit(Event.RefreshDisplayPrefs)
        notifyLightUiListeners()
    }

    fun consumeRefreshDisplayPrefs(): Boolean = pendingRefreshDisplayPrefs.getAndSet(false)

    fun makeLightUiRefresh() {
        pendingLightUi.set(true)
        _displayPrefsVersion.value = _displayPrefsVersion.value + 1
        _events.tryEmit(Event.LightUiRefresh)
        notifyLightUiListeners()
    }

    fun consumeLightUiRefresh(): Boolean = pendingLightUi.getAndSet(false)

    /**
     * Heavy theme/font/DPI change. Activities observe [heavyThemeVersion] / [Event.HeavyThemeRecreate]
     * and call [android.app.Activity.recreate].
     */
    fun makeHeavyThemeRecreate() {
        _heavyThemeVersion.value = _heavyThemeVersion.value + 1
        _events.tryEmit(Event.HeavyThemeRecreate)
    }

    fun notifySelectedBannerChanged() = makeLightUiRefresh()
    fun notifyHomeBannerChanged() = makeLightUiRefresh()
    fun notifyHeaderPaddingChanged() = makeLightUiRefresh()
    fun notifyParticlesChanged() = makeLightUiRefresh()

    fun sendLegacyBroadcast(context: Context, action: String) {
        runCatching {
            context.applicationContext.sendBroadcast(Intent(action))
        }
    }

    private fun notifyLightUiListeners() {
        val dead = mutableListOf<WeakReference<LightUiListener>>()
        for (ref in lightUiListeners) {
            val listener = ref.get()
            if (listener == null) {
                dead.add(ref)
            } else {
                runCatching { listener.onLightUiRefresh() }
            }
        }
        if (dead.isNotEmpty()) lightUiListeners.removeAll(dead.toSet())
    }
}
