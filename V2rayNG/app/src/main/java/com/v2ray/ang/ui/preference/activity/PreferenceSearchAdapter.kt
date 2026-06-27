package com.v2ray.ang.ui.preference.activity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.databinding.ItemPreferenceSearchBinding
import com.v2ray.ang.dto.PreferenceSearchEntry

class PreferenceSearchAdapter(
    private val onResultClicked: (PreferenceSearchEntry) -> Unit
) : RecyclerView.Adapter<PreferenceSearchAdapter.ResultViewHolder>() {

    private var results: List<PreferenceSearchEntry> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val binding = ItemPreferenceSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ResultViewHolder(binding)
    }

    override fun getItemCount(): Int = results.size

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(results[position])
    }

    fun submitList(newResults: List<PreferenceSearchEntry>) {
        results = newResults
        notifyDataSetChanged()
    }

    inner class ResultViewHolder(
        private val binding: ItemPreferenceSearchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: PreferenceSearchEntry) {
            binding.title.text = entry.title

            if (entry.summary.isNotBlank()) {
                binding.summary.visibility = android.view.View.VISIBLE
                binding.summary.text = entry.summary
            } else {
                binding.summary.visibility = android.view.View.GONE
            }

            binding.breadcrumb.text = if (entry.categoryTitle != entry.screenTitle) {
                "${entry.screenTitle} • ${entry.categoryTitle}"
            } else {
                entry.screenTitle
            }

            if (entry.iconRes != 0) {
                binding.icon.setImageResource(entry.iconRes)
                binding.icon.visibility = android.view.View.VISIBLE
            } else {
                binding.icon.visibility = android.view.View.INVISIBLE
            }

            binding.cardRoot.setOnClickListener {
                onResultClicked(entry)
            }
        }
    }
}
