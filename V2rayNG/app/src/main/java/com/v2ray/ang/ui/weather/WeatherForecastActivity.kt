package com.v2ray.ang.ui.weather

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.R
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.util.getColorAttr
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

class WeatherForecastActivity : BaseActivity() {
    private var job: Job? = null

    private lateinit var ivIcon: ImageView
    private lateinit var tvCondition: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvFeelsLike: TextView
    private lateinit var tvMaxMin: TextView
    private lateinit var tvError: TextView
    private lateinit var tvSummary: TextView
    private lateinit var cardCurrent: android.view.View
    private lateinit var cardSummary: android.view.View
    private lateinit var cardHourly: android.view.View
    private lateinit var cardDaily: android.view.View
    private lateinit var recyclerHourly: RecyclerView
    private lateinit var recyclerDaily: RecyclerView

    // Detail grid
    private lateinit var cardUvIndex: android.view.View
    private lateinit var cardSun: android.view.View
    private lateinit var cardAirQuality: android.view.View
    private lateinit var cardPollen: android.view.View
    private lateinit var waveHumidity: WaveFillView
    private lateinit var tvHumidityValue: TextView
    private lateinit var tvDewPoint: TextView
    private lateinit var uvScaleView: UvScaleView
    private lateinit var tvUvValue: TextView
    private lateinit var tvUvLabel: TextView
    private lateinit var tvPrecipitationValue: TextView
    private lateinit var tvPrecipitationHint: TextView
    private lateinit var tvWindValue: TextView
    private lateinit var tvWindDetail: TextView
    private lateinit var tvCloudCoverValue: TextView
    private lateinit var tvCloudCoverLabel: TextView
    private lateinit var arcPressure: ArcGaugeView
    private lateinit var tvPressureValue: TextView
    private lateinit var tvVisibilityValue: TextView
    private lateinit var sunArcView: SunArcView
    private lateinit var tvSunrise: TextView
    private lateinit var tvSunset: TextView
    private lateinit var tvDaylight: TextView
    private lateinit var tvAqiValue: TextView
    private lateinit var barAqi: LevelBarView
    private lateinit var tvAqiLabel: TextView
    private lateinit var tvPollenValue: TextView
    private lateinit var barPollen: LevelBarView
    private lateinit var tvPollenLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_forecast)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.weather_forecast_title))

        ivIcon = findViewById(R.id.ivForecastCurrentIcon)
        tvCondition = findViewById(R.id.tvForecastCurrentCondition)
        tvTemp = findViewById(R.id.tvForecastCurrentTemp)
        tvFeelsLike = findViewById(R.id.tvForecastFeelsLike)
        tvMaxMin = findViewById(R.id.tvForecastMaxMin)
        tvError = findViewById(R.id.tvForecastError)
        tvSummary = findViewById(R.id.tvForecastSummary)
        cardCurrent = findViewById(R.id.cardForecastCurrent)
        cardSummary = findViewById(R.id.cardForecastSummary)
        cardHourly = findViewById(R.id.cardForecastHourly)
        cardDaily = findViewById(R.id.cardForecastDaily)
        recyclerHourly = findViewById(R.id.recyclerForecastHourly)
        recyclerDaily = findViewById(R.id.recyclerForecastDaily)

        cardUvIndex = findViewById(R.id.cardUvIndex)
        cardSun = findViewById(R.id.cardSun)
        cardAirQuality = findViewById(R.id.cardAirQuality)
        cardPollen = findViewById(R.id.cardPollen)
        waveHumidity = findViewById(R.id.waveHumidity)
        tvHumidityValue = findViewById(R.id.tvHumidityValue)
        tvDewPoint = findViewById(R.id.tvDewPoint)
        uvScaleView = findViewById(R.id.uvScaleView)
        tvUvValue = findViewById(R.id.tvUvValue)
        tvUvLabel = findViewById(R.id.tvUvLabel)
        tvPrecipitationValue = findViewById(R.id.tvPrecipitationValue)
        tvPrecipitationHint = findViewById(R.id.tvPrecipitationHint)
        tvWindValue = findViewById(R.id.tvWindValue)
        tvWindDetail = findViewById(R.id.tvWindDetail)
        tvCloudCoverValue = findViewById(R.id.tvCloudCoverValue)
        tvCloudCoverLabel = findViewById(R.id.tvCloudCoverLabel)
        arcPressure = findViewById(R.id.arcPressure)
        tvPressureValue = findViewById(R.id.tvPressureValue)
        tvVisibilityValue = findViewById(R.id.tvVisibilityValue)
        sunArcView = findViewById(R.id.sunArcView)
        tvSunrise = findViewById(R.id.tvSunrise)
        tvSunset = findViewById(R.id.tvSunset)
        tvDaylight = findViewById(R.id.tvDaylight)
        tvAqiValue = findViewById(R.id.tvAqiValue)
        barAqi = findViewById(R.id.barAqi)
        tvAqiLabel = findViewById(R.id.tvAqiLabel)
        tvPollenValue = findViewById(R.id.tvPollenValue)
        barPollen = findViewById(R.id.barPollen)
        tvPollenLabel = findViewById(R.id.tvPollenLabel)

        recyclerHourly.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerDaily.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerHourly.isNestedScrollingEnabled = false
        recyclerDaily.isNestedScrollingEnabled = false

        val cached = WeatherHelper.getCachedWeatherEntry()
        if (cached != null) {
            render(cached)
        }
        loadForecast(force = false, showLoadingIndicator = cached == null)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_weather_forecast, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_refresh_weather -> {
            loadForecast(force = true, showLoadingIndicator = true)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun loadForecast(force: Boolean, showLoadingIndicator: Boolean) {
        job?.cancel()
        if (showLoadingIndicator) {
            showLoading()
        }
        job = lifecycleScope.launch {
            val fresh = WeatherHelper.fetchForecast(this@WeatherForecastActivity, force = force)
            if (showLoadingIndicator) {
                hideLoading()
            }
            if (fresh != null) {
                render(fresh)
            } else if (!cardCurrent.isVisible) {
                tvError.isVisible = true
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun render(entry: WeatherHelper.WeatherCacheEntry) {
        tvError.isVisible = false
        cardCurrent.isVisible = true

        ivIcon.setImageResource(WeatherHelper.iconResForCode(entry.weatherCode, entry.isDay))
        tvCondition.text = getString(WeatherHelper.conditionLabelRes(entry.weatherCode))
        tvTemp.text = "${Math.round(entry.temperatureCelsius)}\u00b0"
        tvFeelsLike.text = getString(
            R.string.weather_feels_like,
            "${Math.round(entry.apparentTemperatureCelsius)}\u00b0"
        )

        val todayMax = entry.dailyTemperatureMaxCelsius.getOrNull(0)
        val todayMin = entry.dailyTemperatureMinCelsius.getOrNull(0)
        tvMaxMin.isVisible = todayMax != null && todayMin != null
        if (todayMax != null && todayMin != null) {
            tvMaxMin.text = getString(
                R.string.weather_max_min,
                "${Math.round(todayMax)}\u00b0",
                "${Math.round(todayMin)}\u00b0"
            )
        }

        cardSummary.isVisible = true
        tvSummary.text = buildDaySummary(entry)

        val hourlyItems = buildHourlyItems(entry)
        cardHourly.isVisible = hourlyItems.isNotEmpty()
        recyclerHourly.adapter = WeatherHourlyAdapter(this, hourlyItems)

        val dailyItems = buildDailyItems(entry)
        cardDaily.isVisible = dailyItems.isNotEmpty()
        recyclerDaily.adapter = WeatherDailyAdapter(this, dailyItems)

        renderDetailGrid(entry)
    }

    /** Rule-based one-paragraph human summary of today's forecast, built from cached data already on hand. */
    private fun buildDaySummary(entry: WeatherHelper.WeatherCacheEntry): String {
        val conditionLabel = getString(WeatherHelper.conditionLabelRes(entry.weatherCode)).lowercase(Locale.getDefault())
        val hi = entry.dailyTemperatureMaxCelsius.getOrNull(0)
        val lo = entry.dailyTemperatureMinCelsius.getOrNull(0)
        val precip = entry.dailyPrecipitationProbabilityMax.getOrNull(0) ?: 0
        val wind = entry.windSpeedKmh

        val parts = mutableListOf<String>()
        parts.add(getString(R.string.weather_summary_condition, conditionLabel))
        if (hi != null && lo != null) {
            parts.add(
                getString(
                    R.string.weather_summary_high_low,
                    "${Math.round(hi)}\u00b0",
                    "${Math.round(lo)}\u00b0"
                )
            )
        }
        when {
            precip >= 70 -> parts.add(getString(R.string.weather_summary_rain_likely))
            precip >= 40 -> parts.add(getString(R.string.weather_summary_rain_possible))
            precip >= 20 -> parts.add(getString(R.string.weather_summary_rain_slight))
        }
        if (wind >= 30) {
            parts.add(getString(R.string.weather_summary_windy))
        }
        return parts.joinToString(" ")
    }

    /**
     * Populates the Humidity / UV / Precipitation / Wind / Cloud cover / Pressure /
     * Visibility / Sun / Air quality / Pollen card grid below the daily forecast.
     * Cards backed by data Open-Meteo didn't return for this location (UV, sun,
     * air quality, pollen) are hidden rather than shown empty.
     */
    private fun renderDetailGrid(entry: WeatherHelper.WeatherCacheEntry) {
        val tertiary = getColorAttr(R.attr.colorTertiary)
        val onSurfaceVariant = getColorAttr(R.attr.colorOnSurfaceVariant)

        // Humidity
        waveHumidity.waveColor = tertiary
        waveHumidity.fillFraction = entry.relativeHumidity / 100f
        tvHumidityValue.text = "${entry.relativeHumidity}%"
        tvDewPoint.text = getString(R.string.weather_dew_point, "${Math.round(entry.dewPointCelsius)}\u00b0")

        // UV index
        val uv = entry.uvIndex
        cardUvIndex.isVisible = uv != null
        if (uv != null) {
            val (level, labelRes) = WeatherHelper.uvCategory(uv)
            uvScaleView.activeIndex = level
            tvUvValue.text = Math.round(uv).toString()
            tvUvLabel.text = getString(labelRes)
        }

        // Precipitation
        val precipMm = entry.precipitationMm ?: 0.0
        tvPrecipitationValue.text = getString(R.string.weather_precipitation_amount, precipMm)
        tvPrecipitationHint.text = buildPrecipitationHint(entry)

        // Wind
        tvWindValue.text = getString(R.string.weather_speed_kmh, Math.round(entry.windSpeedKmh).toString())
        tvWindDetail.text = getString(
            R.string.weather_wind_detail,
            compassDirectionLabel(entry.windDirectionDeg),
            Math.round(entry.windGustsKmh).toString()
        )

        // Cloud cover
        tvCloudCoverValue.text = "${entry.cloudCoverPercent}%"
        tvCloudCoverLabel.text = getString(WeatherHelper.cloudCoverLabelRes(entry.cloudCoverPercent))

        // Pressure (typical sea-level range ~980-1040 hPa mapped to the gauge arc)
        arcPressure.trackColor = android.graphics.Color.argb(
            60,
            android.graphics.Color.red(onSurfaceVariant),
            android.graphics.Color.green(onSurfaceVariant),
            android.graphics.Color.blue(onSurfaceVariant)
        )
        arcPressure.progressColor = tertiary
        arcPressure.progress = (((entry.pressureMsl - 980.0) / 60.0).toFloat()).coerceIn(0f, 1f)
        tvPressureValue.text = Math.round(entry.pressureMsl).toString()

        // Visibility
        tvVisibilityValue.text = Math.round(entry.visibilityMeters / 1000.0).toString()

        // Sun
        val sunrise = entry.dailySunriseIso.getOrNull(0)
        val sunset = entry.dailySunsetIso.getOrNull(0)
        val daylightSec = entry.dailyDaylightDurationSec.getOrNull(0)
        cardSun.isVisible = sunrise != null && sunset != null
        if (sunrise != null && sunset != null) {
            sunArcView.arcColor = tertiary
            sunArcView.baselineColor = onSurfaceVariant
            val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            try {
                val sunriseDate = isoParser.parse(sunrise)
                val sunsetDate = isoParser.parse(sunset)
                if (sunriseDate != null && sunsetDate != null) {
                    tvSunrise.text = timeFmt.format(sunriseDate)
                    tvSunset.text = timeFmt.format(sunsetDate)
                    val span = (sunsetDate.time - sunriseDate.time).toFloat()
                    val elapsed = (Date().time - sunriseDate.time).toFloat()
                    sunArcView.progress = if (span > 0) elapsed / span else 0f
                }
            } catch (e: Exception) {
                // leave sunrise/sunset labels blank on parse failure
            }
            if (daylightSec != null) {
                val hours = (daylightSec / 3600).toInt()
                val minutes = ((daylightSec % 3600) / 60).toInt()
                tvDaylight.text = getString(R.string.weather_daylight_duration, hours, minutes)
            }
        }

        // Air quality
        val aqi = entry.airQualityUsAqi
        cardAirQuality.isVisible = aqi != null
        if (aqi != null) {
            val (fraction, colorRes, labelRes) = WeatherHelper.aqiCategory(aqi)
            tvAqiValue.text = aqi.toString()
            barAqi.segmentColors = intArrayOf(
                getColorCompat(R.color.palette_green),
                getColorCompat(R.color.palette_yellow),
                getColorCompat(R.color.palette_orange),
                getColorCompat(R.color.palette_red),
                getColorCompat(R.color.palette_deep_purple)
            )
            barAqi.markerFraction = fraction
            barAqi.markerColor = getColorCompat(colorRes)
            tvAqiLabel.text = getString(labelRes)
        }

        // Pollen (highest of tree/grass/weed drives the headline level)
        val pollenReadings = listOfNotNull(entry.pollenTree, entry.pollenGrass, entry.pollenWeed)
        cardPollen.isVisible = pollenReadings.isNotEmpty()
        if (pollenReadings.isNotEmpty()) {
            val worst = pollenReadings.max()
            val (level, labelRes) = WeatherHelper.pollenCategory(worst)
            tvPollenValue.text = getString(R.string.weather_pollen_level, level)
            barPollen.segmentColors = intArrayOf(
                getColorCompat(R.color.palette_green),
                getColorCompat(R.color.palette_light_green),
                getColorCompat(R.color.palette_yellow),
                getColorCompat(R.color.palette_orange),
                getColorCompat(R.color.palette_red)
            )
            barPollen.markerFraction = level / 4f
            barPollen.markerColor = getColorCompat(R.color.palette_green)
            tvPollenLabel.text = getString(labelRes)
        }
    }

    private fun getColorCompat(colorRes: Int): Int =
        androidx.core.content.ContextCompat.getColor(this, colorRes)

    private fun compassDirectionLabel(degrees: Int): String {
        val normalized = ((degrees % 360) + 360) % 360
        val index = ((normalized + 22.5) / 45.0).toInt() % 8
        val res = when (index) {
            0 -> R.string.weather_direction_n
            1 -> R.string.weather_direction_ne
            2 -> R.string.weather_direction_e
            3 -> R.string.weather_direction_se
            4 -> R.string.weather_direction_s
            5 -> R.string.weather_direction_sw
            6 -> R.string.weather_direction_w
            else -> R.string.weather_direction_nw
        }
        return getString(res)
    }

    /** "No rain in the next 2 hours" vs a heads-up, based on the next couple of hourly precipitation-probability entries. */
    private fun buildPrecipitationHint(entry: WeatherHelper.WeatherCacheEntry): String {
        val times = entry.hourlyTimeIso
        if (times.isEmpty()) return ""
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.US).format(Date())
        val startIndex = times.indexOfFirst { it >= nowIso }.let { if (it < 0) 0 else it }
        val nextProbabilities = (startIndex until minOf(times.size, startIndex + 2))
            .mapNotNull { entry.hourlyPrecipitationProbability.getOrNull(it) }
        val maxProbability = nextProbabilities.maxOrNull() ?: 0
        return if (maxProbability >= 40) {
            getString(R.string.weather_precipitation_rain_expected)
        } else {
            getString(R.string.weather_precipitation_no_rain_hours, 2)
        }
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
                    getString(R.string.weather_now)
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
                getString(R.string.weather_today)
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
