package com.miku.ray.ui.routing

import android.app.Application
import com.miku.ray.dto.entities.RulesetItem
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RoutingSettingsViewModel(application: Application) : BaseViewModel(application) {
    private val rulesets: MutableList<RulesetItem> = mutableListOf()

    private val _rulesetsFlow = MutableStateFlow<List<RulesetItem>>(emptyList())
    val rulesetsFlow: StateFlow<List<RulesetItem>> = _rulesetsFlow.asStateFlow()

    fun getAll(): List<RulesetItem> = rulesets.toList()

    fun reload() {
        val loaded = MmkvManager.decodeRoutingRulesets() ?: mutableListOf()
        // Repair legacy/imported IDs in one write before exposing them as RecyclerView keys.
        if (SettingsManager.ensureRoutingRulesetIds(loaded)) {
            MmkvManager.encodeRoutingRulesets(loaded)
        }
        rulesets.clear()
        rulesets.addAll(loaded)
        _rulesetsFlow.value = rulesets.toList()
    }

    fun update(position: Int, item: RulesetItem) {
        if (position !in rulesets.indices) return
        rulesets[position] = item
        SettingsManager.saveRoutingRuleset(position, item)
        _rulesetsFlow.value = rulesets.toList()
    }

    fun remove(position: Int) {
        if (position !in rulesets.indices) return
        rulesets.removeAt(position)
        SettingsManager.removeRoutingRuleset(position)
        _rulesetsFlow.value = rulesets.toList()
    }

    fun swap(fromPosition: Int, toPosition: Int) {
        if (fromPosition in rulesets.indices && toPosition in rulesets.indices) {
            val item = rulesets.removeAt(fromPosition)
            rulesets.add(toPosition, item)
            SettingsManager.swapRoutingRuleset(fromPosition, toPosition)
            _rulesetsFlow.value = rulesets.toList()
        }
    }
}
