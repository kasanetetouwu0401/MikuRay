package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.BaseAdapterListener
import com.v2ray.ang.databinding.ActivityRoutingSettingBinding
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.extension.snackbarSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.ui.bottomsheet.RoutingMenuBottomSheet
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.showBlur
import com.v2ray.ang.viewmodel.RoutingSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoutingSettingFragment : HelperMikuFragment<ActivityRoutingSettingBinding>(),
    RoutingMenuBottomSheet.OnRoutingMenuOptionClickListener {

    override val applyBottomInset: Boolean = true

    private val viewModel: RoutingSettingsViewModel by viewModels()
    private lateinit var adapter: RoutingSettingRecyclerAdapter
    private var mItemTouchHelper: ItemTouchHelper? = null

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        ActivityRoutingSettingBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar(binding.toolbar, title = getString(R.string.routing_settings_title))
        setupMenu()

        adapter = RoutingSettingRecyclerAdapter(viewModel, FragmentAdapterListener())

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        mItemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(adapter))
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    private fun setupMenu() {
        binding.toolbar.inflateMenu(R.menu.menu_routing_setting)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.add_rule -> {
                    startActivity(Intent(requireContext(), RoutingEditActivity::class.java))
                    true
                }
                R.id.action_more_menu -> {
                    val bottomSheet = RoutingMenuBottomSheet()
                    bottomSheet.show(childFragmentManager, RoutingMenuBottomSheet.TAG)
                    true
                }
                else -> false
            }
        }
    }

    override fun onRoutingMenuOptionClicked(viewId: Int) {
        when (viewId) {
            R.id.import_predefined_rulesets -> importPredefined()
            R.id.import_rulesets_from_clipboard -> importFromClipboard()
            R.id.import_rulesets_from_qrcode -> importQRcode()
            R.id.export_rulesets_to_clipboard -> export2Clipboard()
            R.id.menu_user_asset_setting -> startActivity(Intent(requireContext(), UserAssetActivity::class.java))
        }
    }

    private fun importPredefined() {
        AlertDialog.Builder(requireContext()).setItems(resources.getStringArray(R.array.preset_rulesets)) { _, i ->
            AlertDialog.Builder(requireContext()).setMessage(R.string.routing_settings_import_rulesets_tip)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    try {
                        lifecycleScope.launch(Dispatchers.IO) {
                            SettingsManager.resetRoutingRulesetsFromPresets(requireContext(), i)
                            launch(Dispatchers.Main) {
                                refreshData()
                                requireContext().snackbarSuccess(
                                    getString(R.string.routing_settings_import_predefined_rulesets),
                                    title = getString(R.string.title_alerter_success)
                                )
                            }
                        }
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Failed to import predefined ruleset", e)
                    }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    //do nothing
                }
                .showBlur()
        }.showBlur()
    }

    private fun importFromClipboard() {
        AlertDialog.Builder(requireContext()).setMessage(R.string.routing_settings_import_rulesets_tip)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val clipboard = try {
                    Utils.getClipboard(requireContext())
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to get clipboard content", e)
                    requireContext().snackbarError(
                        getString(R.string.routing_settings_import_rulesets_from_clipboard),
                        title = getString(R.string.title_alerter_error)
                    )
                    return@setPositiveButton
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = SettingsManager.resetRoutingRulesets(clipboard)
                    withContext(Dispatchers.Main) {
                        if (result) {
                            refreshData()
                            requireContext().snackbarSuccess(
                                getString(R.string.routing_settings_import_rulesets_from_clipboard),
                                title = getString(R.string.title_alerter_success)
                            )
                        } else {
                            requireContext().snackbarError(
                                getString(R.string.routing_settings_import_rulesets_from_clipboard),
                                title = getString(R.string.title_alerter_error)
                            )
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do nothing
            }
            .showBlur()
    }

    private fun importQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                importRulesetsFromQRcode(scanResult)
            }
        }
        return true
    }

    private fun export2Clipboard() {
        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) {
            requireContext().snackbarError(
                getString(R.string.routing_settings_export_rulesets_to_clipboard),
                title = getString(R.string.title_alerter_error)
            )
        } else {
            Utils.setClipboard(requireContext(), JsonUtil.toJson(rulesetList))
            requireContext().snackbarSuccess(
                getString(R.string.routing_settings_export_rulesets_to_clipboard),
                title = getString(R.string.title_alerter_success)
            )
        }
    }

    private fun importRulesetsFromQRcode(qrcode: String?): Boolean {
        AlertDialog.Builder(requireContext()).setMessage(R.string.routing_settings_import_rulesets_tip)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = SettingsManager.resetRoutingRulesets(qrcode)
                    withContext(Dispatchers.Main) {
                        if (result) {
                            refreshData()
                            requireContext().snackbarSuccess(
                                getString(R.string.routing_settings_import_rulesets_from_qrcode),
                                title = getString(R.string.title_alerter_success)
                            )
                        } else {
                            requireContext().snackbarError(
                                getString(R.string.routing_settings_import_rulesets_from_qrcode),
                                title = getString(R.string.title_alerter_error)
                            )
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do nothing
            }
            .showBlur()
        return true
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        viewModel.reload()
        adapter.notifyDataSetChanged()
    }

    private inner class FragmentAdapterListener : BaseAdapterListener {
        override fun onEdit(guid: String, position: Int) {
            startActivity(
                Intent(requireContext(), RoutingEditActivity::class.java)
                    .putExtra("position", position)
            )
        }

        override fun onRemove(guid: String, position: Int) {
        }

        override fun onShare(url: String) {
        }

        override fun onRefreshData() {
            refreshData()
        }
    }
}
