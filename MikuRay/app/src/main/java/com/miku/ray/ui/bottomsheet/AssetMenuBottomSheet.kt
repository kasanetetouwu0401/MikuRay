package com.miku.ray.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.UwuBottomSheetAssetMenuBinding
import com.miku.ray.handler.MmkvManager

class AssetMenuBottomSheet : BaseBottomSheetFragment() {

    private var _binding: UwuBottomSheetAssetMenuBinding? = null
    private val binding get() = requireNotNull(_binding)

    interface OnAssetMenuOptionClickListener {
        fun onAssetMenuOptionClicked(viewId: Int)
    }

    private var mListener: OnAssetMenuOptionClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnAssetMenuOptionClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnAssetMenuOptionClickListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = UwuBottomSheetAssetMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupParticles(binding.root)
        loadBannerSheet(binding.root)

        val clickListener = View.OnClickListener {
            mListener?.onAssetMenuOptionClicked(it.id)
            dismiss()
        }

        val actionViews = listOf(
            binding.addFile,
            binding.addUrl,
            binding.addQrcode
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
        const val TAG = "AssetMenuBottomSheet"
    }
}
