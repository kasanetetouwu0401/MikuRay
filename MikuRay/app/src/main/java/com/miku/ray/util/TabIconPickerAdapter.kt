package com.miku.ray.util

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.databinding.ItemTabIconPickerBinding

class TabIconPickerAdapter(
    private val context: Context,
    val icons: List<String?>,
    private var selectedIcon: String?,
    private val onSelect: (String?) -> Unit,
) : RecyclerView.Adapter<TabIconPickerAdapter.VH>() {

    companion object {
        val DEFAULT_ICONS: List<String> = listOf(
            "filter_all_solar",
            "filter_airplane_solar",
            "filter_book_solar",
            "filter_bots_solar",
            "filter_cat_solar",
            "filter_channel_solar",
            "filter_crown_solar",
            "filter_custom_solar",
            "filter_favorite_solar",
            "filter_flower_solar",
            "filter_game_solar",
            "filter_groups_solar",
            "filter_home_solar",
            "filter_light_solar",
            "filter_like_solar",
            "filter_love_solar",
            "filter_mask_solar",
            "filter_money_solar",
            "filter_note_solar",
            "filter_palette_solar",
            "filter_party_solar",
            "filter_private_solar",
            "filter_setup_solar",
            "filter_sport_solar",
            "filter_study_solar",
            "filter_trade_solar",
            "filter_travel_solar",
            "filter_unmuted_solar",
            "filter_unread_solar",
            "filter_work_solar",
        )

        fun labelFor(iconName: String): String = iconName
            .removePrefix("filter_")
            .removeSuffix("_solar")
            .replaceFirstChar { it.uppercase() }
    }

    inner class VH(val binding: ItemTabIconPickerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemTabIconPickerBinding.inflate(android.view.LayoutInflater.from(context), parent, false))

    override fun getItemCount() = icons.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val name     = icons[position]
        val resId    = if (name != null) context.resources.getIdentifier(name, "drawable", context.packageName) else 0
        val selected = name == selectedIcon

        if (resId != 0) {
            holder.binding.iconView.setImageResource(resId)
        } else {
            holder.binding.iconView.setImageDrawable(null)
        }

        val (bgColor, iconTint, checkTint) = if (selected) {
            Triple(
                context.getColorAttr("colorPrimary"),
                context.getColorAttr("colorOnPrimary"),
                context.getColorAttr("colorOnPrimary"),
            )
        } else {
            Triple(
                0,
                context.getColorAttr("colorOnSurfaceVariant"),
                0,
            )
        }

        holder.binding.iconCard.setCardBackgroundColor(bgColor)
        holder.binding.iconView.imageTintList = ColorStateList.valueOf(iconTint)
        holder.binding.checkView.visibility   = if (selected) View.VISIBLE else View.GONE
        if (selected) holder.binding.checkView.imageTintList = ColorStateList.valueOf(checkTint)

        holder.itemView.setOnClickListener {
            val prev = selectedIcon
            selectedIcon = name
            onSelect(name)
            val prevIdx = icons.indexOf(prev)
            if (prevIdx >= 0) notifyItemChanged(prevIdx)
            notifyItemChanged(position)
        }
    }

    fun setSelected(iconName: String?) {
        val prev = selectedIcon
        selectedIcon = iconName
        val prevIdx = icons.indexOf(prev)
        val newIdx  = icons.indexOf(iconName)
        if (prevIdx >= 0) notifyItemChanged(prevIdx)
        if (newIdx  >= 0) notifyItemChanged(newIdx)
    }
}
