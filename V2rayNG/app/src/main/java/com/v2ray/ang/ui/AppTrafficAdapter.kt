package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemAppTrafficBinding
import com.v2ray.ang.dto.AppTrafficInfo
import com.v2ray.ang.util.AppTrafficUtil

class AppTrafficAdapter : RecyclerView.Adapter<AppTrafficAdapter.AppTrafficViewHolder>() {

    var apps: List<AppTrafficInfo> = emptyList()
        private set

    /** When true, items render as a live transfer rate (per poll interval) rather than a lifetime total. */
    var isLiveMode: Boolean = false

    fun submitList(newApps: List<AppTrafficInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppTrafficViewHolder {
        val binding = ItemAppTrafficBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppTrafficViewHolder(binding)
    }

    override fun getItemCount(): Int = apps.size

    override fun onBindViewHolder(holder: AppTrafficViewHolder, position: Int) {
        holder.bind(apps[position], isLiveMode)
    }

    class AppTrafficViewHolder(private val binding: ItemAppTrafficBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppTrafficInfo, isLiveMode: Boolean) {
            binding.icon.setImageDrawable(item.appIcon)
            binding.name.text = if (item.isSystemApp) {
                String.format("** %s", item.appName)
            } else {
                item.appName
            }
            binding.packageName.text = item.packageName

            if (isLiveMode) {
                binding.trafficDetail.text = binding.root.context.getString(R.string.app_traffic_live_rate_label)
                binding.trafficTotal.text = binding.root.context.getString(
                    R.string.app_traffic_per_interval_format,
                    AppTrafficUtil.formatBytes(item.totalBytes)
                )
            } else {
                binding.trafficDetail.text = binding.root.context.getString(
                    R.string.app_traffic_rx_tx_format,
                    AppTrafficUtil.formatBytes(item.rxBytes),
                    AppTrafficUtil.formatBytes(item.txBytes)
                )
                binding.trafficTotal.text = AppTrafficUtil.formatBytes(item.totalBytes)
            }

            binding.activeBadge.visibility = if (item.isActiveNow) View.VISIBLE else View.GONE
        }
    }
}
