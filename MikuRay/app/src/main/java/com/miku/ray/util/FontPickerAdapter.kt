package com.miku.ray.util

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.databinding.ItemFontPickerBinding

class FontPickerAdapter(
    private val context: Context,
    private val values: Array<String>,
    private val labels: Array<String>,
    private var selectedValue: String,
    private val onSelect: (value: String, label: String) -> Unit
) : RecyclerView.Adapter<FontPickerAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemFontPickerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemFontPickerBinding.inflate(android.view.LayoutInflater.from(context), parent, false))

    override fun getItemCount() = values.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val value = values[position]
        val label = labels.getOrElse(position) { value }

        holder.binding.textFontLabel.text = label
        holder.binding.textFontLabel.typeface = AppFontResolver.getTypeface(context, value)

        val isSelected = value == selectedValue
        holder.binding.imageFontCheck.visibility = if (isSelected) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            if (value != selectedValue) {
                val previous = selectedValue
                selectedValue = value
                notifyItemChanged(values.indexOf(previous))
                notifyItemChanged(position)
            }
            onSelect(value, label)
        }
    }
}
