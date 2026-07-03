package com.v2ray.ang.util

import com.google.gson.annotations.SerializedName

/**
 * Subset of the Open-Meteo `/v1/forecast` response WeatherHelper consumes.
 * Endpoint: https://api.open-meteo.com/v1/forecast
 *
 * `current`, `hourly`, and `daily` are fetched together in one request.
 * Field names mirror the API's snake_case keys via [SerializedName];
 * everything is metric (Open-Meteo's default units).
 */
data class OpenMeteoForecastResponse(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val current: OpenMeteoCurrent? = null,
    val hourly: OpenMeteoHourly? = null,
    val daily: OpenMeteoDaily? = null
)

data class OpenMeteoCurrent(
    @SerializedName("temperature_2m") val temperature: Double? = null,
    @SerializedName("apparent_temperature") val apparentTemperature: Double = 0.0,
    @SerializedName("relative_humidity_2m") val relativeHumidity: Int = 0,
    @SerializedName("dew_point_2m") val dewPoint: Double = 0.0,
    @SerializedName("weather_code") val weatherCode: Int = 0,
    @SerializedName("wind_speed_10m") val windSpeed: Double = 0.0,
    @SerializedName("wind_direction_10m") val windDirection: Int = 0,
    @SerializedName("pressure_msl") val pressureMsl: Double = 0.0,
    @SerializedName("visibility") val visibility: Double = 0.0,
    @SerializedName("cloud_cover") val cloudCover: Int = 0,
    @SerializedName("wind_gusts_10m") val windGusts: Double = 0.0,
    @SerializedName("is_day") val isDay: Int = 1
)

/**
 * Parallel-arrays hourly block (same shape Open-Meteo returns: one list per
 * field, all the same length, index `i` = one hour). Only the 4 fields
 * `HourlyCard` actually renders — see [com.v2ray.ang.util.WeatherHelper] doc
 * comment on why the wider field list from the reference isn't pulled in.
 * Nullable with no defaults relied on: Gson leaves a field null (not the
 * Kotlin default) when the API omits it, so every read site must null-check.
 */
data class OpenMeteoHourly(
    val time: List<String>? = null,
    @SerializedName("temperature_2m") val temperature: List<Double>? = null,
    @SerializedName("weather_code") val weatherCode: List<Int>? = null,
    @SerializedName("precipitation_probability") val precipitationProbability: List<Int>? = null,
    @SerializedName("is_day") val isDay: List<Int>? = null
)

/** Parallel-arrays daily block, one entry per day. See [OpenMeteoHourly]. */
data class OpenMeteoDaily(
    val time: List<String>? = null,
    @SerializedName("weather_code") val weatherCode: List<Int>? = null,
    @SerializedName("temperature_2m_max") val temperatureMax: List<Double>? = null,
    @SerializedName("temperature_2m_min") val temperatureMin: List<Double>? = null,
    @SerializedName("precipitation_probability_max") val precipitationProbabilityMax: List<Int>? = null
)

/**
 * Open-Meteo geocoding response for `/v1/search?name=...`, used to resolve a
 * user-typed custom location string to coordinates.
 * Endpoint: https://geocoding-api.open-meteo.com/v1/search
 */
data class OpenMeteoGeocodingResponse(
    val results: List<OpenMeteoGeocodingResult>? = null
)

data class OpenMeteoGeocodingResult(
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val country: String? = null,
    val admin1: String? = null
)
