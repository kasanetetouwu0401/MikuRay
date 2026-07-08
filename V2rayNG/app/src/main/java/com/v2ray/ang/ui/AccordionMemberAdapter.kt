package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemGroupAccordionMemberBinding
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.SensorTextController

/**
 * Lightweight adapter showing the server members of an expanded accordion group.
 */
class AccordionMemberAdapter(
    private val listener: Listener
) : RecyclerView.Adapter<AccordionMemberAdapter.MemberViewHolder>() {

    interface Listener {
        fun onSelectMember(guid: String)
        fun onEditMember(guid: String)
    }

    private var members: List<ServersCache> = emptyList()

    fun setMembers(newMembers: List<ServersCache>) {
        members = newMembers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        return MemberViewHolder(
            ItemGroupAccordionMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val item = members[position]
        val binding = holder.binding
        val context = binding.root.context

        binding.tvMemberName.text = item.profile.remarks
        binding.tvMemberAddress.text = SensorTextController.getAddress(item.profile)

        val aff = MmkvManager.decodeServerAffiliationInfo(item.guid)
        val delayStr = aff?.getTestDelayString().orEmpty()
        binding.tvMemberPing.text = delayStr
        binding.tvMemberPing.visibility = if (delayStr.isEmpty()) View.GONE else View.VISIBLE
        if ((aff?.testDelayMillis ?: 0L) < 0L) {
            binding.tvMemberPing.setTextColor(ContextCompat.getColor(context, R.color.colorPingRed))
        } else {
            binding.tvMemberPing.setTextColor(ContextCompat.getColor(context, R.color.colorPing))
        }

        val isSelected = item.guid == MmkvManager.getSelectServer()
        binding.ivMemberSelected.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE

        binding.layoutMemberRow.setOnClickListener {
            listener.onSelectMember(item.guid)
        }
        binding.btnMemberEdit.setOnClickListener {
            listener.onEditMember(item.guid)
        }
    }

    override fun getItemCount(): Int = members.size

    class MemberViewHolder(val binding: ItemGroupAccordionMemberBinding) :
        RecyclerView.ViewHolder(binding.root)
}
