package com.miku.ray.ui.userasset

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.R
import com.miku.ray.contracts.BaseAdapterListener
import com.miku.ray.databinding.ItemRecyclerUserAssetBinding
import com.miku.ray.dto.entities.AssetUrlCache
import com.miku.ray.extension.toTrafficString
import java.text.DateFormat
import java.util.Date

class UserAssetAdapter(
    private val adapterListener: BaseAdapterListener?
) : RecyclerView.Adapter<UserAssetAdapter.UserAssetViewHolder>() {

    private var assets: List<AssetUrlCache> = emptyList()
    private var fileMetadata: Map<String, AssetFileMetadata> = emptyMap()

    fun submitState(state: UserAssetUiState) {
        val oldAssets = assets
        val newAssets = state.assets
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldAssets.size
            override fun getNewListSize(): Int = newAssets.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldAssets[oldItemPosition].guid == newAssets[newItemPosition].guid
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = oldAssets[oldItemPosition]
                val new = newAssets[newItemPosition]
                if (old != new) return false
                val oldMeta = fileMetadata[old.guid]
                val newMeta = state.fileMetadata[new.guid]
                return oldMeta == newMeta
            }
        })
        assets = newAssets
        fileMetadata = state.fileMetadata
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemCount() = assets.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserAssetViewHolder {
        return UserAssetViewHolder(
            ItemRecyclerUserAssetBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: UserAssetViewHolder, position: Int) {
        val item = assets.getOrNull(position) ?: return
        val meta = fileMetadata[item.guid]

        with(holder.binding) {
            assetName.text = item.assetUrl.remarks

            if (meta != null) {
                val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                assetProperties.text =
                    "${meta.length.toTrafficString()}  •  ${dateFormat.format(Date(meta.lastModified))}"
            } else {
                assetProperties.text = root.context.getString(R.string.msg_file_not_found)
            }

            layoutEdit.isVisible = item.assetUrl.locked != true && item.assetUrl.url != "file"

            layoutEdit.setOnClickListener {
                adapterListener?.onEdit(item.guid, position)
            }

            layoutRemove.setOnClickListener {
                adapterListener?.onRemove(item.guid, position)
            }

            layoutCard.setOnClickListener {
            }
        }
    }

    class UserAssetViewHolder(val binding: ItemRecyclerUserAssetBinding) :
        RecyclerView.ViewHolder(binding.root)
}
