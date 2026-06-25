package com.v2ray.ang.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.SimpleItemAnimator
import com.v2ray.ang.contracts.MainAdapterListener
import com.v2ray.ang.databinding.FragmentGroupServerBinding
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.viewmodel.MainViewModel

/**
 * Fragment read-only untuk [SwitchProfileActivity].
 *
 * Reuse [MainRecyclerAdapter] tapi semua action kecuali onSelectServer di-ignore.
 * Server yang dipilih di-callback ke activity via [Callback].
 */
class SwitchProfileGroupFragment : BaseFragment<FragmentGroupServerBinding>() {

    interface Callback {
        fun onServerSelected(guid: String)
    }

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: MainRecyclerAdapter
    private val subId: String by lazy { arguments?.getString(ARG_SUB_ID).orEmpty() }

    private val callback: Callback?
        get() = activity as? Callback

    companion object {
        private const val ARG_SUB_ID = "subscriptionId"

        fun newInstance(subId: String) = SwitchProfileGroupFragment().apply {
            arguments = Bundle().apply { putString(ARG_SUB_ID, subId) }
        }
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentGroupServerBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = MainRecyclerAdapter(mainViewModel, SwitchAdapterListener())

        binding.recyclerView.apply {
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(requireContext(), 1)
            adapter = this@SwitchProfileGroupFragment.adapter
            // reset padding — dialog card yang handle spacing
            setPadding(0, 0, 0, 0)
            clipToPadding = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val anim = binding.recyclerView.itemAnimator
        if (anim is SimpleItemAnimator) anim.supportsChangeAnimations = false

        // drag & swipe dimatikan di mode switch
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, 0) {
            override fun onMove(rv: androidx.recyclerview.widget.RecyclerView, vh: androidx.recyclerview.widget.RecyclerView.ViewHolder, t: androidx.recyclerview.widget.RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: androidx.recyclerview.widget.RecyclerView.ViewHolder, d: Int) {}
        }).attachToRecyclerView(binding.recyclerView)

        binding.refreshLayout.isEnabled = false

        mainViewModel.updateListAction.observe(viewLifecycleOwner) { index ->
            if (mainViewModel.subscriptionId != subId) return@observe
            adapter.setData(mainViewModel.serversCache, index)
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.subscriptionIdChanged(subId)
    }

    // ─────────────────────────────────────────────────────────────────────────

    private inner class SwitchAdapterListener : MainAdapterListener {
        override fun onSelectServer(guid: String) {
            callback?.onServerSelected(guid)
        }

        // semua action lain di-ignore di mode switch
        override fun onEdit(guid: String, position: Int) {}
        override fun onEdit(guid: String, position: Int, profile: ProfileItem) {}
        override fun onShare(url: String) {}
        override fun onShare(guid: String, profile: ProfileItem, position: Int, more: Boolean) {}
        override fun onRemove(guid: String, position: Int) {}
        override fun onRefreshData() {}
    }
}
