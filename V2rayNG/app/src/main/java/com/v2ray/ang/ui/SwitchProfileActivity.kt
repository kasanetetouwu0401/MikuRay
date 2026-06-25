package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.databinding.ActivitySwitchProfileBinding
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.getColorAttr
import com.v2ray.ang.viewmodel.MainViewModel

/**
 * Floating dialog activity untuk switch profil server dari notifikasi.
 *
 * Pakai Theme.AppDialog (parent: Theme.Material3Expressive.DayNight.Dialog) sehingga
 * Android otomatis handle: floating window, dim background, ukuran dialog —
 * tanpa layout card/scrim manual.
 *
 * Diluncurkan via PendingIntent dari action notifikasi "Ganti Profil".
 */
class SwitchProfileActivity : BaseActivity(), SwitchProfileGroupFragment.Callback {

    private val binding by lazy {
        ActivitySwitchProfileBinding.inflate(layoutInflater)
    }

    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var groupPagerAdapter: SwitchGroupPagerAdapter
    private var tabMediator: TabLayoutMediator? = null

    private val tabSelectedListener = object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
            applyTabStyle(tab, true)
        }
        override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {
            applyTabStyle(tab, false)
        }
        override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupViewPager()
        setupGroupTab()
        mainViewModel.reloadServerList()
    }

    private fun setupViewPager() {
        groupPagerAdapter = SwitchGroupPagerAdapter(this, emptyList())
        binding.viewPager.apply {
            adapter = groupPagerAdapter
            isUserInputEnabled = true
            getChildAt(0)?.overScrollMode = View.OVER_SCROLL_NEVER
        }
    }

    private fun setupGroupTab() {
        val groups = mainViewModel.getSubscriptions(this)
        groupPagerAdapter.update(groups)

        tabMediator?.detach()
        tabMediator = TabLayoutMediator(binding.tabGroup, binding.viewPager) { tab, position ->
            groups.getOrNull(position)?.let { group ->
                tab.tag = group.id
                val tabView = LayoutInflater.from(this).inflate(R.layout.item_tab_group, null)
                val tabIcon = tabView.findViewById<ImageView>(R.id.tab_icon)
                val tabLabel = tabView.findViewById<TextView>(R.id.tab_label)
                val tabBadge = tabView.findViewById<TextView>(R.id.tab_badge)
                tabLabel.text = group.remarks
                setTabIcon(tabIcon, group.icon)
                setBadgeCount(tabBadge, group.serverCount)
                tab.customView = tabView
            }
        }.also { it.attach() }

        binding.tabGroup.post {
            for (i in 0 until binding.tabGroup.tabCount) {
                applyTabStyle(binding.tabGroup.getTabAt(i), i == binding.tabGroup.selectedTabPosition)
            }
        }

        binding.tabGroup.removeOnTabSelectedListener(tabSelectedListener)
        binding.tabGroup.addOnTabSelectedListener(tabSelectedListener)
    }

    private fun applyTabStyle(tab: com.google.android.material.tabs.TabLayout.Tab?, selected: Boolean) {
        val view = tab?.customView ?: return
        val icon = view.findViewById<ImageView>(R.id.tab_icon)
        val label = view.findViewById<TextView>(R.id.tab_label) ?: return
        val badge = view.findViewById<TextView>(R.id.tab_badge) ?: return

        val tintColor = if (selected) getColorAttr("colorOnPrimary")
                        else getColorAttr("colorOnSurfaceVariant")
        label.setTextColor(tintColor)
        icon?.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)

        if (selected) {
            badge.setTextColor(getColorAttr("colorPrimary"))
            badge.backgroundTintList = android.content.res.ColorStateList.valueOf(getColorAttr("colorOnPrimary"))
        } else {
            badge.setTextColor(getColorAttr("colorOnPrimary"))
            badge.backgroundTintList = android.content.res.ColorStateList.valueOf(getColorAttr("colorPrimary"))
        }
    }

    private fun setTabIcon(iconView: ImageView?, iconName: String?) {
        iconView ?: return
        if (iconName.isNullOrBlank()) { iconView.visibility = View.GONE; return }
        val resId = resources.getIdentifier(iconName, "drawable", packageName)
        if (resId == 0) { iconView.visibility = View.GONE; return }
        iconView.setImageResource(resId)
        iconView.visibility = View.VISIBLE
    }

    private fun setBadgeCount(badge: TextView, count: Int) {
        if (count > 0) {
            badge.text = if (count > 99) "99+" else count.toString()
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
        badge.post { badge.requestLayout() }
    }

    override fun onServerSelected(guid: String) {
        val wasRunning = CoreServiceManager.isRunning()
        MmkvManager.setSelectServer(guid)
        if (wasRunning) {
            CoreServiceManager.stopVService(this)
            CoreServiceManager.startVService(this, guid)
        }
        finish()
    }

    override fun onDestroy() {
        tabMediator?.detach()
        super.onDestroy()
    }
}

class SwitchGroupPagerAdapter(
    activity: FragmentActivity,
    var groups: List<GroupMapItem>,
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = groups.size
    override fun createFragment(position: Int) = SwitchProfileGroupFragment.newInstance(groups[position].id)
    override fun getItemId(position: Int): Long = groups[position].id.hashCode().toLong()
    override fun containsItem(itemId: Long): Boolean = groups.any { it.id.hashCode().toLong() == itemId }

    @SuppressLint("NotifyDataSetChanged")
    fun update(groups: List<GroupMapItem>) {
        this.groups = groups
        notifyDataSetChanged()
    }
}
