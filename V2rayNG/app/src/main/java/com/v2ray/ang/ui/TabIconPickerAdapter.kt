package com.v2ray.ang.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.v2ray.ang.R
import com.v2ray.ang.util.getColorAttr

/**
 * Grid adapter untuk tab icon picker dialog.
 * Tiap item menampilkan satu vector drawable dari daftar [icons].
 * Item dengan nilai `null` merepresentasikan opsi "tanpa icon" — cell
 * ditampilkan kosong tanpa drawable.
 * Selected item mendapat background [colorPrimary] dan tint
 * [colorOnPrimary] (icon & check); unselected pakai [colorOnSurfaceVariant].
 */
class TabIconPickerAdapter(
    private val context: Context,
    /** List nama drawable resource tanpa ".xml" (e.g. "filter_cloud_solar"), atau null untuk opsi "tanpa icon". */
    val icons: List<String?>,
    private var selectedIcon: String?,
    private val onSelect: (String?) -> Unit,
) : RecyclerView.Adapter<TabIconPickerAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.icon_card)
        val icon: ImageView        = view.findViewById(R.id.icon_view)
        val check: ImageView       = view.findViewById(R.id.check_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(context)
            .inflate(R.layout.item_tab_icon_picker, parent, false)
        return VH(v)
    }

    override fun getItemCount() = icons.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val name     = icons[position]
        val resId    = if (name != null) context.resources.getIdentifier(name, "drawable", context.packageName) else 0
        val selected = name == selectedIcon

        // Icon drawable — kosong jika item ini adalah opsi "tanpa icon" atau resource tidak ditemukan
        if (resId != 0) {
            holder.icon.setImageResource(resId)
        } else {
            holder.icon.setImageDrawable(null)
        }

        // Tint & background
        val (bgColor, iconTint, checkTint) = if (selected) {
            Triple(
                context.getColorAttr("colorPrimary"),
                context.getColorAttr("colorOnPrimary"),
                context.getColorAttr("colorOnPrimary"),
            )
        } else {
            Triple(
                0, // transparent
                context.getColorAttr("colorOnSurfaceVariant"),
                0,
            )
        }

        holder.card.setCardBackgroundColor(bgColor)
        holder.icon.imageTintList = ColorStateList.valueOf(iconTint)
        holder.check.visibility   = if (selected) View.VISIBLE else View.GONE
        if (selected) holder.check.imageTintList = ColorStateList.valueOf(checkTint)

        holder.itemView.setOnClickListener {
            val prev = selectedIcon
            selectedIcon = name
            onSelect(name)
            // Refresh dua item: prev selected + yang baru
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
