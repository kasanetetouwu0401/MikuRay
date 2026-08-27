package com.miku.ray.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.UwuLayoutBottomSheetAddConfigBinding
import com.miku.ray.handler.MmkvManager

class AddConfigBottomSheet : BaseBottomSheetFragment() {

    private var _binding: UwuLayoutBottomSheetAddConfigBinding? = null
    private val binding get() = requireNotNull(_binding)

    interface OnAddConfigClickListener {
        fun onAddConfigOptionClicked(viewId: Int)
    }

    private var mListener: OnAddConfigClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnAddConfigClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnAddConfigClickListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = UwuLayoutBottomSheetAddConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupParticles(binding.root)
        loadBannerSheet(binding.root)

        val clickListener = View.OnClickListener {
            mListener?.onAddConfigOptionClicked(it.id)
            dismiss()
        }

        val actionViews = listOf(
            binding.importQrcode,
            binding.importClipboard,
            binding.importLocal,
            binding.importManuallyPolicyGroup,
            binding.importManuallyProxyChain,
            binding.importManuallyVmess,
            binding.importManuallyVless,
            binding.importManuallySs,
            binding.importManuallySocks,
            binding.importManuallyHttp,
            binding.importManuallyTrojan,
            binding.importManuallyWireguard,
            binding.importManuallyHysteria2
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
        const val TAG = "AddConfigBottomSheet"
    }
}
