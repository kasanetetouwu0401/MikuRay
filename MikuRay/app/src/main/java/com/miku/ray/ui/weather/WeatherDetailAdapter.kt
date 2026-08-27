package com.miku.ray.ui.weather

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.databinding.ItemWeatherDetailBinding

class WeatherDetailAdapter(
    private val context: Context,
    private val items: List<DetailItem>
) : RecyclerView.Adapter<WeatherDetailAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemWeatherDetailBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemWeatherDetailBinding.inflate(android.view.LayoutInflater.from(context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            ivDetailIcon.setImageResource(item.iconRes)
            tvDetailValue.text = item.value
            tvDetailLabel.text = item.label
        }
    }
}
