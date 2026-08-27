package com.miku.ray.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.miku.ray.databinding.UwuBottomSheetRoutingMenuBinding

class RoutingMenuBottomSheet : BaseBottomSheetFragment() {

    private var _binding: UwuBottomSheetRoutingMenuBinding? = null
    private val binding get() = requireNotNull(_binding)

    interface OnRoutingMenuOptionClickListener {
        fun onRoutingMenuOptionClicked(viewId: Int)
    }

    private var mListener: OnRoutingMenuOptionClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnRoutingMenuOptionClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnRoutingMenuOptionClickListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = UwuBottomSheetRoutingMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupParticles(binding.root)
        loadBannerSheet(binding.root)

        val clickListener = View.OnClickListener {
            mListener?.onRoutingMenuOptionClicked(it.id)
            dismiss()
        }

        val actionViews = listOf(
            binding.importPredefinedRulesets,
            binding.importRulesetsFromClipboard,
            binding.importRulesetsFromQrcode,
            binding.exportRulesetsToClipboard,
            binding.menuUserAssetSetting
        )

        actionViews.forEach { actionView ->
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
        const val TAG = "RoutingMenuBottomSheet"
    }
}
