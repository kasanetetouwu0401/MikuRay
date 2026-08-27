package com.miku.ray.util

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.databinding.ItemIndicatorStyleBinding

class IndicatorStyleAdapter(
    private val context: Context,
    private val selected: IndicatorStyle,
    private val onSelect: (IndicatorStyle) -> Unit
) : RecyclerView.Adapter<IndicatorStyleAdapter.ViewHolder>() {

    private val styles = IndicatorStyle.values()

    inner class ViewHolder(val binding: ItemIndicatorStyleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemIndicatorStyleBinding.inflate(android.view.LayoutInflater.from(context), parent, false))

    override fun getItemCount() = styles.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val style = styles[position]

        holder.binding.imagePreviewContainer.background = ContextCompat.getDrawable(context, style.drawableRes)

        val isSelected = style == selected

        if (isSelected) {
            holder.binding.imageCheck.visibility = View.VISIBLE
            holder.binding.contentContainer.visibility = View.INVISIBLE
        } else {
            holder.binding.imageCheck.visibility = View.GONE
            holder.binding.contentContainer.visibility = View.VISIBLE
        }

        holder.binding.root.setOnClickListener {
            onSelect(style)
        }
    }
}
