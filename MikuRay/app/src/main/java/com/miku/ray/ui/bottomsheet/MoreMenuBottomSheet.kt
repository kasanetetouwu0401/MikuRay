package com.miku.ray.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.UwuBottomSheetMoreMenuBinding
import com.miku.ray.handler.MmkvManager

class MoreMenuBottomSheet : BaseBottomSheetFragment() {

    private var _binding: UwuBottomSheetMoreMenuBinding? = null
    private val binding get() = requireNotNull(_binding)

    interface OnMoreOptionClickListener {
        fun onMoreOptionClicked(viewId: Int)
    }

    private var mListener: OnMoreOptionClickListener? = null

    private var currentOrder: Int = ORDER_ORIGIN
    private var subscriptionId: String = ""

    private fun orderKey(): String {
        val subId = subscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
        return "${AppConfig.PREF_SERVER_ORDER}_$subId"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnMoreOptionClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnMoreOptionClickListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subscriptionId = arguments?.getString(ARG_SUBSCRIPTION_ID).orEmpty()
        currentOrder = MmkvManager.decodeSettingsInt(orderKey(), ORDER_ORIGIN)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = UwuBottomSheetMoreMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupParticles(binding.root)
        loadBannerSheet(binding.root)

        val checkOrigin = binding.actionOrderOrigin
        val checkName   = binding.actionOrderByName
        val checkDelay  = binding.actionOrderByDelay

        fun updateChecks(order: Int) {
            checkOrigin?.isChecked = order == ORDER_ORIGIN
            checkName?.isChecked   = order == ORDER_BY_NAME
            checkDelay?.isChecked  = order == ORDER_BY_DELAY
        }
        updateChecks(currentOrder)

        val isTrafficEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_TRAFFIC_ENABLED) == true
        binding.resetTraffic.visibility = if (isTrafficEnabled) View.VISIBLE else View.GONE

        val hasTestResults = MmkvManager.hasAnyTestDelayResults()
        binding.clearTestResults.visibility = if (hasTestResults) View.VISIBLE else View.GONE
        val hasCountryCodes = MmkvManager.hasAnyCountryCodeResults()
        binding.clearCountryCodes.visibility = if (hasCountryCodes) View.VISIBLE else View.GONE

        val isScrollButtonsHidden = MmkvManager.decodeSettingsBool(AppConfig.PREF_HIDE_SCROLL_BUTTONS, false)
        val hasSelectedServer = !MmkvManager.getSelectServer().isNullOrEmpty()
        binding.actionScrollToSelected.visibility =
            if (isScrollButtonsHidden && hasSelectedServer) View.VISIBLE else View.GONE

        binding.cardOrderOrigin.setOnClickListener {
            binding.actionOrderOrigin.performClick()
        }
        binding.cardOrderByName.setOnClickListener {
            binding.actionOrderByName.performClick()
        }
        binding.cardOrderByDelay.setOnClickListener {
            binding.actionOrderByDelay.performClick()
        }

        val clickListener = View.OnClickListener { v ->
            mListener?.onMoreOptionClicked(v.id)
            dismiss()
        }

        val orderClickListener = View.OnClickListener { v ->
            val newOrder = when (v.id) {
                R.id.action_order_origin   -> ORDER_ORIGIN
                R.id.action_order_by_name  -> ORDER_BY_NAME
                R.id.action_order_by_delay -> ORDER_BY_DELAY
                else -> currentOrder
            }
            MmkvManager.encodeSettings(orderKey(), newOrder)
            currentOrder = newOrder
            updateChecks(newOrder)
            mListener?.onMoreOptionClicked(v.id)
            dismiss()
        }

        listOf(
            binding.actionOrderOrigin,
            binding.actionOrderByName,
            binding.actionOrderByDelay
        ).forEach { actionView ->
            actionView.setOnClickListener(orderClickListener)
        }

        listOf(
            binding.serviceRestart,
            binding.delAllConfig,
            binding.delDuplicateConfig,
            binding.delInvalidConfig,
            binding.exportAll,
            binding.exportGroupFile,
            binding.realPingAll,
            binding.countryCodeAll,
            binding.tcpingAll,
            binding.clearTestResults,
            binding.clearCountryCodes,
            binding.subUpdate,
            binding.resetTraffic,
            binding.actionScrollToSelected
        ).forEach { actionView ->
            actionView.setOnClickListener(clickListener)
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
        const val TAG = "MoreMenuBottomSheet"

        const val ORDER_ORIGIN   = 0
        const val ORDER_BY_NAME  = 1
        const val ORDER_BY_DELAY = 2

        private const val ARG_SUBSCRIPTION_ID = "subscriptionId"

        fun newInstance(subscriptionId: String): MoreMenuBottomSheet {
            return MoreMenuBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SUBSCRIPTION_ID, subscriptionId)
                }
            }
        }
    }
}
