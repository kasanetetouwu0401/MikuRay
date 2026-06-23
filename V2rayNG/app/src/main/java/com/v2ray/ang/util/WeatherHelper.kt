package com.v2ray.ang.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.work.*
import androidx.work.multiprocess.RemoteWorkManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.*
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

object WeatherHelper {

    data class WeatherResult(val emoji: String, val tempCelsius: Int) {
        fun getTemperatureString(celsius: Boolean = isDefaultCelsius()) =
            if (celsius) "${tempCelsius}°C" else "${Math.round(tempCelsius * 9.0 / 5.0 + 32)}°F"
    }

    private fun Context.hasPerm(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    fun hasLocationPermission(context: Context) =
        context.hasPerm(Manifest.permission.ACCESS_COARSE_LOCATION) || 
        context.hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun hasFineLocationPermission(context: Context) =
        context.hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBackgroundLocationPermission(context: Context) =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) hasLocationPermission(context)
        else context.hasPerm(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun isDefaultCelsius(): Boolean {
        val tz = TimeZone.getDefault().id
        return !tz.startsWith("US/") && tz !in listOf("America/Nassau", "America/Belize", "America/Cayman", "Pacific/Palau")
    }

    fun isCelsius(): Boolean {
        val stored = MmkvManager.decodeSettingsString(AppConfig.PREF_WEATHER_USE_CELSIUS, "")
        return if (stored.isNullOrEmpty()) isDefaultCelsius() else stored == "true"
    }

    fun iconResForEmoji(emoji: String?) = when (emoji) {
        "\u2600" -> R.drawable.ic_weather_sunny
        "\u2601", "\u26c5", "\ud83c\udf24" -> R.drawable.ic_cloud
        "\ud83c\udf26", "\ud83c\udf27" -> R.drawable.ic_weather_rain
        "\u26a1", "\u26c8" -> R.drawable.ic_weather_storm
        "\u2744", "\ud83c\udf28" -> R.drawable.ic_weather_snow
        "\ud83d\ude36\u200d\ud83c\udf2b" -> R.drawable.ic_weather_fog
        "\ud83c\udf13", "\ud83c\udf14", "\ud83c\udf16", "\ud83c\udf17",
        "\ud83c\udf1a", "\ud83c\udf1b", "\ud83c\udf1c", "\ud83c\udf1d" -> R.drawable.ic_weather_night
        else -> R.drawable.ic_weather_sunny
    }

    private fun emojiForCode(code: Int, isDay: Boolean): String = when (code) {
        0, 1 -> if (isDay) "\u2600" else moonPhaseEmoji()
        2 -> "\u26c5"
        3 -> "\u2601"
        45, 48 -> "\ud83d\ude36\u200d\ud83c\udf2b"
        51, 53, 55, 61, 63, 80, 81 -> "\ud83c\udf26"
        56, 57, 65, 66, 67, 82 -> "\ud83c\udf27"
        71, 73, 75, 77, 85, 86 -> "\ud83c\udf28"
        95 -> "\u26a1"
        96, 99 -> "\u26c8"
        else -> if (isDay) "\u2600" else moonPhaseEmoji()
    }

    private fun moonPhaseEmoji(): String {
        val phase = ((System.currentTimeMillis() / 86400000.0 + 2440588.0 - 0.5 - 2451550.1) % 29.53058867 + 29.53058867) % 29.53058867
        return when {
            phase < 1.85 -> "\ud83c\udf1a"
            phase < 5.54 -> "\ud83c\udf1b"
            phase < 9.23 -> "\ud83c\udf13"
            phase < 12.92 -> "\ud83c\udf14"
            phase < 16.61 -> "\ud83c\udf1d"
            phase < 20.30 -> "\ud83c\udf16"
            phase < 23.99 -> "\ud83c\udf17"
            phase < 27.68 -> "\ud83c\udf1c"
            else -> "\ud83c\udf1a"
        }
    }

    fun getCachedWeather(): WeatherResult? =
        getCachedWeatherStale()?.takeIf { getCacheAgeMs() in 0..AppConfig.WEATHER_CACHE_TTL_MS }

    fun getCachedWeatherStale(): WeatherResult? {
        val temp = MmkvManager.decodeSettingsInt(AppConfig.PREF_WEATHER_CACHE_TEMP, Int.MIN_VALUE)
        val emoji = MmkvManager.decodeSettingsString(AppConfig.PREF_WEATHER_CACHE_EMOJI, "") ?: ""
        return if (temp == Int.MIN_VALUE) null else WeatherResult(emoji, temp)
    }

    fun getCacheAgeMs(): Long {
        val ts = MmkvManager.decodeSettingsLong(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, 0L)
        return if (ts == 0L) -1L else System.currentTimeMillis() - ts
    }

    private fun isCacheValidForLocation(location: Location): Boolean {
        val lat = MmkvManager.decodeSettingsFloat(AppConfig.PREF_WEATHER_CACHE_LAT, 0f)
        val lon = MmkvManager.decodeSettingsFloat(AppConfig.PREF_WEATHER_CACHE_LON, 0f)
        if (lat == 0f && lon == 0f) return false
        
        val results = FloatArray(1)
        Location.distanceBetween(lat.toDouble(), lon.toDouble(), location.latitude, location.longitude, results)
        return results[0] <= AppConfig.WEATHER_LOCATION_STALE_METERS
    }

    private fun saveCache(result: WeatherResult, location: Location) {
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TEMP, result.tempCelsius)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_EMOJI, result.emoji)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, System.currentTimeMillis())
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_LAT, location.latitude.toFloat())
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_LON, location.longitude.toFloat())
    }

    fun clearCache() {
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, 0L)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TEMP, Int.MIN_VALUE)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_EMOJI, "")
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_LAT, 0f)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_LON, 0f)
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT, ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
            .build()
    }

    private suspend fun getCurrentLocation(context: Context, force: Boolean = false): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        val isGpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!isGpsEnabled && !isNetworkEnabled) return null

        val useFine = force || hasFineLocationPermission(context)
        val provider = if (useFine && isGpsEnabled) LocationManager.GPS_PROVIDER 
                       else if (isNetworkEnabled) LocationManager.NETWORK_PROVIDER 
                       else LocationManager.GPS_PROVIDER

        fun getFallback(): Location? {
            return try {
                val last = lm.getLastKnownLocation(provider)
                if (last != null) return last
                val altProvider = if (provider == LocationManager.GPS_PROVIDER) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                if (lm.isProviderEnabled(altProvider)) lm.getLastKnownLocation(altProvider) else null
            } catch (e: Exception) { null }
        }

        if (!force) getFallback()?.let { return it }

        return withTimeoutOrNull(AppConfig.WEATHER_LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) { 
                        if (cont.isActive) cont.resume(it) 
                    }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } ?: getFallback()
    }

    suspend fun fetchCurrentWeather(context: Context, force: Boolean = false): WeatherResult? = withContext(Dispatchers.IO) {
        val location = getCurrentLocation(context, force) ?: return@withContext null
        
        if (!force && getCachedWeather() != null && isCacheValidForLocation(location)) {
            return@withContext getCachedWeather()
        }

        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}&current=temperature_2m,weather_code,is_day"
            val defaultAgent = System.getProperty("http.agent") ?: "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", defaultAgent)
                .build()
            
            val jsonStr = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return@withContext null
            val current = JsonUtil.parseString(jsonStr)?.getAsJsonObject("current") ?: return@withContext null
            
            val temp = current.get("temperature_2m")?.asDouble ?: return@withContext null
            val code = current.get("weather_code")?.asInt ?: 0
            val isDay = current.get("is_day")?.asInt != 0

            WeatherResult(emojiForCode(code, isDay), Math.round(temp).toInt()).also { saveCache(it, location) }
        } catch (e: Exception) {
            null
        }
    }

    fun scheduleBackgroundUpdates(context: Context, forceReschedule: Boolean = false) {
        if (!hasLocationPermission(context)) return
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(AppConfig.WEATHER_UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(AppConfig.WEATHER_UPDATE_TASK_NAME)
            .build()
            
        val policy = if (forceReschedule) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP
        RemoteWorkManager.getInstance(context).enqueueUniquePeriodicWork(AppConfig.WEATHER_UPDATE_TASK_NAME, policy, request)
    }

    fun cancelBackgroundUpdates(context: Context) =
        RemoteWorkManager.getInstance(context).cancelUniqueWork(AppConfig.WEATHER_UPDATE_TASK_NAME)

    class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false) ||
                !hasBackgroundLocationPermission(applicationContext)) {
                return Result.success()
            }
            return if (fetchCurrentWeather(applicationContext) != null) Result.success() else Result.retry()
        }
    }
}
