package com.v2ray.ang.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Fetches the device's current location via Google Fused Location Provider
 * and the current weather at that location from Open-Meteo (no API key).
 * Used to populate the small weather chip on the main screen.
 */
object WeatherHelper {

    data class WeatherResult(
        val tempCelsius: Int,
        val isDay: Boolean,
        val weatherCode: Int
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasFineLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Gets the current location using Fused Location Provider.
     * Uses PRIORITY_HIGH_ACCURACY if ACCESS_FINE_LOCATION is granted,
     * otherwise falls back to PRIORITY_BALANCED_POWER_ACCURACY (coarse).
     */
    private suspend fun getCurrentLocation(context: Context): android.location.Location? {
        if (!hasLocationPermission(context)) return null
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val priority = if (hasFineLocationPermission(context)) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val cts = CancellationTokenSource()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cts.cancel() }
            try {
                fusedClient.getCurrentLocation(priority, cts.token)
                    .addOnSuccessListener { location -> cont.resume(location) }
                    .addOnFailureListener { cont.resume(null) }
            } catch (e: SecurityException) {
                cont.resume(null)
            }
        }
    }

    /**
     * Fetches the current temperature for the device's current location.
     * Returns null on any failure (no permission, no location fix, or network error).
     */
    suspend fun fetchCurrentWeather(context: Context): WeatherResult? = withContext(Dispatchers.IO) {
        val location = getCurrentLocation(context) ?: return@withContext null
        try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${location.latitude}" +
                "&longitude=${location.longitude}" +
                "&current=temperature_2m,weather_code,is_day"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JsonUtil.parseString(body) ?: return@withContext null
                val current = json.getAsJsonObject("current") ?: return@withContext null
                val temp = current.get("temperature_2m")?.asDouble ?: return@withContext null
                val code = current.get("weather_code")?.asInt ?: 0
                val isDay = (current.get("is_day")?.asInt ?: 1) == 1
                WeatherResult(
                    tempCelsius = Math.round(temp).toInt(),
                    isDay = isDay,
                    weatherCode = code
                )
            }
        } catch (e: Exception) {
            LogUtil.e("WeatherHelper", "fetchCurrentWeather failed: ${e.message}")
            null
        }
    }

    /**
     * Maps an Open-Meteo WMO weather code to one of our weather drawables.
     * https://open-meteo.com/en/docs (WMO Weather interpretation codes)
     */
    fun iconResFor(code: Int, isDay: Boolean): Int {
        return when (code) {
            0, 1 -> if (isDay) com.v2ray.ang.R.drawable.ic_weather_sunny else com.v2ray.ang.R.drawable.ic_weather_night
            2, 3 -> com.v2ray.ang.R.drawable.ic_cloud
            45, 48 -> com.v2ray.ang.R.drawable.ic_weather_fog
            51, 53, 55, 56, 57,
            61, 63, 65, 66, 67,
            80, 81, 82 -> com.v2ray.ang.R.drawable.ic_weather_rain
            71, 73, 75, 77, 85, 86 -> com.v2ray.ang.R.drawable.ic_weather_snow
            95, 96, 99 -> com.v2ray.ang.R.drawable.ic_weather_storm
            else -> if (isDay) com.v2ray.ang.R.drawable.ic_weather_sunny else com.v2ray.ang.R.drawable.ic_weather_night
        }
    }
}
