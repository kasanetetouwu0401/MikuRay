package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.databinding.ItemGroupAccordionBinding
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.handler.MmkvManager

/**
 * Adapter that renders subscription groups as expandable accordion cards.
 * Tapping the chevron expands the card in place to reveal its server profiles,
 * instead of switching between horizontal tab pages.
 */
class AccordionGroupAdapter(
    private val listener: Listener
) : RecyclerView.Adapter<AccordionGroupAdapter.GroupViewHolder>() {

    interface Listener {
        fun onResolveMembers(group: GroupMapItem): List<ServersCache>
        fun onSelectMember(guid: String)
        fun onEditMember(guid: String)
        fun onResolveIcon(iconName: String?): Int
    }

    private var groups: List<GroupMapItem> = emptyList()
    // Multiple groups can be expanded at the same time.
    private val expandedIds = mutableSetOf<String>()

    fun setGroups(newGroups: List<GroupMapItem>) {
        groups = newGroups
        // Drop expanded state for groups that no longer exist.
        expandedIds.retainAll(newGroups.map { it.id }.toSet())
        notifyDataSetChanged()
    }

    /**
     * Refresh selection / ping state for all groups without collapsing them.
     * The header subtitle reflects the globally selected server, so every group's
     * header needs a rebind on selection change -- not just the expanded one --
     * otherwise a collapsed group can keep showing a stale "selected server" preview
     * until it's tapped open again.
     */
    fun refreshExpandedMembers() {
        groups.indices.forEach { index -> notifyItemChanged(index) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        return GroupViewHolder(
            ItemGroupAccordionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        val binding = holder.binding
        val isExpanded = expandedIds.contains(group.id)

        binding.tvGroupName.text = group.remarks

        val selectedGuid = MmkvManager.getSelectServer()
        val selectedProfile = selectedGuid?.let { MmkvManager.decodeServerConfig(it) }
        val selectedBelongsHere = selectedProfile != null &&
            (group.id.isEmpty() || selectedProfile.subscriptionId == group.id)

        binding.tvGroupSubtitle.text = if (selectedBelongsHere) {
            "${group.serverCount} servers \u00b7 ${selectedProfile?.remarks}"
        } else {
            "${group.serverCount} servers"
        }

        val iconRes = listener.onResolveIcon(group.icon)
        binding.ivGroupIcon.setImageResource(iconRes)

        binding.ivExpandChevron.rotation = if (isExpanded) 180f else 0f
        binding.dividerMembers.visibility = if (isExpanded) View.VISIBLE else View.GONE
        binding.rvMembers.visibility = if (isExpanded) View.VISIBLE else View.GONE

        if (isExpanded) {
            val memberAdapter = (binding.rvMembers.adapter as? AccordionMemberAdapter)
                ?: AccordionMemberAdapter(object : AccordionMemberAdapter.Listener {
                    override fun onSelectMember(guid: String) = listener.onSelectMember(guid)
                    override fun onEditMember(guid: String) = listener.onEditMember(guid)
                }).also {
                    binding.rvMembers.layoutManager = LinearLayoutManager(binding.root.context)
                    binding.rvMembers.adapter = it
                }
            memberAdapter.setMembers(listener.onResolveMembers(group))
        }

        val toggle = View.OnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@OnClickListener
            if (expandedIds.contains(group.id)) {
                expandedIds.remove(group.id)
            } else {
                expandedIds.add(group.id)
            }
            notifyItemChanged(pos)
        }
        binding.layoutHeader.setOnClickListener(toggle)
        binding.btnExpand.setOnClickListener(toggle)
    }

    override fun getItemCount(): Int = groups.size

    class GroupViewHolder(val binding: ItemGroupAccordionBinding) :
        RecyclerView.ViewHolder(binding.root)
}
