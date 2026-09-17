package com.miku.ray.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.remixicon.R as RemixR
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.WindowBlurUtils

class RoutingMenuBottomSheet : BaseBottomSheetFragment() {

    interface OnRoutingMenuOptionClickListener {
        fun onRoutingMenuOptionClicked(viewId: Int)
    }

    private var mListener: OnRoutingMenuOptionClickListener? = null
    private var tvGeoFilesSourcesSummary: TextView? = null
    private var tvRoutingDomainStrategySummary: TextView? = null

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

        tvGeoFilesSourcesSummary = view.findViewById(R.id.tv_geo_files_sources_summary)
        tvRoutingDomainStrategySummary = view.findViewById(R.id.tv_routing_domain_strategy_summary)
        refreshGeoFilesSourcesSummary()
        refreshRoutingDomainStrategySummary()

        view.findViewById<View>(R.id.pref_geo_files_sources)?.setOnClickListener {
            showGeoFilesSourcesDialog()
        }
        view.findViewById<View>(R.id.pref_routing_domain_strategy)?.setOnClickListener {
            showRoutingDomainStrategyDialog()
        }
    }

    private fun refreshGeoFilesSourcesSummary() {
        tvGeoFilesSourcesSummary?.text = MmkvManager.decodeSettingsString(
            AppConfig.PREF_GEO_FILES_SOURCES,
            AppConfig.GEO_FILES_SOURCES.first()
        )
    }

    private fun refreshRoutingDomainStrategySummary() {
        tvRoutingDomainStrategySummary?.text = MmkvManager.decodeSettingsString(
            AppConfig.PREF_ROUTING_DOMAIN_STRATEGY,
            ROUTING_DOMAIN_STRATEGY_DEFAULT
        )
    }

    private fun showGeoFilesSourcesDialog() {
        val context = context ?: return
        val entries = resources.getStringArray(R.array.geo_files_sources_entries)
        val values = resources.getStringArray(R.array.geo_files_sources_values)
        val current = MmkvManager.decodeSettingsString(
            AppConfig.PREF_GEO_FILES_SOURCES,
            AppConfig.GEO_FILES_SOURCES.first()
        )
        val checkedItem = values.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(context)
            .setIcon(RemixR.drawable.rmx_download_cloud_2_line)
            .setTitle(R.string.asset_geo_files_sources)
            .setSingleChoiceItems(entries, checkedItem) { dialog, which ->
                MmkvManager.encodeSettings(AppConfig.PREF_GEO_FILES_SOURCES, values[which])
                refreshGeoFilesSourcesSummary()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            
             WindowBlurUtils.applyWindowBlur(dialog.window)
            .show()
    }

    private fun showRoutingDomainStrategyDialog() {
        val context = context ?: return
        val entries = resources.getStringArray(R.array.routing_domain_strategy)
        val current = MmkvManager.decodeSettingsString(
            AppConfig.PREF_ROUTING_DOMAIN_STRATEGY,
            ROUTING_DOMAIN_STRATEGY_DEFAULT
        )
        val checkedItem = entries.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(context)
            .setIcon(RemixR.drawable.rmx_git_branch_line)
            .setTitle(R.string.routing_settings_domain_strategy)
            .setSingleChoiceItems(entries, checkedItem) { dialog, which ->
                MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, entries[which])
                refreshRoutingDomainStrategySummary()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            
             WindowBlurUtils.applyWindowBlur(dialog.window)
            .show()
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
        tvGeoFilesSourcesSummary = null
        tvRoutingDomainStrategySummary = null
    }

    companion object {
        const val TAG = "RoutingMenuBottomSheet"
        private const val ROUTING_DOMAIN_STRATEGY_DEFAULT = "AsIs"
    }
}
