package com.miku.ray.ui.weather

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.databinding.ItemWeatherDailyBinding

class WeatherDailyAdapter(
    private val context: Context,
    private val items: List<DailyForecastItem>
) : RecyclerView.Adapter<WeatherDailyAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemWeatherDailyBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemWeatherDailyBinding.inflate(android.view.LayoutInflater.from(context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            tvDailyMax.text = "${item.maxTempCelsius}\u00b0"
            tvDailyMin.text = "${item.minTempCelsius}\u00b0"
            ivDailyIcon.setImageResource(item.iconRes)
            tvDailyWeekday.text = item.weekdayLabel

            if (item.precipProbability > 0) {
                tvDailyPrecip.visibility = View.VISIBLE
                tvDailyPrecip.text = "${item.precipProbability}%"
            } else {
                tvDailyPrecip.visibility = View.INVISIBLE
            }
        }
    }
}
