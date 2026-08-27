package com.miku.ray.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.miku.ray.databinding.UwuLayoutBottomSheetMainMenuBinding

class MainMenuBottomSheet : BaseBottomSheetFragment() {

    private var _binding: UwuLayoutBottomSheetMainMenuBinding? = null
    private val binding get() = requireNotNull(_binding)

    interface OnOptionClickListener {
        fun onOptionClicked(viewId: Int)
    }

    private var mListener: OnOptionClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnOptionClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnOptionClickListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = UwuLayoutBottomSheetMainMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupParticles(binding.root)
        loadBannerSheet(binding.root)

        val clickListener = View.OnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }

        val actionViews = listOf(
            binding.menuSubSetting,
            binding.menuRoutingSetting,
            binding.menuSettings,
            binding.menuLogcat,
            binding.menuBackupRestore,
            binding.menuAbout
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
        const val TAG = "MainMenuBottomSheet"
    }
}
