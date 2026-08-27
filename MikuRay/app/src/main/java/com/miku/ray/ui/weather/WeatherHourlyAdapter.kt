package com.miku.ray.ui.weather

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.databinding.ItemWeatherHourlyBinding
import com.miku.ray.util.getColorAttr

class WeatherHourlyAdapter(
    private val context: Context,
    private val items: List<HourlyForecastItem>
) : RecyclerView.Adapter<WeatherHourlyAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemWeatherHourlyBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemWeatherHourlyBinding.inflate(android.view.LayoutInflater.from(context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            tvHourlyTemp.text = "${item.tempCelsius}\u00b0"
            ivHourlyIcon.setImageResource(item.iconRes)
            tvHourlyTime.text = item.timeLabel
            tvHourlyDay.text = item.dayLabel

            if (item.precipProbability > 0) {
                tvHourlyPrecip.visibility = View.VISIBLE
                tvHourlyPrecip.text = "${item.precipProbability}%"
            } else {
                tvHourlyPrecip.visibility = View.INVISIBLE
            }

            if (item.isNow) {
                cardHourlyTemp.setCardBackgroundColor(context.getColorAttr("colorPrimary"))
                tvHourlyTemp.setTextColor(context.getColorAttr("colorOnPrimary"))
            } else {
                cardHourlyTemp.setCardBackgroundColor(context.getColorAttr("colorSurfaceContainerHighest"))
                tvHourlyTemp.setTextColor(context.getColorAttr("colorOnSurface"))
            }
        }
    }
}
