package com.miku.ray.ui.dialog

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.handler.UiCustomizationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

abstract class UiCustomizationPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {
    private var stateJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    protected var customizationState: UiCustomizationState = SettingsChangeManager.uiCustomizationState.value
        private set

    override fun onAttached() {
        super.onAttached()
        stateJob?.cancel()
        stateJob = scope.launch {
            SettingsChangeManager.uiCustomizationState.collect { state ->
                customizationState = state
                onCustomizationStateChanged(state)
            }
        }
    }

    override fun onDetached() {
        stateJob?.cancel()
        stateJob = null
        super.onDetached()
    }

    protected open fun onCustomizationStateChanged(state: UiCustomizationState) = Unit
}
