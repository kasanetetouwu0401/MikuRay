package com.v2ray.ang.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

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

    fun hasBackgroundLocationPermission(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            return hasLocationPermission(context)
        }
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getCachedWeather(): WeatherResult? {
        val timestamp = MmkvManager.decodeSettingsLong(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, 0L)
        if (timestamp == 0L) return null
        if (System.currentTimeMillis() - timestamp > AppConfig.WEATHER_CACHE_TTL_MS) return null
        return readCacheEntry()
    }

    fun getCachedWeatherStale(): WeatherResult? {
        val timestamp = MmkvManager.decodeSettingsLong(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, 0L)
        if (timestamp == 0L) return null
        return readCacheEntry()
    }

    private fun readCacheEntry(): WeatherResult? {
        val temp = MmkvManager.decodeSettingsInt(AppConfig.PREF_WEATHER_CACHE_TEMP, Int.MIN_VALUE)
        if (temp == Int.MIN_VALUE) return null
        val code = MmkvManager.decodeSettingsInt(AppConfig.PREF_WEATHER_CACHE_CODE, 0)
        val isDay = MmkvManager.decodeSettingsBool(AppConfig.PREF_WEATHER_CACHE_IS_DAY, true)
        return WeatherResult(tempCelsius = temp, isDay = isDay, weatherCode = code)
    }

    private fun saveCache(result: WeatherResult) {
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TEMP, result.tempCelsius)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_CODE, result.weatherCode)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_IS_DAY, result.isDay)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, System.currentTimeMillis())
    }

    private suspend fun getCurrentLocation(context: Context, force: Boolean = false): android.location.Location? {
        if (!hasLocationPermission(context)) return null
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val priority = if (force || hasFineLocationPermission(context)) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        if (force) {
            runCatching { fusedClient.flushLocations() }
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

    suspend fun fetchCurrentWeather(context: Context, force: Boolean = false): WeatherResult? = withContext(Dispatchers.IO) {
        val location = getCurrentLocation(context, force) ?: return@withContext null
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
                val result = WeatherResult(
                    tempCelsius = Math.round(temp).toInt(),
                    isDay = isDay,
                    weatherCode = code
                )
                saveCache(result)
                result
            }
        } catch (e: Exception) {
            LogUtil.e("WeatherHelper", "fetchCurrentWeather failed: ${e.message}")
            null
        }
    }

    fun iconResFor(code: Int, isDay: Boolean): Int {
        return when (code) {
            0, 1 -> if (isDay) R.drawable.ic_weather_sunny else R.drawable.ic_weather_night
            2, 3 -> R.drawable.ic_cloud
            45, 48 -> R.drawable.ic_weather_fog
            51, 53, 55, 56, 57,
            61, 63, 65, 66, 67,
            80, 81, 82 -> R.drawable.ic_weather_rain
            71, 73, 75, 77, 85, 86 -> R.drawable.ic_weather_snow
            95, 96, 99 -> R.drawable.ic_weather_storm
            else -> if (isDay) R.drawable.ic_weather_sunny else R.drawable.ic_weather_night
        }
    }

    fun scheduleBackgroundUpdates(context: Context, forceReschedule: Boolean = false) {
        if (!hasLocationPermission(context)) {
            LogUtil.e("WeatherHelper", "scheduleBackgroundUpdates: no location permission, skip")
            return
        }

        val request = PeriodicWorkRequestBuilder<UpdateWorker>(
            AppConfig.WEATHER_UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(AppConfig.WEATHER_UPDATE_TASK_NAME)
            .build()

        val policy = if (forceReschedule) {
            ExistingPeriodicWorkPolicy.REPLACE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }

        RemoteWorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AppConfig.WEATHER_UPDATE_TASK_NAME,
            policy,
            request
        )
        LogUtil.i(
            "WeatherHelper",
            "scheduleBackgroundUpdates: scheduled, interval=${AppConfig.WEATHER_UPDATE_INTERVAL_MINUTES}min policy=$policy"
        )
    }

    fun cancelBackgroundUpdates(context: Context) {
        RemoteWorkManager.getInstance(context).cancelUniqueWork(AppConfig.WEATHER_UPDATE_TASK_NAME)
        LogUtil.i("WeatherHelper", "cancelBackgroundUpdates: cancelled")
    }

    class UpdateWorker(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)) {
                LogUtil.i("WeatherHelper", "UpdateWorker: weather chip disabled, skipping run")
                return Result.success()
            }
            if (!hasBackgroundLocationPermission(applicationContext)) {
                LogUtil.w(
                    "WeatherHelper",
                    "UpdateWorker: missing ACCESS_BACKGROUND_LOCATION, cannot fetch in background"
                )
                return Result.success()
            }

            val result = fetchCurrentWeather(applicationContext)
            return if (result != null) {
                LogUtil.i(
                    "WeatherHelper",
                    "UpdateWorker: success, temp=${result.tempCelsius} code=${result.weatherCode}"
                )
                Result.success()
            } else {
                LogUtil.w("WeatherHelper", "UpdateWorker: fetch failed, will retry")
                Result.retry()
            }
        }
    }
}
