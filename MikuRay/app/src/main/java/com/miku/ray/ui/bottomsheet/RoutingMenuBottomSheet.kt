package com.miku.ray.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.ui.preference.MaterialSectionHelper

class RoutingMenuBottomSheet : BaseBottomSheetFragment() {

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
    ): View? {
        return inflater.inflate(R.layout.uwu_bottom_sheet_routing_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupParticles(view)
        loadBannerSheet(view)

        val clickListener = View.OnClickListener {
            mListener?.onRoutingMenuOptionClicked(it.id)
            dismiss()
        }

        val actionIds = listOf(
            R.id.import_predefined_rulesets,
            R.id.import_rulesets_from_clipboard,
            R.id.import_rulesets_from_qrcode,
            R.id.export_rulesets_to_clipboard,
            R.id.menu_user_asset_setting
        )

        actionIds.forEach { id ->
            view.findViewById<View>(id)?.setOnClickListener(clickListener)
        }

        // menu_user_asset_setting / export_rulesets_to_clipboard are the only two cards that
        // form a top/bottom section here - the three import_* rows above them are plain rows,
        // not part of the uwu card chain.
        MaterialSectionHelper.applyToCards(
            view,
            listOf(R.id.menu_user_asset_setting, R.id.export_rulesets_to_clipboard)
        )
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        const val TAG = "RoutingMenuBottomSheet"
    }
}
