package com.v2ray.ang.ui.bottomsheet

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.v2ray.ang.R
import com.v2ray.ang.ui.WeatherDailyAdapter
import com.v2ray.ang.ui.WeatherHourlyAdapter
import com.v2ray.ang.util.WeatherHelper
import com.v2ray.ang.util.WindowBlurUtils
import com.v2ray.ang.util.getColorAttr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One rendered cell in the hourly strip. */
data class HourlyForecastItem(
    val timeLabel: String,
    val dayLabel: String,
    val tempCelsius: Int,
    val precipProbability: Int,
    val iconRes: Int,
    val isNow: Boolean
)

data class DailyForecastItem(
    val weekdayLabel: String,
    val maxTempCelsius: Int,
    val minTempCelsius: Int,
    val precipProbability: Int,
    val iconRes: Int
)

class WeatherForecastBottomSheet(
    private val context: Context,
    private val onWeatherUpdated: ((WeatherHelper.WeatherResult) -> Unit)? = null
) {
    private var job: Job? = null

    fun show() {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.uwu_layout_bottom_sheet_weather_forecast, null)

        val ivIcon = view.findViewById<ImageView>(R.id.ivForecastCurrentIcon)
        val tvCondition = view.findViewById<TextView>(R.id.tvForecastCurrentCondition)
        val tvTemp = view.findViewById<TextView>(R.id.tvForecastCurrentTemp)
        val tvFeelsLike = view.findViewById<TextView>(R.id.tvForecastFeelsLike)
        val tvMaxMin = view.findViewById<TextView>(R.id.tvForecastMaxMin)
        val tvError = view.findViewById<TextView>(R.id.tvForecastError)
        val progress = view.findViewById<ProgressBar>(R.id.progressForecastLoading)
        val cardHourly = view.findViewById<View>(R.id.cardForecastHourly)
        val cardDaily = view.findViewById<View>(R.id.cardForecastDaily)
        val recyclerHourly = view.findViewById<RecyclerView>(R.id.recyclerForecastHourly)
        val recyclerDaily = view.findViewById<RecyclerView>(R.id.recyclerForecastDaily)

        recyclerHourly.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerDaily.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        fun render(entry: WeatherHelper.WeatherCacheEntry) {
            tvError.isVisible = false

            ivIcon.setImageResource(WeatherHelper.iconResForCode(entry.weatherCode, entry.isDay))
            tvCondition.text = context.getString(WeatherHelper.conditionLabelRes(entry.weatherCode))
            tvTemp.text = "${Math.round(entry.temperatureCelsius)}\u00b0"
            tvFeelsLike.text = context.getString(
                R.string.weather_feels_like,
                "${Math.round(entry.apparentTemperatureCelsius)}\u00b0"
            )

            val todayMax = entry.dailyTemperatureMaxCelsius.getOrNull(0)
            val todayMin = entry.dailyTemperatureMinCelsius.getOrNull(0)
            tvMaxMin.isVisible = todayMax != null && todayMin != null
            if (todayMax != null && todayMin != null) {
                tvMaxMin.text = context.getString(
                    R.string.weather_max_min,
                    "${Math.round(todayMax)}\u00b0",
                    "${Math.round(todayMin)}\u00b0"
                )
            }

            val hourlyItems = buildHourlyItems(entry)
            cardHourly.isVisible = hourlyItems.isNotEmpty()
            recyclerHourly.adapter = WeatherHourlyAdapter(context, hourlyItems)

            val dailyItems = buildDailyItems(entry)
            cardDaily.isVisible = dailyItems.isNotEmpty()
            recyclerDaily.adapter = WeatherDailyAdapter(context, dailyItems)
        }

        val cached = WeatherHelper.getCachedWeatherEntry()
        if (cached != null) {
            render(cached)
        } else {
            progress.isVisible = true
        }

        val scope = (context as? LifecycleOwner)?.lifecycleScope ?: CoroutineScope(Dispatchers.Main + Job())
        job = scope.launch {
            val fresh = WeatherHelper.fetchForecast(context)
            progress.isVisible = false
            if (fresh != null) {
                render(fresh)
                onWeatherUpdated?.invoke(fresh.toWeatherResult())
            } else if (cached == null) {
                tvError.isVisible = true
            }
        }

        dialog.setContentView(view)

        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        val bgColor = context.getColorAttr(R.attr.colorBg)

        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            bottomSheet.backgroundTintList = ColorStateList.valueOf(bgColor)

            bottomSheet.clipToOutline = true

            ViewCompat.setOnApplyWindowInsetsListener(bottomSheet) { v, insets ->
                val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                val screenHeight = v.resources.displayMetrics.heightPixels
                val margin = (8 * v.resources.displayMetrics.density).toInt()

                dialog.behavior.maxHeight = screenHeight - statusBarInset - margin

                insets
            }
        }

        dialog.window?.let { window ->
            WindowBlurUtils.applyWindowBlur(window)

            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

            window.navigationBarColor = bgColor
        }

        dialog.setOnDismissListener {
            job?.cancel()
        }

        dialog.show()
    }

    private fun buildHourlyItems(entry: WeatherHelper.WeatherCacheEntry): List<HourlyForecastItem> {
        val times = entry.hourlyTimeIso
        if (times.isEmpty()) return emptyList()

        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.US).format(Date())
        val startIndex = times.indexOfFirst { it >= nowIso }.let { if (it < 0) 0 else it }
        val endIndex = minOf(times.size, startIndex + 24)

        val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        val timeFmt = SimpleDateFormat("HH.mm", Locale.getDefault())
        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())

        return (startIndex until endIndex).mapNotNull { i ->
            val iso = times.getOrNull(i) ?: return@mapNotNull null
            val temp = entry.hourlyTemperatureCelsius.getOrNull(i) ?: return@mapNotNull null
            val code = entry.hourlyWeatherCode.getOrNull(i) ?: 0
            val precip = entry.hourlyPrecipitationProbability.getOrNull(i) ?: 0
            val isDay = (entry.hourlyIsDay.getOrNull(i) ?: 1) == 1
            val date = try {
                isoParser.parse(iso)
            } catch (e: Exception) {
                null
            }

            HourlyForecastItem(
                timeLabel = if (i == startIndex) {
                    context.getString(R.string.weather_now)
                } else {
                    date?.let { timeFmt.format(it) } ?: ""
                },
                dayLabel = date?.let { dayFmt.format(it) } ?: "",
                tempCelsius = Math.round(temp).toInt(),
                precipProbability = precip,
                iconRes = WeatherHelper.iconResForCode(code, isDay),
                isNow = i == startIndex
            )
        }
    }

    private fun buildDailyItems(entry: WeatherHelper.WeatherCacheEntry): List<DailyForecastItem> {
        val dates = entry.dailyDateIso
        if (dates.isEmpty()) return emptyList()

        val dateParser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())

        return dates.indices.mapNotNull { i ->
            val dateStr = dates.getOrNull(i) ?: return@mapNotNull null
            val max = entry.dailyTemperatureMaxCelsius.getOrNull(i) ?: return@mapNotNull null
            val min = entry.dailyTemperatureMinCelsius.getOrNull(i) ?: return@mapNotNull null
            val code = entry.dailyWeatherCode.getOrNull(i) ?: 0
            val precip = entry.dailyPrecipitationProbabilityMax.getOrNull(i) ?: 0

            val weekdayLabel = if (i == 0) {
                context.getString(R.string.weather_today)
            } else {
                val date = try {
                    dateParser.parse(dateStr)
                } catch (e: Exception) {
                    null
                }
                date?.let { dayFmt.format(it) } ?: ""
            }

            DailyForecastItem(
                weekdayLabel = weekdayLabel,
                maxTempCelsius = Math.round(max).toInt(),
                minTempCelsius = Math.round(min).toInt(),
                precipProbability = precip,
                iconRes = WeatherHelper.iconResForCode(code, true)
            )
        }
    }
}
