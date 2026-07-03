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
import com.v2ray.ang.databinding.ActivitySubSettingBinding
import com.v2ray.ang.databinding.ItemQrcodeBinding
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.extension.snackbarSuccess
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.ui.bottomsheet.ShareSubBottomSheet
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.showBlur
import com.v2ray.ang.util.showDeleteConfirmDialog
import com.v2ray.ang.util.showSubUpdateDiffDialog
import com.v2ray.ang.viewmodel.SubscriptionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SubSettingFragment : MikuFragment<ActivitySubSettingBinding>(), ShareSubBottomSheet.OnShareSubOptionClickListener {

    override val applyBottomInset: Boolean = true

    private val viewModel: SubscriptionsViewModel by viewModels()
    private lateinit var adapter: SubSettingRecyclerAdapter
    private var mItemTouchHelper: ItemTouchHelper? = null

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        ActivitySubSettingBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar(binding.toolbar, title = getString(R.string.title_sub_setting))
        setupMenu()

        adapter = SubSettingRecyclerAdapter(viewModel, FragmentAdapterListener())

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
        binding.toolbar.inflateMenu(R.menu.action_sub_setting)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.add_config -> {
                    startActivity(Intent(requireContext(), SubEditActivity::class.java))
                    true
                }
                R.id.sub_update -> {
                    (activity as? BaseActivity)?.showLoading()
                    lifecycleScope.launch(Dispatchers.IO) {
                        val result = AngConfigManager.updateConfigViaSubAll()
                        delay(500L)
                        launch(Dispatchers.Main) {
                            if (!isAdded) return@launch
                            if (result.successCount + result.failureCount + result.skipCount == 0) {
                                requireContext().toastSuccess(getString(R.string.title_update_subscription_no_subscription))
                            } else if (result.successCount > 0 && result.failureCount + result.skipCount == 0) {
                                requireContext().toastSuccess(getString(R.string.title_update_config_count, result.configCount))
                            } else {
                                requireContext().toastSuccess(
                                    getString(
                                        R.string.title_update_subscription_result,
                                        result.configCount, result.successCount, result.failureCount, result.skipCount
                                    )
                                )
                            }
                            if (result.addedProfiles.isNotEmpty() || result.deletedProfiles.isNotEmpty()) {
                                showSubUpdateDiffDialog(requireContext(), result)
                            }
                            (activity as? BaseActivity)?.hideLoading()
                            refreshData()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        viewModel.reload()
        adapter.notifyDataSetChanged()
    }

    override fun onShareSubOptionClicked(optionId: Int, url: String) {
        try {
            when (optionId) {
                R.id.share_qrcode -> {
                    val ivBinding = ItemQrcodeBinding.inflate(LayoutInflater.from(requireContext()))
                    ivBinding.ivQcode.setImageBitmap(
                        QRCodeDecoder.createQRCode(url)
                    )
                    AlertDialog.Builder(requireContext()).setView(ivBinding.root).showBlur()
                }
                R.id.share_clipboard -> {
                    Utils.setClipboard(requireContext(), url)
                    requireContext().snackbarSuccess(
                        getString(R.string.menu_item_export_proxy_app),
                        title = getString(R.string.title_alerter_success)
                    )
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Share subscription failed", e)
            requireContext().snackbarError(
                getString(R.string.menu_item_export_proxy_app),
                title = getString(R.string.title_alerter_error)
            )
        }
    }

    private inner class FragmentAdapterListener : BaseAdapterListener {
        override fun onEdit(guid: String, position: Int) {
            startActivity(
                Intent(requireContext(), SubEditActivity::class.java)
                    .putExtra("subId", guid)
            )
        }

        override fun onRemove(guid: String, position: Int) {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
                showDeleteConfirmDialog(context = requireContext(), messageRes = R.string.del_sub_dialog_comfirm_message) {
                    viewModel.remove(guid)
                    refreshData()
                }
            } else {
                viewModel.remove(guid)
                refreshData()
            }
        }

        override fun onShare(url: String) {
            val bottomSheet = ShareSubBottomSheet.newInstance(url)
            bottomSheet.show(childFragmentManager, ShareSubBottomSheet.TAG)
        }

        override fun onRefreshData() {
            refreshData()
        }
    }
}
