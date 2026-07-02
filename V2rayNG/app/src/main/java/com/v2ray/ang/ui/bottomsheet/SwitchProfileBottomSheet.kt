package com.v2ray.ang.ui.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import com.google.android.material.tabs.TabLayoutMediator
import com.v2ray.ang.R
import com.v2ray.ang.ui.GroupPagerAdapter
import com.v2ray.ang.util.getColorAttr
import com.v2ray.ang.viewmodel.MainViewModel

/**
 * Shows the same "group tabs + server list" UI used on the main screen, but inside a
 * bottom sheet so it can be reused by lightweight hosts (e.g. a quick-switch overlay
 * launched from the connection notification) without opening the full app UI.
 */
class SwitchProfileBottomSheet : BaseBottomSheetFragment() {

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var groupPagerAdapter: GroupPagerAdapter
    private var tabMediator: TabLayoutMediator? = null

    private var tabGroup: com.google.android.material.tabs.TabLayout? = null
    private var viewPager: androidx.viewpager2.widget.ViewPager2? = null

    private val tabSelectedListener = object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
            applyTabSelectedStyle(tab, true)
        }
        override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {
            applyTabSelectedStyle(tab, false)
        }
        override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.uwu_bottom_sheet_switch_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabGroup = view.findViewById(R.id.tab_group_switch_profile)
        viewPager = view.findViewById(R.id.view_pager_switch_profile)

        groupPagerAdapter = GroupPagerAdapter(this, emptyList())
        viewPager?.adapter = groupPagerAdapter

        setupGroupTab()
    }

    private fun setupGroupTab() {
        val tabs = tabGroup ?: return
        val pager = viewPager ?: return

        val groups = mainViewModel.getSubscriptions(requireContext())
        groupPagerAdapter.update(groups)

        tabMediator?.detach()
        tabMediator = TabLayoutMediator(tabs, pager) { tab, position ->
            groupPagerAdapter.groups.getOrNull(position)?.let { group ->
                tab.tag = group.id
                val tabView = LayoutInflater.from(requireContext()).inflate(R.layout.item_tab_group, null)
                val tabIcon = tabView.findViewById<android.widget.ImageView>(R.id.tab_icon)
                val tabLabel = tabView.findViewById<TextView>(R.id.tab_label)
                val tabBadge = tabView.findViewById<TextView>(R.id.tab_badge)
                tabLabel.text = group.remarks
                setTabIcon(tabIcon, group.icon)
                setBadgeVisibility(tabBadge, tabLabel, group.serverCount)
                tab.customView = tabView
            }
        }.also { it.attach() }

        tabs.post {
            for (i in 0 until tabs.tabCount) {
                val tab = tabs.getTabAt(i)
                applyTabSelectedStyle(tab, i == tabs.selectedTabPosition)
            }
        }

        tabs.removeOnTabSelectedListener(tabSelectedListener)
        tabs.addOnTabSelectedListener(tabSelectedListener)

        val targetIndex = groups.indexOfFirst { it.id == mainViewModel.subscriptionId }
            .takeIf { it >= 0 } ?: (groups.size - 1)

        if (targetIndex >= 0) {
            pager.setCurrentItem(targetIndex, false)
        }
    }

    /** Called by the host after the server list for the current group changed (e.g. delete). */
    fun refreshTabBadges() {
        val tabs = tabGroup ?: return
        val groups = mainViewModel.getSubscriptions(requireContext())
        for (i in groups.indices) {
            val tab = tabs.getTabAt(i) ?: continue
            val tabBadge = tab.customView?.findViewById<TextView>(R.id.tab_badge) ?: continue
            val tabLabel = tab.customView?.findViewById<TextView>(R.id.tab_label) ?: continue
            setBadgeVisibility(tabBadge, tabLabel, groups.getOrNull(i)?.serverCount ?: 0)
        }
    }

    private fun setBadgeVisibility(badge: TextView, label: TextView, count: Int) {
        if (count > 0) {
            badge.text = if (count > 99) "99+" else count.toString()
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
        badge.post { badge.requestLayout() }
    }

    private fun setTabIcon(iconView: android.widget.ImageView?, iconName: String?) {
        iconView ?: return
        if (iconName.isNullOrBlank()) {
            iconView.visibility = View.GONE
            return
        }
        val resId = resources.getIdentifier(iconName, "drawable", requireContext().packageName)
        if (resId == 0) {
            iconView.visibility = View.GONE
            return
        }
        iconView.setImageResource(resId)
        iconView.visibility = View.VISIBLE
    }

    private fun applyTabSelectedStyle(tab: com.google.android.material.tabs.TabLayout.Tab?, selected: Boolean) {
        val view = tab?.customView ?: return
        val icon = view.findViewById<android.widget.ImageView>(R.id.tab_icon)
        val label = view.findViewById<TextView>(R.id.tab_label) ?: return
        val badge = view.findViewById<TextView>(R.id.tab_badge) ?: return

        val tintColor = if (selected) requireContext().getColorAttr(R.attr.colorOnPrimary) else requireContext().getColorAttr(R.attr.colorOnSurfaceVariant)
        label.setTextColor(tintColor)
        icon?.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)

        if (selected) {
            badge.setTextColor(requireContext().getColorAttr(R.attr.colorPrimary))
            badge.backgroundTintList = android.content.res.ColorStateList.valueOf(
                requireContext().getColorAttr(R.attr.colorOnPrimary)
            )
        } else {
            badge.setTextColor(requireContext().getColorAttr(R.attr.colorOnPrimary))
            badge.backgroundTintList = android.content.res.ColorStateList.valueOf(
                requireContext().getColorAttr(R.attr.colorPrimary)
            )
        }
    }

    override fun onDestroyView() {
        tabMediator?.detach()
        tabMediator = null
        tabGroup = null
        viewPager = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "SwitchProfileBottomSheet"
    }
}
