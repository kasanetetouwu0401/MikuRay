package com.v2ray.ang.ui

import android.annotation.SuppressLint
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.v2ray.ang.dto.GroupMapItem

/**
 * Pager adapter for subscription groups.
 *
 * Can be hosted either by a [FragmentActivity] (e.g. MainActivity, where the ViewPager2 lives
 * directly in the activity's own layout) or by a [Fragment] (e.g. a bottom sheet embedding the
 * same tabs+pager UI), so the created pages are scoped to whichever one actually owns the
 * ViewPager2 rather than always the top-level activity.
 */
class GroupPagerAdapter : FragmentStateAdapter {
    var groups: List<GroupMapItem>
        private set

    constructor(fragmentActivity: FragmentActivity, groups: List<GroupMapItem>) : super(fragmentActivity) {
        this.groups = groups
    }

    constructor(fragment: Fragment, groups: List<GroupMapItem>) : super(fragment) {
        this.groups = groups
    }

    override fun getItemCount(): Int = groups.size
    override fun createFragment(position: Int) = GroupServerFragment.newInstance(groups[position].id)
    override fun getItemId(position: Int): Long = groups[position].id.hashCode().toLong()
    override fun containsItem(itemId: Long): Boolean = groups.any { it.id.hashCode().toLong() == itemId }

    @SuppressLint("NotifyDataSetChanged")
    fun update(groups: List<GroupMapItem>) {
        this.groups = groups
        notifyDataSetChanged()
    }
}
