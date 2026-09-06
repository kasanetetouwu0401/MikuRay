package com.miku.ray.ui.base

/**
 * Base interface for one-shot ViewModel UI events (things that should be
 * consumed exactly once by the Activity/Fragment, unlike continuously
 * observable state which belongs in a StateFlow).
 */
interface ViewModelEvent

/**
 * Common one-shot UI events shared by all ViewModels.
 */
interface BaseViewModelEvent : ViewModelEvent {
    object FinishActivity : BaseViewModelEvent
}
