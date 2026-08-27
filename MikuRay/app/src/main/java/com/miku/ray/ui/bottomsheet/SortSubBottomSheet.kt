package com.miku.ray.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.UwuBottomSheetSortSubBinding
import com.miku.ray.handler.MmkvManager

class SortSubBottomSheet : BaseBottomSheetFragment() {

    private var _binding: UwuBottomSheetSortSubBinding? = null
    private val binding get() = requireNotNull(_binding)

    interface OnSortSubOptionClickListener {
        fun onSortSubOptionClicked(order: Int)
    }

    private var mListener: OnSortSubOptionClickListener? = null
    private var currentOrder: Int = ORDER_ORIGIN

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnSortSubOptionClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnSortSubOptionClickListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentOrder = MmkvManager.decodeSettingsInt(AppConfig.PREF_SUB_SORT_ORDER, ORDER_ORIGIN)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = UwuBottomSheetSortSubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupParticles(binding.root)
        loadBannerSheet(binding.root)

        val checkOrigin  = binding.actionSortSubOrigin
        val checkAdded   = binding.actionSortSubAdded
        val checkUpdated = binding.actionSortSubUpdated

        fun updateChecks(order: Int) {
            checkOrigin?.isChecked  = order == ORDER_ORIGIN
            checkAdded?.isChecked   = order == ORDER_BY_ADDED
            checkUpdated?.isChecked = order == ORDER_BY_UPDATED
        }
        updateChecks(currentOrder)

        binding.cardSortSubOrigin.setOnClickListener {
            binding.actionSortSubOrigin.performClick()
        }
        binding.cardSortSubAdded.setOnClickListener {
            binding.actionSortSubAdded.performClick()
        }
        binding.cardSortSubUpdated.setOnClickListener {
            binding.actionSortSubUpdated.performClick()
        }

        val orderClickListener = View.OnClickListener { v ->
            val newOrder = when (v.id) {
                R.id.action_sort_sub_origin  -> ORDER_ORIGIN
                R.id.action_sort_sub_added   -> ORDER_BY_ADDED
                R.id.action_sort_sub_updated -> ORDER_BY_UPDATED
                else -> currentOrder
            }
            MmkvManager.encodeSettings(AppConfig.PREF_SUB_SORT_ORDER, newOrder)
            currentOrder = newOrder
            updateChecks(newOrder)
            mListener?.onSortSubOptionClicked(newOrder)
            dismiss()
        }

        listOf(
            binding.actionSortSubOrigin,
            binding.actionSortSubAdded,
            binding.actionSortSubUpdated
        ).forEach { actionView ->
            actionView.setOnClickListener(orderClickListener)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        const val TAG = "SortSubBottomSheet"

        const val ORDER_ORIGIN     = 0
        const val ORDER_BY_ADDED   = 1
        const val ORDER_BY_UPDATED = 2

        fun newInstance(): SortSubBottomSheet {
            return SortSubBottomSheet()
        }

        /**
         * Applies the persisted PREF_SUB_SORT_ORDER to a list of subscriptions,
         * without mutating the input list. Shared by SubSettingActivity and
         * MainActivity so both stay in sync.
         */
        fun <T> sorted(list: List<T>, addedTime: (T) -> Long, lastUpdated: (T) -> Long): List<T> {
            val order = MmkvManager.decodeSettingsInt(AppConfig.PREF_SUB_SORT_ORDER, ORDER_ORIGIN)
            return when (order) {
                ORDER_BY_ADDED   -> list.sortedByDescending(addedTime)
                ORDER_BY_UPDATED -> list.sortedByDescending(lastUpdated)
                else             -> list
            }
        }
    }
}
